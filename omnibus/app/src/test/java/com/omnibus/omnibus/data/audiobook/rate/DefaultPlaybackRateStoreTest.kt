package com.omnibus.omnibus.data.audiobook.rate

import com.omnibus.omnibus.data.audiobook.rate.local.PlaybackRateCache
import com.omnibus.omnibus.data.audiobook.rate.remote.PlaybackRateApi
import com.omnibus.omnibus.data.audiobook.rate.remote.PutOutcome
import com.omnibus.omnibus.data.audiobook.rate.remote.RateFetch
import com.omnibus.omnibus.data.network.ConnectivityMonitor
import com.omnibus.omnibus.data.network.TestConnectivityMonitor
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomDouble
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** A rate inside the bounds, so it reads back as it was given. */
private fun randomRate(): Double = randomDouble(0.5, 3.0)

/** A store whose reads and flushes run on the test's background scope, on the test clock. */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultPlaybackRateStoreTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    fun TestScope.sut(
        api: PlaybackRateApi = TestPlaybackRateApi(),
        cache: PlaybackRateCache = TestPlaybackRateCache(),
        connectivity: ConnectivityMonitor = TestConnectivityMonitor(online = true),
    ) = DefaultPlaybackRateStore(api, cache, connectivity, backgroundScope)

    /** Moves past the read deadline, running everything that falls due. */
    fun TestScope.passDeadline() {
        runCurrent()
        advanceTimeBy(1_200)
        runCurrent()
    }

    describe("reading a book's rate") {

        context("when a local change is unsent") {
            // I-SPD-4, R15: it answers at once, with the server never consulted
            it("#168 TC-31: answers the local change (I-SPD-4)") {
                runTest {
                    val book = randomString()
                    val pending = randomRate()
                    val api = TestPlaybackRateApi().apply { defaultFetch = RateFetch.Found(randomRate()) }
                    val cache = TestPlaybackRateCache().apply { pendingRates[book] = pending }

                    sut(api, cache).rate(book) shouldBe pending
                }
            }

            // I-SPD-3, R17
            it("#168 TC-32: sends it in the background (I-SPD-3)") {
                runTest {
                    val book = randomString()
                    val pending = randomRate()
                    val api = TestPlaybackRateApi()
                    val cache = TestPlaybackRateCache()
                    cache.saveLocal(book, pending)

                    sut(api, cache).rate(book)
                    runCurrent()

                    api.puts shouldBe listOf(book to pending)
                }
            }
        }

        context("when offline") {
            // P-SPD-5, I-SPD-4
            it("#168 TC-34: answers the cached rate (P-SPD-5, I-SPD-4)") {
                runTest {
                    val book = randomString()
                    val cached = randomRate()
                    val cache = TestPlaybackRateCache().apply { cachedRates[book] = cached }

                    sut(cache = cache, connectivity = TestConnectivityMonitor(online = false)).rate(book) shouldBe cached
                }
            }
        }

        context("when the server answers a rate") {
            // I-SPD-4
            it("#168 TC-35: caches it (I-SPD-4)") {
                runTest {
                    val book = randomString()
                    val rate = randomRate()
                    val api = TestPlaybackRateApi().apply { defaultFetch = RateFetch.Found(rate) }
                    val cache = TestPlaybackRateCache()

                    sut(api, cache).rate(book)

                    cache.cached(book) shouldBe rate
                }
            }

            // I-SPD-1
            it("#168 TC-36: clamps one above the bounds (I-SPD-1)") {
                runTest {
                    val api = TestPlaybackRateApi().apply { defaultFetch = RateFetch.Found(9.0) }

                    sut(api).rate(randomString()) shouldBe 3.0
                }
            }
        }

        context("when the listener never set a rate") {
            // I-SPD-4: the book plays at 1.0
            it("#168 TC-37: answers null over a cached rate (I-SPD-4)") {
                runTest {
                    val book = randomString()
                    val api = TestPlaybackRateApi().apply { defaultFetch = RateFetch.Unset }
                    val cache = TestPlaybackRateCache().apply { cachedRates[book] = randomRate() }

                    sut(api, cache).rate(book).shouldBeNull()
                }
            }

            // I-SPD-4
            it("#168 TC-37: forgets the cached rate (I-SPD-4)") {
                runTest {
                    val book = randomString()
                    val api = TestPlaybackRateApi().apply { defaultFetch = RateFetch.Unset }
                    val cache = TestPlaybackRateCache().apply { cachedRates[book] = randomRate() }

                    sut(api, cache).rate(book)

                    cache.cached(book).shouldBeNull()
                }
            }
        }

        context("when the read fails") {
            // I-SPD-4
            it("#168 TC-38: answers the cached rate (I-SPD-4)") {
                runTest {
                    val book = randomString()
                    val cached = randomRate()
                    val api = TestPlaybackRateApi().apply { defaultFetch = RateFetch.Failed }
                    val cache = TestPlaybackRateCache().apply { cachedRates[book] = cached }

                    sut(api, cache).rate(book) shouldBe cached
                }
            }

            // R15
            it("#168 TC-46: answers null for a cache that throws (R15)") {
                runTest {
                    val cache = TestPlaybackRateCache().apply { failWith = RuntimeException("boom") }

                    sut(cache = cache).rate(randomString()).shouldBeNull()
                }
            }
        }

        context("when the server stays silent") {
            // I-SPD-4, R15
            it("#168 TC-39: answers the cached rate at 1200 ms, not before (I-SPD-4)") {
                runTest {
                    val book = randomString()
                    val cached = randomRate()
                    val api = TestPlaybackRateApi().apply { fetchAnswers[book] = CompletableDeferred() }
                    val cache = TestPlaybackRateCache().apply { cachedRates[book] = cached }
                    val read = async { sut(api, cache).rate(book) }
                    runCurrent()

                    advanceTimeBy(1_199)
                    runCurrent()
                    read.isCompleted shouldBe false

                    advanceTimeBy(1)
                    runCurrent()
                    read.await() shouldBe cached
                }
            }
        }

        context("when the server answers just inside the deadline") {
            // I-SPD-4
            it("#168 TC-40: answers the server's rate over the cached one (I-SPD-4)") {
                runTest {
                    val book = randomString()
                    val gate = CompletableDeferred<RateFetch>()
                    val api = TestPlaybackRateApi().apply { fetchAnswers[book] = gate }
                    val cache = TestPlaybackRateCache().apply { cachedRates[book] = 1.0 }
                    val read = async { sut(api, cache).rate(book) }
                    runCurrent()

                    advanceTimeBy(1_199)
                    runCurrent()
                    gate.complete(RateFetch.Found(1.5))
                    runCurrent()

                    read.await() shouldBe 1.5
                }
            }
        }

        context("when the server answers after the deadline") {
            // I-SPD-4
            it("#168 TC-41: refreshes the cache (I-SPD-4)") {
                runTest {
                    val book = randomString()
                    val rate = randomRate()
                    val gate = CompletableDeferred<RateFetch>()
                    val api = TestPlaybackRateApi().apply { fetchAnswers[book] = gate }
                    val cache = TestPlaybackRateCache()
                    val read = async { sut(api, cache).rate(book) }
                    passDeadline()
                    read.await()

                    gate.complete(RateFetch.Found(rate))
                    runCurrent()

                    cache.cached(book) shouldBe rate
                }
            }

            context("when a newer save was made") {
                // I-SPD-3, I-SPD-4: a stale answer never overwrites it, sent or still pending
                it("#168 TC-43, TC-44: leaves the saved rate in the cache, for each send outcome (I-SPD-4)") {
                    checkAll(Exhaustive.of(PutOutcome.Keep, PutOutcome.Sent)) { outcome ->
                        runTest {
                            val book = randomString()
                            val gate = CompletableDeferred<RateFetch>()
                            val api = TestPlaybackRateApi().apply {
                                fetchAnswers[book] = gate
                                defaultPut = outcome
                            }
                            val cache = TestPlaybackRateCache()
                            val store = sut(api, cache)
                            val read = async { store.rate(book) }
                            passDeadline()
                            read.await()
                            store.save(book, 2.0)
                            runCurrent()

                            gate.complete(RateFetch.Found(1.0))
                            runCurrent()

                            cache.cached(book) shouldBe 2.0
                        }
                    }
                }
            }
        }

        context("when the caller is cancelled") {
            // R15, R16
            it("#168 TC-47: still refreshes the cache from the server's answer (R15, R16)") {
                runTest {
                    val book = randomString()
                    val rate = randomRate()
                    val gate = CompletableDeferred<RateFetch>()
                    val api = TestPlaybackRateApi().apply { fetchAnswers[book] = gate }
                    val cache = TestPlaybackRateCache()
                    val read = async { sut(api, cache).rate(book) }
                    runCurrent()
                    read.cancel()
                    runCurrent()

                    gate.complete(RateFetch.Found(rate))
                    runCurrent()

                    cache.cached(book) shouldBe rate
                }
            }
        }
    }

    describe("saving a rate") {

        // I-SPD-1, R16: the save returns before anything is sent
        it("#168 TC-48: writes the device before the send (I-SPD-1, I-SPD-3)") {
            runTest {
                val book = randomString()
                val rate = randomRate()
                val api = TestPlaybackRateApi()
                val cache = TestPlaybackRateCache()

                sut(api, cache).save(book, rate)

                api.puts.shouldBeEmpty()
                cache.pending(book) shouldBe rate
            }
        }

        // I-SPD-1
        it("#168 TC-49: ignores each non-finite rate (I-SPD-1)") {
            checkAll(Exhaustive.of(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) { rate ->
                runTest {
                    val cache = TestPlaybackRateCache()

                    sut(cache = cache).save(randomString(), rate)

                    cache.localWrites.shouldBeEmpty()
                }
            }
        }

        // I-SPD-1
        it("#168 TC-49: clamps a rate out of bounds, for each bound (I-SPD-1)") {
            checkAll(Exhaustive.of(9.0 to 3.0, 0.1 to 0.5)) { (rate, clamped) ->
                runTest {
                    val book = randomString()
                    val cache = TestPlaybackRateCache()

                    sut(cache = cache).save(book, rate)

                    cache.cached(book) shouldBe clamped
                }
            }
        }

        // I-SPD-3, R17: a rejected PUT never loops
        it("#168 TC-48, TC-51: clears the pending rate on a sent or rejected send (I-SPD-3, R17)") {
            checkAll(Exhaustive.of(PutOutcome.Sent, PutOutcome.Drop)) { outcome ->
                runTest {
                    val book = randomString()
                    val api = TestPlaybackRateApi().apply { defaultPut = outcome }
                    val cache = TestPlaybackRateCache()

                    sut(api, cache).save(book, randomRate())
                    runCurrent()

                    cache.pending(book).shouldBeNull()
                }
            }
        }

        context("when the send is kept for later") {
            // I-SPD-3
            it("#168 TC-50: keeps the rate pending (I-SPD-3)") {
                runTest {
                    val book = randomString()
                    val rate = randomRate()
                    val api = TestPlaybackRateApi().apply { defaultPut = PutOutcome.Keep }
                    val cache = TestPlaybackRateCache()

                    sut(api, cache).save(book, rate)
                    runCurrent()

                    cache.pending(book) shouldBe rate
                }
            }

            // I-SPD-3
            it("#168 TC-59: sends the newest rate with the next change (I-SPD-3)") {
                runTest {
                    val book = randomString()
                    val api = TestPlaybackRateApi().apply { putAnswers.addLast(CompletableDeferred(PutOutcome.Keep)) }
                    val store = sut(api)
                    runCurrent()
                    store.save(book, 1.5)
                    runCurrent()

                    store.save(book, 1.8)
                    runCurrent()

                    api.puts shouldBe listOf(book to 1.5, book to 1.8)
                }
            }
        }

        context("when offline") {
            // I-SPD-3
            it("#168 TC-52: sends nothing (I-SPD-3)") {
                runTest {
                    val api = TestPlaybackRateApi()

                    sut(api, connectivity = TestConnectivityMonitor(online = false)).save(randomString(), randomRate())
                    runCurrent()

                    api.puts.shouldBeEmpty()
                }
            }
        }
    }

    describe("flushing pending rates") {

        context("when changes arrive during a send") {
            // I-SPD-3
            it("#168 TC-54: sends only the newest once the send is done (I-SPD-3)") {
                runTest {
                    val book = randomString()
                    val gate = CompletableDeferred<PutOutcome>()
                    val api = TestPlaybackRateApi().apply { putAnswers.addLast(gate) }
                    val store = sut(api)
                    store.save(book, 1.5)
                    runCurrent()
                    store.save(book, 1.8)
                    store.save(book, 2.0)
                    runCurrent()

                    gate.complete(PutOutcome.Sent)
                    runCurrent()

                    api.puts shouldBe listOf(book to 1.5, book to 2.0)
                }
            }
        }

        context("when the device comes online") {
            // I-SPD-3
            it("#168 TC-56: sends each pending book (I-SPD-3)") {
                runTest {
                    val connectivity = TestConnectivityMonitor(online = false)
                    val api = TestPlaybackRateApi()
                    val cache = TestPlaybackRateCache().apply { pendingRates.putAll(mapOf("a" to 1.1, "b" to 1.2)) }
                    sut(api, cache, connectivity)
                    runCurrent()

                    connectivity.isOnline.value = true
                    runCurrent()

                    api.puts shouldBe listOf("a" to 1.1, "b" to 1.2)
                }
            }
        }
    }
})
