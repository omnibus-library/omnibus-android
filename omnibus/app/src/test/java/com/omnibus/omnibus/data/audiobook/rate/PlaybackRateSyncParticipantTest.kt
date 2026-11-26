package com.omnibus.omnibus.data.audiobook.rate

import com.omnibus.omnibus.data.audiobook.rate.remote.PutOutcome
import com.omnibus.omnibus.data.network.TestConnectivityMonitor
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomDouble
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** A rate inside the bounds, so it reads back as it was given. */
private fun randomRate(): Double = randomDouble(0.5, 3.0)

class PlaybackRateSyncParticipantTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    /**
     * The participant over a store on the test's background scope. The store's start-up flush runs before this
     * returns, so only the rates the test saves afterwards are pending.
     */
    fun TestScope.participant(
        api: TestPlaybackRateApi = TestPlaybackRateApi(),
        cache: TestPlaybackRateCache = TestPlaybackRateCache(),
        connectivity: TestConnectivityMonitor = TestConnectivityMonitor(online = true),
    ): PlaybackRateSyncParticipant {
        val store = DefaultPlaybackRateStore(api, cache, connectivity, backgroundScope)
        runCurrent()
        return PlaybackRateSyncParticipant(store, cache)
    }

    describe("reporting rates waiting to be sent") {

        it("#369 TC-6: has nothing pending when no rate is stored") {
            runTest {
                participant().hasPending().shouldBeFalse()
            }
        }

        it("#369 TC-6: has a pending rate once one is saved") {
            runTest {
                val cache = TestPlaybackRateCache()
                val participant = participant(cache = cache)

                cache.saveLocal(randomString(), randomRate())

                participant.hasPending().shouldBeTrue()
            }
        }
    }

    describe("sending the pending rates") {

        it("#369 TC-6: is true with nothing pending, even offline") {
            runTest {
                participant(connectivity = TestConnectivityMonitor(online = false)).sendPending() shouldBe true
            }
        }

        it("#369 TC-6: is true when every pending rate is accepted") {
            runTest {
                val cache = TestPlaybackRateCache()
                val participant = participant(cache = cache)
                cache.saveLocal(randomString(), randomRate())

                participant.sendPending() shouldBe true
                participant.hasPending().shouldBeFalse()
            }
        }

        it("#369 TC-6: is false when the server keeps or rejects a pending rate") {
            runTest {
                checkAll(Exhaustive.of(PutOutcome.Keep, PutOutcome.Drop)) { outcome ->
                    val api = TestPlaybackRateApi().apply { defaultPut = outcome }
                    val cache = TestPlaybackRateCache()
                    val participant = participant(api = api, cache = cache)
                    cache.saveLocal(randomString(), randomRate())

                    participant.sendPending() shouldBe false
                }
            }
        }

        it("#369 TC-6: is false while offline") {
            runTest {
                val cache = TestPlaybackRateCache()
                val participant = participant(cache = cache, connectivity = TestConnectivityMonitor(online = false))
                cache.saveLocal(randomString(), randomRate())

                participant.sendPending() shouldBe false
            }
        }

        it("#369 TC-6: keeps a rate the server did not accept pending") {
            runTest {
                val api = TestPlaybackRateApi().apply { defaultPut = PutOutcome.Keep }
                val cache = TestPlaybackRateCache()
                val participant = participant(api = api, cache = cache)
                cache.saveLocal(randomString(), randomRate())

                participant.sendPending()

                participant.hasPending().shouldBeTrue()
            }
        }
    }
})
