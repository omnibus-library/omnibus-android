package com.omnibus.omnibus.data.audiobook.playback

import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import com.omnibus.omnibus.data.audiobook.AudiobookManifestResult
import com.omnibus.omnibus.data.audiobook.AudiobookManifestError

/** Listener dispatch: the end, settle, ticks, play and pause, background, and listeners that are absent or throw. */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAudiobookControllerListenersTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }
    afterSpec { unmockAndroidLog() }

    lateinit var scheduler: TestCoroutineScheduler
    val rigs = mutableListOf<ControllerRig>()

    fun newRig(listeners: Set<PlaybackListener> = emptySet()) =
        ControllerRig(scheduler, listeners = listeners).also { rigs += it }

    beforeEach {
        scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
    }

    afterEach {
        rigs.forEach { it.close() }
        rigs.clear()
        Dispatchers.resetMain()
    }

    describe("onEnded") {
        // TC-64: the end fires once, with the position at the duration and playback stopped, and no pause.
        it("fires at the end with the position at the duration (I-SAVE-2)") {
            val listener = CallRecorder()
            val rig = newRig(listeners = setOf(listener))
            rig.loadAndSettle()
            rig.engine.emit(EngineEvent.Ended)
            rig.engine.push(EngineSnapshot(positionSeconds = 3_000.0, durationSeconds = 3_000.0, hasMedia = true))
            rig.settle()
            listener.count("ended") shouldBe 1
            listener.count("pause") shouldBe 0
            listener.ended.single().positionSeconds shouldBe 3_000.0
            listener.ended.single().isPlaying shouldBe false
        }
    }

    describe("onSettled") {
        // TC-67: onSettled fires once, after the seek and before the first play, with the start position.
        it("fires once before auto-start (I-RES-5)") {
            val listener = CallRecorder()
            val rig = ControllerRig(scheduler, resolver = ScriptedResolver(settleAnswer = answering(90.0)), listeners = setOf(listener))
                .also { rigs += it }
            rig.loadAndSettle()
            listener.calls shouldBe listOf("settled:90.0", "play")
            rig.engine.calls.contains("seekTo(90.0)") shouldBe true
        }

        // TC-67: a paused load settles once, and does not play.
        it("fires once for a paused load (I-RES-5)") {
            val listener = CallRecorder()
            val rig = newRig(listeners = setOf(listener))
            rig.loadAndSettle(autoplay = false)
            listener.calls shouldBe listOf("settled:0.0")
        }

        // TC-67: a same-book no-op and a failed load do not settle.
        it("not for no-ops or failures (I-RES-5)") {
            val listener = CallRecorder()
            val rig = newRig(listeners = setOf(listener))
            rig.loadAndSettle()
            rig.controller.load("book-1", null, null, true)
            rig.settle()
            listener.count("settled") shouldBe 1

            rig.repository.result = AudiobookManifestResult.Failure(AudiobookManifestError.Unreachable)
            rig.controller.load("book-2", null, null, true)
            rig.settle()
            listener.count("settled") shouldBe 1
        }
    }

    describe("onTick") {
        // TC-68: a tick every 0.5 s while playing: four in two seconds.
        it("fires every 0.5 s while playing (I-SAVE-2)") {
            val listener = CallRecorder()
            val rig = newRig(listeners = setOf(listener))
            rig.loadAndSettle()
            listener.count("tick") shouldBe 0
            rig.advance(2_000)
            listener.count("tick") shouldBe 4
        }

        // TC-68: each tick carries the engine's position and the chapter at it.
        it("reports the engine's position and chapter on each tick (I-SAVE-2)") {
            val listener = CallRecorder()
            val rig = newRig(listeners = setOf(listener))
            rig.loadAndSettle()
            rig.engine.snapshot = rig.engine.snapshot.copy(positionSeconds = 1_000.0)
            rig.advance(500)
            listener.ticks.last().positionSeconds shouldBe 1_000.0
            listener.ticks.last().currentChapterIndex shouldBe 1
        }

        // TC-68: no ticks while paused, and they resume on play.
        it("never while paused (I-SAVE-2)") {
            val listener = CallRecorder()
            val rig = newRig(listeners = setOf(listener))
            rig.loadAndSettle()
            rig.controller.pause()
            rig.settle()
            val before = listener.count("tick")
            rig.advance(2_000)
            listener.count("tick") shouldBe before
            rig.controller.play()
            rig.settle()
            rig.advance(500)
            listener.count("tick") shouldBe before + 1
        }

        // TC-68: stop ends the tick loop.
        it("stops after stop()") {
            val listener = CallRecorder()
            val rig = newRig(listeners = setOf(listener))
            rig.loadAndSettle()
            rig.advance(1_000)
            rig.controller.stop()
            rig.settle()
            val before = listener.count("tick")
            rig.advance(5_000)
            listener.count("tick") shouldBe before
        }
    }

    describe("onPlay and onPause") {
        // TC-69: play fires on each start and pause on each user or focus pause, but not on end, unload or rebuild.
        it("fire on play and on every pause but not on end, unload or rebuild (R10)") {
            val listener = CallRecorder()
            val rig = newRig(listeners = setOf(listener))
            rig.loadAndSettle()
            listener.count("play") shouldBe 1

            rig.controller.pause()
            rig.settle()
            listener.count("pause") shouldBe 1

            rig.controller.play()
            rig.settle()
            listener.count("play") shouldBe 2
            rig.engine.emit(EngineEvent.PausedExternally)
            rig.engine.push(rig.engine.snapshot().copy(isPlaying = false, isAudible = false))
            rig.settle()
            listener.count("pause") shouldBe 2

            rig.controller.play()
            rig.settle()
            listener.count("play") shouldBe 3
            rig.engine.emit(EngineEvent.Ended)
            rig.engine.push(rig.engine.snapshot().copy(isPlaying = false, isAudible = false, positionSeconds = 3_000.0))
            rig.settle()
            listener.count("pause") shouldBe 2

            rig.controller.play()
            rig.settle()
            listener.count("play") shouldBe 4
            rig.controller.stop()
            rig.settle()
            listener.count("pause") shouldBe 2

            rig.loadAndSettle()
            listener.count("play") shouldBe 5
            rig.engine.fail(FailureKind.Stream)
            rig.settle()
            rig.advance(1_000)
            listener.count("pause") shouldBe 2
            listener.count("play") shouldBe 5
        }
    }

    describe("onAppBackground") {
        // TC-70: the background signal reaches listeners only while a book is loaded.
        it("fires with a book loaded, not when idle (R10)") {
            val listener = CallRecorder()
            val rig = newRig(listeners = setOf(listener))
            rig.background.emit()
            rig.settle()
            listener.count("background") shouldBe 0

            rig.loadAndSettle()
            rig.background.emit()
            rig.settle()
            listener.count("background") shouldBe 1
            listener.backgrounds.single().book?.bookUuid shouldBe "book-1"
        }
    }

    describe("no listeners") {
        // TC-71: an empty listener set works for a whole load, pause and stop.
        it("works with an empty set (R10)") {
            val rig = newRig()
            rig.loadAndSettle()
            rig.controller.pause()
            rig.settle()
            rig.controller.play()
            rig.settle()
            rig.controller.stop()
            rig.settle()
            rig.controller.state.value shouldBe PlaybackState()
        }

        // TC-71: a listener that throws never stops the others or the controller.
        it("keeps going when a listener throws (C4)") {
            val good = CallRecorder()
            val rig = newRig(listeners = setOf(ExplodingListener(), good))
            rig.loadAndSettle()
            good.count("settled") shouldBe 1
            good.count("play") shouldBe 1
            rig.controller.state.value.isPlaying shouldBe true
            rig.controller.pause()
            rig.settle()
            good.count("pause") shouldBe 1
        }
    }
})

/** A listener that throws from every callback. */
private class ExplodingListener : PlaybackListener {
    override fun onSettled(state: PlaybackState, startSeconds: Double) = boom()
    override fun onTick(state: PlaybackState) = boom()
    override fun onPlay(state: PlaybackState) = boom()
    override fun onPause(state: PlaybackState) = boom()
    override fun onAppBackground(state: PlaybackState) = boom()
    override fun onEnded(state: PlaybackState) = boom()
    override suspend fun onUnload(state: PlaybackState, reason: UnloadReason) = boom()

    private fun boom(): Nothing = throw IllegalStateException("listener failed")
}
