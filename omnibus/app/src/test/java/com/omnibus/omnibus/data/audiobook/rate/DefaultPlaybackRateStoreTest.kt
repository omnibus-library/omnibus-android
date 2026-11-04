package com.omnibus.omnibus.data.audiobook.rate

import com.omnibus.omnibus.data.audiobook.rate.remote.PutOutcome
import com.omnibus.omnibus.data.audiobook.rate.remote.RateFetch
import com.omnibus.omnibus.data.network.FakeConnectivityMonitor
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * [DefaultPlaybackRateStore] on the test clock: the read order and deadline (I-SPD-4), the guarded cache refresh, local
 * saves, the outbox flush (I-SPD-3). Each store runs its reads and flushes on the test's background scope.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultPlaybackRateStoreTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    val book = "book-1"

    describe("reads") {

        // I-SPD-4, R15: an unsent local change wins over the server, and answers at once
        it("#168 TC-31: a pending rate wins over the server and returns without waiting (I-SPD-4)") {
            runTest {
                val api = FakePlaybackRateApi().apply { defaultFetch = RateFetch.Found(2.0) }
                val cache = FakePlaybackRateCache().apply { pendingRates[book] = 1.5 }
                val store = DefaultPlaybackRateStore(api, cache, FakeConnectivityMonitor(true), backgroundScope)

                store.rate(book) shouldBe 1.5

                testScheduler.currentTime shouldBe 0L
                api.fetches.shouldBeEmpty()
            }
        }

        // I-SPD-3, R17: a pending rate read starts a background flush that clears it once sent
        it("#168 TC-32: a read with a pending rate sends it in the background and clears it (I-SPD-3)") {
            runTest {
                val api = FakePlaybackRateApi()
                val cache = FakePlaybackRateCache()
                cache.saveLocal(book, 1.5)
                val store = DefaultPlaybackRateStore(api, cache, FakeConnectivityMonitor(true), backgroundScope)

                store.rate(book) shouldBe 1.5
                runCurrent()

                api.puts shouldBe listOf(book to 1.5)
                cache.pending(book).shouldBeNull()
                cache.cached(book) shouldBe 1.5
            }
        }

        // I-SPD-1: a pending rate is clamped to the bounds when it is read
        it("#168 TC-33: a pending rate outside the bounds reads clamped (I-SPD-1)") {
            runTest {
                val cache = FakePlaybackRateCache()
                cache.saveLocal("high", 4.0)
                cache.saveLocal("low", 0.1)
                val store = DefaultPlaybackRateStore(FakePlaybackRateApi(), cache, FakeConnectivityMonitor(true), backgroundScope)

                store.rate("high") shouldBe 3.0
                store.rate("low") shouldBe 0.5
            }
        }

        // P-SPD-5, I-SPD-4: offline the read is the cache, and the network is not touched
        it("#168 TC-34: offline reads the cache with no network call (P-SPD-5, I-SPD-4)") {
            runTest {
                val api = FakePlaybackRateApi()
                val cache = FakePlaybackRateCache().apply { cachedRates[book] = 1.25 }
                val store = DefaultPlaybackRateStore(api, cache, FakeConnectivityMonitor(false), backgroundScope)

                store.rate(book) shouldBe 1.25
                store.rate("book-empty").shouldBeNull()

                runCurrent()
                api.fetches.shouldBeEmpty()
                api.puts.shouldBeEmpty()
            }
        }

        // I-SPD-4: an answer that arrives in time is returned and refreshes the cache
        it("#168 TC-35: an immediate server answer is returned and cached (I-SPD-4)") {
            runTest {
                val api = FakePlaybackRateApi().apply { defaultFetch = RateFetch.Found(1.75) }
                val cache = FakePlaybackRateCache()
                val store = DefaultPlaybackRateStore(api, cache, FakeConnectivityMonitor(true), backgroundScope)

                store.rate(book) shouldBe 1.75

                cache.cached(book) shouldBe 1.75
            }
        }

        // I-SPD-1: the server's rate is clamped, and the cache keeps the clamped value
        it("#168 TC-36: a server rate above the bounds is clamped and the clamped value is cached (I-SPD-1)") {
            runTest {
                val api = FakePlaybackRateApi().apply { defaultFetch = RateFetch.Found(9.0) }
                val cache = FakePlaybackRateCache()
                val store = DefaultPlaybackRateStore(api, cache, FakeConnectivityMonitor(true), backgroundScope)

                store.rate(book) shouldBe 3.0

                cache.cached(book) shouldBe 3.0
            }
        }

        // I-SPD-4: the listener never set a rate on the server, so the cache forgets it and the book plays at 1.0
        it("#168 TC-37: an unset server answer reads null and clears the cache (I-SPD-4)") {
            runTest {
                val connectivity = FakeConnectivityMonitor(true)
                val api = FakePlaybackRateApi().apply { defaultFetch = RateFetch.Unset }
                val cache = FakePlaybackRateCache().apply { cachedRates[book] = 1.25 }
                val store = DefaultPlaybackRateStore(api, cache, connectivity, backgroundScope)

                store.rate(book).shouldBeNull()

                cache.cached(book).shouldBeNull()
                connectivity.isOnline.value = false
                store.rate(book).shouldBeNull()
            }
        }

        // I-SPD-4: a failed read falls back to the cache, and to null with no cache
        it("#168 TC-38: a failed read answers the cache, or null with an empty cache (I-SPD-4)") {
            runTest {
                val api = FakePlaybackRateApi().apply { defaultFetch = RateFetch.Failed }
                val cache = FakePlaybackRateCache().apply { cachedRates[book] = 1.25 }
                val store = DefaultPlaybackRateStore(api, cache, FakeConnectivityMonitor(true), backgroundScope)

                store.rate(book) shouldBe 1.25
                cache.cached(book) shouldBe 1.25

                store.rate("book-empty").shouldBeNull()
            }
        }

        // I-SPD-4, R15: the answer waits for exactly 1.2 s of virtual time
        it("#168 TC-39: the deadline returns the cache at exactly 1200 ms, not before (I-SPD-4)") {
            runTest {
                val held = CompletableDeferred<RateFetch>()
                val heldEmpty = CompletableDeferred<RateFetch>()
                val api = FakePlaybackRateApi().apply {
                    fetchAnswers[book] = held
                    fetchAnswers["book-empty"] = heldEmpty
                }
                val cache = FakePlaybackRateCache().apply { cachedRates[book] = 1.25 }
                val store = DefaultPlaybackRateStore(api, cache, FakeConnectivityMonitor(true), backgroundScope)
                val cached = async { store.rate(book) }
                val empty = async { store.rate("book-empty") }
                runCurrent()

                advanceTimeBy(1_199)
                runCurrent()
                cached.isCompleted shouldBe false
                empty.isCompleted shouldBe false

                advanceTimeBy(1)
                runCurrent()
                cached.await() shouldBe 1.25
                empty.await().shouldBeNull()
            }
        }

        // I-SPD-4: an answer inside the deadline wins over the cache
        it("#168 TC-40: an answer inside the deadline is returned, not the cache (I-SPD-4)") {
            runTest {
                val gate = CompletableDeferred<RateFetch>()
                val api = FakePlaybackRateApi().apply { fetchAnswers[book] = gate }
                val cache = FakePlaybackRateCache().apply { cachedRates[book] = 1.0 }
                val store = DefaultPlaybackRateStore(api, cache, FakeConnectivityMonitor(true), backgroundScope)
                val read = async { store.rate(book) }
                runCurrent()

                advanceTimeBy(1_199)
                runCurrent()
                gate.complete(RateFetch.Found(1.5))
                runCurrent()

                read.await() shouldBe 1.5
            }
        }

        // I-SPD-4: a late answer refreshes the cache but never changes what the caller was given
        it("#168 TC-41: a late answer only refreshes the cache and sends nothing (I-SPD-4)") {
            runTest {
                val gate = CompletableDeferred<RateFetch>()
                val connectivity = FakeConnectivityMonitor(true)
                val api = FakePlaybackRateApi().apply { fetchAnswers[book] = gate }
                val cache = FakePlaybackRateCache().apply { cachedRates[book] = 1.25 }
                val store = DefaultPlaybackRateStore(api, cache, connectivity, backgroundScope)
                val read = async { store.rate(book) }
                runCurrent()
                advanceTimeBy(1_200)
                runCurrent()
                read.await() shouldBe 1.25

                gate.complete(RateFetch.Found(2.0))
                runCurrent()

                cache.cached(book) shouldBe 2.0
                connectivity.isOnline.value = false
                store.rate(book) shouldBe 2.0
                api.puts.shouldBeEmpty()
            }
        }

        // I-SPD-4: a late unset answer clears the cache and leaves the caller's answer alone
        it("#168 TC-42: a late unset answer clears the cache only (I-SPD-4)") {
            runTest {
                val gate = CompletableDeferred<RateFetch>()
                val api = FakePlaybackRateApi().apply { fetchAnswers[book] = gate }
                val cache = FakePlaybackRateCache().apply { cachedRates[book] = 1.25 }
                val store = DefaultPlaybackRateStore(api, cache, FakeConnectivityMonitor(true), backgroundScope)
                val read = async { store.rate(book) }
                runCurrent()
                advanceTimeBy(1_200)
                runCurrent()

                gate.complete(RateFetch.Unset)
                runCurrent()

                read.await() shouldBe 1.25
                cache.cached(book).shouldBeNull()
            }
        }

        // I-SPD-3, I-SPD-4: a save made while a late read is held is not overwritten by that read
        it("#168 TC-43: a stale read does not overwrite a newer save while it is still pending (I-SPD-4)") {
            runTest {
                val gate = CompletableDeferred<RateFetch>()
                val api = FakePlaybackRateApi().apply {
                    fetchAnswers[book] = gate
                    defaultPut = PutOutcome.Keep
                }
                val cache = FakePlaybackRateCache()
                val store = DefaultPlaybackRateStore(api, cache, FakeConnectivityMonitor(true), backgroundScope)
                val read = async { store.rate(book) }
                runCurrent()
                advanceTimeBy(1_200)
                runCurrent()
                read.await().shouldBeNull()

                store.save(book, 2.0)
                runCurrent()
                gate.complete(RateFetch.Found(1.0))
                runCurrent()

                cache.cached(book) shouldBe 2.0
                cache.pending(book) shouldBe 2.0
                store.rate(book) shouldBe 2.0
            }
        }

        // I-SPD-4: the per-book save counter blocks a stale write even after the pending rate is sent; other books are not blocked
        it("#168 TC-44: a save counter blocks a stale write after the pending rate is sent, and other books still refresh (I-SPD-4)") {
            runTest {
                val gate = CompletableDeferred<RateFetch>()
                val gateOther = CompletableDeferred<RateFetch>()
                val api = FakePlaybackRateApi().apply {
                    fetchAnswers[book] = gate
                    fetchAnswers["book-b"] = gateOther
                    defaultPut = PutOutcome.Sent
                }
                val cache = FakePlaybackRateCache()
                val store = DefaultPlaybackRateStore(api, cache, FakeConnectivityMonitor(true), backgroundScope)
                val read = async { store.rate(book) }
                val readOther = async { store.rate("book-b") }
                runCurrent()
                advanceTimeBy(1_200)
                runCurrent()
                read.await().shouldBeNull()
                readOther.await().shouldBeNull()

                store.save(book, 2.0)
                runCurrent()
                cache.pending(book).shouldBeNull()

                gate.complete(RateFetch.Found(1.0))
                gateOther.complete(RateFetch.Found(1.5))
                runCurrent()

                cache.cached(book) shouldBe 2.0
                cache.cached("book-b") shouldBe 1.5
            }
        }

        // R15: a blank uuid is answered null and never reaches the API, the cache or the outbox
        it("#168 TC-45: a blank uuid is ignored by rate and save (R15, R16)") {
            runTest {
                val api = FakePlaybackRateApi()
                val cache = FakePlaybackRateCache().apply {
                    cachedRates[""] = 1.0
                    pendingRates[""] = 1.5
                }
                val store = DefaultPlaybackRateStore(api, cache, FakeConnectivityMonitor(true), backgroundScope)

                store.rate("").shouldBeNull()
                store.rate("  ").shouldBeNull()
                store.save("", 1.5)
                store.save("  ", 1.5)
                runCurrent()

                api.fetches.shouldBeEmpty()
                api.puts.shouldBeEmpty()
                cache.localWrites.shouldBeEmpty()
            }
        }

        // R15, I-SPD-3: a failing API or cache is logged and answered as null; nothing escapes to the scope
        it("#168 TC-46: a failing API or cache never throws from rate or save (R15, R16)") {
            runTest {
                val boom = RuntimeException("boom")
                listOf(true, false).forEach { online ->
                    val failingApi = FakePlaybackRateApi().apply { failWith = boom }
                    val apiStore = DefaultPlaybackRateStore(
                        failingApi,
                        FakePlaybackRateCache(),
                        FakeConnectivityMonitor(online),
                        backgroundScope,
                    )
                    apiStore.rate(book).shouldBeNull()
                    apiStore.save(book, 1.5)
                    runCurrent()

                    val failingCache = FakePlaybackRateCache().apply { failWith = boom }
                    val cacheStore = DefaultPlaybackRateStore(
                        FakePlaybackRateApi(),
                        failingCache,
                        FakeConnectivityMonitor(online),
                        backgroundScope,
                    )
                    cacheStore.rate(book).shouldBeNull()
                    cacheStore.save(book, 1.5)
                    runCurrent()
                }
            }
        }

        // R15, R16: cancellation reaches the caller, and the server read goes on for the cache
        it("#168 TC-47: cancelling a caller throws CancellationException and the server read still refreshes the cache (R15, R16)") {
            runTest {
                val gate = CompletableDeferred<RateFetch>()
                val api = FakePlaybackRateApi().apply { fetchAnswers[book] = gate }
                val cache = FakePlaybackRateCache()
                val store = DefaultPlaybackRateStore(api, cache, FakeConnectivityMonitor(true), backgroundScope)

                val read = async { store.rate(book) }
                runCurrent()
                read.cancel()
                runCurrent()
                shouldThrow<CancellationException> { read.await() }

                gate.complete(RateFetch.Found(1.5))
                runCurrent()
                cache.cached(book) shouldBe 1.5

                cache.saveGate = CompletableDeferred()
                val saver = async { store.save(book, 1.25) }
                runCurrent()
                saver.cancel()
                runCurrent()
                shouldThrow<CancellationException> { saver.await() }
                cache.localWrites.shouldBeEmpty()
            }
        }
    }

    describe("saves") {

        // I-SPD-1, R16: the device is written first, and the send goes on in the background
        it("#168 TC-48: save writes the device before the PUT, and the PUT clears the pending rate (I-SPD-1, I-SPD-3)") {
            runTest {
                val api = FakePlaybackRateApi()
                val cache = FakePlaybackRateCache()
                val store = DefaultPlaybackRateStore(api, cache, FakeConnectivityMonitor(true), backgroundScope)

                store.save(book, 1.25)

                cache.cached(book) shouldBe 1.25
                cache.pending(book) shouldBe 1.25
                api.puts.shouldBeEmpty()

                runCurrent()

                api.puts shouldBe listOf(book to 1.25)
                cache.pending(book).shouldBeNull()
                cache.cached(book) shouldBe 1.25
            }
        }

        // I-SPD-1: non-finite rates are ignored, and out-of-range finite rates are clamped before they are kept
        it("#168 TC-49: save ignores non-finite rates and clamps finite ones (I-SPD-1)") {
            runTest {
                val api = FakePlaybackRateApi()
                val cache = FakePlaybackRateCache()
                val store = DefaultPlaybackRateStore(api, cache, FakeConnectivityMonitor(true), backgroundScope)

                store.save("nan", Double.NaN)
                store.save("inf", Double.POSITIVE_INFINITY)
                store.save("high", 9.0)
                store.save("low", 0.1)
                runCurrent()

                cache.localWrites shouldBe listOf("high" to 3.0, "low" to 0.5)
                api.puts shouldBe listOf("high" to 3.0, "low" to 0.5)
                cache.cached("nan").shouldBeNull()
                cache.cached("inf").shouldBeNull()
            }
        }

        // I-SPD-3: a kept PUT leaves the new speed on the device, offline and after a relaunch
        it("#168 TC-50: a failed PUT keeps the local rate, which is read offline (I-SPD-3)") {
            runTest {
                val connectivity = FakeConnectivityMonitor(true)
                val api = FakePlaybackRateApi().apply { defaultPut = PutOutcome.Keep }
                val cache = FakePlaybackRateCache()
                val store = DefaultPlaybackRateStore(api, cache, connectivity, backgroundScope)

                store.save(book, 1.25)
                runCurrent()

                cache.pending(book) shouldBe 1.25
                cache.cached(book) shouldBe 1.25
                connectivity.isOnline.value = false
                store.rate(book) shouldBe 1.25
            }
        }

        // R17: a rejected PUT is dropped, so it never loops, and the cached rate stays
        it("#168 TC-51: a dropped PUT clears the pending rate and keeps the cache (R17)") {
            runTest {
                val api = FakePlaybackRateApi().apply { defaultPut = PutOutcome.Drop }
                val cache = FakePlaybackRateCache()
                val store = DefaultPlaybackRateStore(api, cache, FakeConnectivityMonitor(true), backgroundScope)

                store.save(book, 1.25)
                runCurrent()

                cache.pending(book).shouldBeNull()
                cache.cached(book) shouldBe 1.25
            }
        }

        // I-SPD-3: offline a save is only written to the device
        it("#168 TC-52: a save offline writes the device and sends nothing (I-SPD-3)") {
            runTest {
                val api = FakePlaybackRateApi()
                val cache = FakePlaybackRateCache()
                val store = DefaultPlaybackRateStore(api, cache, FakeConnectivityMonitor(false), backgroundScope)

                store.save(book, 1.25)
                runCurrent()

                api.puts.shouldBeEmpty()
                cache.pending(book) shouldBe 1.25
                cache.cached(book) shouldBe 1.25
            }
        }

        // I-SPD-1: concurrent saves reach the device in call order, so the last one wins
        it("#168 TC-53: concurrent saves write the device in call order with no lost update (I-SPD-1)") {
            runTest {
                val gate = CompletableDeferred<PutOutcome>()
                val api = FakePlaybackRateApi().apply {
                    putAnswers.addLast(gate)
                    defaultPut = PutOutcome.Sent
                }
                val cache = FakePlaybackRateCache()
                val store = DefaultPlaybackRateStore(api, cache, FakeConnectivityMonitor(true), backgroundScope)

                listOf(1.0, 1.1, 1.2, 1.5, 2.0).map { rate -> launch { store.save(book, rate) } }
                runCurrent()

                cache.pending(book) shouldBe 2.0
                cache.localWrites.map { it.second } shouldBe listOf(1.0, 1.1, 1.2, 1.5, 2.0)

                gate.complete(PutOutcome.Sent)
                runCurrent()
                cache.pending(book).shouldBeNull()
                cache.cached(book) shouldBe 2.0
            }
        }
    }

    describe("flush") {

        // I-SPD-3: a burst while a PUT is in flight goes out as one PUT of the newest rate
        it("#168 TC-54: a burst coalesces to the newest pending rate, with one PUT in flight (I-SPD-3)") {
            runTest {
                val gate = CompletableDeferred<PutOutcome>()
                val api = FakePlaybackRateApi().apply {
                    putAnswers.addLast(gate)
                    defaultPut = PutOutcome.Sent
                }
                val cache = FakePlaybackRateCache()
                val store = DefaultPlaybackRateStore(api, cache, FakeConnectivityMonitor(true), backgroundScope)

                store.save(book, 1.5)
                runCurrent()
                store.save(book, 1.8)
                store.save(book, 2.0)
                runCurrent()

                gate.complete(PutOutcome.Sent)
                runCurrent()

                api.puts shouldBe listOf(book to 1.5, book to 2.0)
                api.maxInFlightPuts shouldBe 1
                cache.pending(book).shouldBeNull()
                cache.cached(book) shouldBe 2.0
            }
        }

        // I-SPD-3: a sent PUT never clears a newer pending rate written while it was in flight
        it("#168 TC-55: a newer pending rate survives an older PUT that was sent (I-SPD-3)") {
            runTest {
                val gate = CompletableDeferred<PutOutcome>()
                val api = FakePlaybackRateApi().apply {
                    putAnswers.addLast(gate)
                    defaultPut = PutOutcome.Sent
                }
                val cache = FakePlaybackRateCache()
                val store = DefaultPlaybackRateStore(api, cache, FakeConnectivityMonitor(true), backgroundScope)

                store.save(book, 2.0)
                runCurrent()
                store.save(book, 3.0)
                runCurrent()

                gate.complete(PutOutcome.Sent)
                runCurrent()

                api.puts shouldBe listOf(book to 2.0, book to 3.0)
                cache.pending(book).shouldBeNull()
            }
        }

        // I-SPD-3: on reconnect every pending book is sent once; sent and rejected ones clear, kept ones stay
        it("#168 TC-56: reconnecting sends each pending book once and clears by outcome (I-SPD-3)") {
            runTest {
                val connectivity = FakeConnectivityMonitor(false)
                val api = FakePlaybackRateApi().apply {
                    putAnswers.addLast(CompletableDeferred(PutOutcome.Sent))
                    putAnswers.addLast(CompletableDeferred(PutOutcome.Drop))
                    putAnswers.addLast(CompletableDeferred(PutOutcome.Keep))
                }
                val cache = FakePlaybackRateCache()
                DefaultPlaybackRateStore(api, cache, connectivity, backgroundScope)
                cache.saveLocal("a", 1.1)
                cache.saveLocal("b", 1.2)
                cache.saveLocal("c", 1.3)
                runCurrent()
                api.puts.shouldBeEmpty()

                connectivity.isOnline.value = true
                runCurrent()

                api.puts shouldBe listOf("a" to 1.1, "b" to 1.2, "c" to 1.3)
                cache.pending("a").shouldBeNull()
                cache.pending("b").shouldBeNull()
                cache.pending("c") shouldBe 1.3
            }
        }

        // I-SPD-3: nothing is sent while offline, and an empty outbox makes no calls
        it("#168 TC-57: a flush does nothing offline or with an empty outbox (I-SPD-3)") {
            runTest {
                val connectivity = FakeConnectivityMonitor(false)
                val api = FakePlaybackRateApi()
                val cache = FakePlaybackRateCache()
                val store = DefaultPlaybackRateStore(api, cache, connectivity, backgroundScope)

                store.save(book, 1.5)
                runCurrent()
                api.puts.shouldBeEmpty()
                cache.pending(book) shouldBe 1.5

                cache.clearPendingIf(book, 1.5)
                connectivity.isOnline.value = true
                runCurrent()
                connectivity.isOnline.value = false
                runCurrent()
                connectivity.isOnline.value = true
                runCurrent()

                api.puts.shouldBeEmpty()
                api.fetches.shouldBeEmpty()
            }
        }

        // I-SPD-3, I-SPD-4: a kept rate is sent at the next load, when a store is built over the same cache
        it("#168 TC-58: a kept rate is retried when the book next loads (I-SPD-3, I-SPD-4)") {
            runTest {
                val api = FakePlaybackRateApi().apply {
                    putAnswers.addLast(CompletableDeferred(PutOutcome.Keep))
                    defaultPut = PutOutcome.Sent
                }
                val cache = FakePlaybackRateCache()
                val first = DefaultPlaybackRateStore(api, cache, FakeConnectivityMonitor(true), backgroundScope)
                runCurrent()
                first.save(book, 1.5)
                runCurrent()
                api.puts shouldBe listOf(book to 1.5)
                cache.pending(book) shouldBe 1.5

                val next = DefaultPlaybackRateStore(api, cache, FakeConnectivityMonitor(true), backgroundScope)
                next.rate(book) shouldBe 1.5
                runCurrent()

                api.puts shouldBe listOf(book to 1.5, book to 1.5)
                cache.pending(book).shouldBeNull()
            }
        }

        // I-SPD-3: after a failed send the next change goes out, and it is the newest value that is sent
        it("#168 TC-59: the next change after a failed send retries with the newest value (I-SPD-3)") {
            runTest {
                val api = FakePlaybackRateApi().apply {
                    putAnswers.addLast(CompletableDeferred(PutOutcome.Keep))
                    defaultPut = PutOutcome.Sent
                }
                val cache = FakePlaybackRateCache()
                val store = DefaultPlaybackRateStore(api, cache, FakeConnectivityMonitor(true), backgroundScope)
                runCurrent()

                store.save(book, 1.5)
                runCurrent()
                store.save(book, 1.8)
                runCurrent()

                api.puts shouldBe listOf(book to 1.5, book to 1.8)
                cache.pending(book).shouldBeNull()
            }
        }
    }
})
