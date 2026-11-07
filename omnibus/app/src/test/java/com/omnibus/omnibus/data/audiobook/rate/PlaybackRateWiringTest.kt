package com.omnibus.omnibus.data.audiobook.rate

import com.omnibus.omnibus.data.audiobook.playback.ControllerRig
import com.omnibus.omnibus.data.audiobook.rate.remote.RateFetch
import com.omnibus.omnibus.data.network.TestConnectivityMonitor
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler

/** A rate store over a fake API and cache, built into a [ControllerRig] on one virtual clock. */
@OptIn(ExperimentalCoroutinesApi::class)
private class RateWiring : AutoCloseable {
    private val scheduler = TestCoroutineScheduler()
    private val storeScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
    val api = TestPlaybackRateApi()
    val rig = ControllerRig(
        scheduler,
        rateStore = DefaultPlaybackRateStore(api, TestPlaybackRateCache(), TestConnectivityMonitor(true), storeScope),
    )

    override fun close() {
        rig.close()
        storeScope.cancel()
    }
}

/** The rate store through a real [com.omnibus.omnibus.data.audiobook.playback.DefaultAudiobookController] (C2, C6). */
class PlaybackRateWiringTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("the controller's rate") {

        // I-SPD-4
        it("#168 TC-60: is the store's answer at load (I-SPD-4)") {
            RateWiring().use { w ->
                w.api.defaultFetch = RateFetch.Found(1.75)

                w.rig.loadAndSettle()

                w.rig.controller.state.value.rate shouldBe 1.75
            }
        }

        context("when a change is persisted") {
            // I-SPD-1, I-SPD-3
            it("#168 TC-60: reaches the server (I-SPD-1, I-SPD-3)") {
                RateWiring().use { w ->
                    w.rig.loadAndSettle()

                    w.rig.controller.setRate(1.25, persist = true)
                    w.rig.settle()

                    w.api.puts shouldBe listOf("book-1" to 1.25)
                }
            }
        }
    }
})
