package com.omnibus.omnibus.data.audiobook.rate

import com.omnibus.omnibus.data.audiobook.di.PLAYBACK_RATE_DATASTORE_FILE
import com.omnibus.omnibus.data.audiobook.playback.AudiobookController
import com.omnibus.omnibus.data.audiobook.playback.ControllerRig
import com.omnibus.omnibus.data.audiobook.rate.remote.RateFetch
import com.omnibus.omnibus.data.network.FakeConnectivityMonitor
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * The rate store wired into a real [com.omnibus.omnibus.data.audiobook.playback.DefaultAudiobookController] (C2, C6): the load
 * applies the rate the store answers, and a save reaches the server. The Hilt graph itself is checked by KSP at
 * `assembleDebug`; this spec checks what the module wires.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackRateWiringTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    lateinit var scheduler: TestCoroutineScheduler
    val wirings = mutableListOf<RateWiring>()

    fun newWiring() = RateWiring(scheduler).also { wirings += it }

    beforeEach {
        scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
    }

    afterEach {
        wirings.forEach { it.close() }
        wirings.clear()
        Dispatchers.resetMain()
    }

    describe("controller wiring") {

        // I-SPD-4: an unsent local change is what the book loads at
        it("#168 TC-60: a pending local rate is applied at load (I-SPD-4)") {
            val w = newWiring()
            w.cache.saveLocal("book-1", 1.5)

            w.rig.loadAndSettle()

            w.rig.controller.state.value.rate shouldBe 1.5
            w.rig.engine.calls.contains("setSpeed(1.5)") shouldBe true
        }

        // I-SPD-4: with nothing pending the server's answer is applied at load
        it("#168 TC-60: the server's answer is applied at load when nothing is pending (I-SPD-4)") {
            val w = newWiring()
            w.api.defaultFetch = RateFetch.Found(1.75)

            w.rig.loadAndSettle()

            w.rig.controller.state.value.rate shouldBe 1.75
            w.rig.engine.calls.contains("setSpeed(1.75)") shouldBe true
        }

        // I-SPD-1, I-SPD-3: a persisted change is written to the device, then sent to the server
        it("#168 TC-60: a persisted change is saved, sent and cleared (I-SPD-1, I-SPD-3)") {
            val w = newWiring()
            w.rig.loadAndSettle()

            w.rig.controller.setRate(1.25, persist = true)
            w.rig.settle()

            w.api.puts shouldBe listOf("book-1" to 1.25)
            w.cache.pending("book-1").shouldBeNull()
            w.cache.cached("book-1") shouldBe 1.25
        }

        // I-SPD-2: a live change that is not persisted reaches neither the cache nor the server
        it("#168 TC-60: a non-persisted change writes nothing (I-SPD-2)") {
            val w = newWiring()
            w.rig.loadAndSettle()
            val writesBefore = w.cache.localWrites.size

            w.rig.controller.setRate(2.0, persist = false)
            w.rig.settle()

            w.api.puts.shouldBeEmpty()
            w.cache.localWrites.size shouldBe writesBefore
            w.rig.controller.state.value.rate shouldBe 2.0
        }
    }

    describe("module") {

        // R18: the store takes no controller, so the graph has no cycle, and the file keeps its DataStore suffix
        it("#168 TC-61: the store does not depend on the controller and the rate file has the DataStore suffix (R18)") {
            val parameters = DefaultPlaybackRateStore::class.java.constructors.single().parameterTypes.toList()
            parameters.contains(AudiobookController::class.java) shouldBe false

            PLAYBACK_RATE_DATASTORE_FILE.endsWith(".preferences_pb") shouldBe true
            PLAYBACK_RATE_DATASTORE_FILE shouldNotBe "audiobook_resume_offers.preferences_pb"
            PLAYBACK_RATE_DATASTORE_FILE shouldNotBe "audiobook_positions.preferences_pb"
        }
    }
})

/** A rate store over a fake API and cache, built into a [ControllerRig] on the spec's clock. [close] ends both scopes. */
private class RateWiring(scheduler: TestCoroutineScheduler) {
    val api = FakePlaybackRateApi()
    val cache = FakePlaybackRateCache()
    private val storeScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
    val store = DefaultPlaybackRateStore(api, cache, FakeConnectivityMonitor(true), storeScope)
    val rig = ControllerRig(scheduler, rateStore = store)

    fun close() {
        rig.close()
        storeScope.cancel()
    }
}
