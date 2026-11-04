package com.omnibus.omnibus.data.audiobook.playback

import com.omnibus.omnibus.data.audiobook.AudiobookManifestResult
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** Health: the rebuild budget, buffering, load failure mapping and the rebuild's quiet transitions. */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAudiobookControllerHealthTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }
    afterSpec { unmockAndroidLog() }

    lateinit var scheduler: TestCoroutineScheduler
    val rigs = mutableListOf<ControllerRig>()

    fun newRig(
        resolver: ResumeStartResolver? = null,
        listeners: Set<PlaybackListener> = emptySet(),
    ) = ControllerRig(scheduler, resolver = resolver, listeners = listeners).also { rigs += it }

    beforeEach {
        scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
    }

    afterEach {
        rigs.forEach { it.close() }
        rigs.clear()
        Dispatchers.resetMain()
    }

    describe("rebuild") {
        // TC-37: a failure while playing at 120 s rebuilds after 1 s, then after 2 s and 3 s, and resumes each time.
        // The rebuilt item does not report playing (reflect is off), so the failures stay consecutive.
        it("rebuilds at the current position after attempt seconds (I-HLTH-2)") {
            val rig = newRig()
            rig.loadAndSettle()
            rig.controller.seekTo(120.0)
            rig.engine.reflect = false

            rig.engine.emit(EngineEvent.Failed(FailureKind.Stream))
            rig.settle()
            var mark = rig.engine.calls.size
            rig.advance(999)
            rig.engine.calls.drop(mark).none { it == "setSource" } shouldBe true
            rig.advance(1)
            rig.engine.calls.drop(mark) shouldBe listOf("setSource", "seekTo(120.0)", "play")

            rig.engine.emit(EngineEvent.Failed(FailureKind.Stream))
            rig.settle()
            mark = rig.engine.calls.size
            rig.advance(1_999)
            rig.engine.calls.drop(mark).none { it == "setSource" } shouldBe true
            rig.advance(1)
            rig.engine.calls.drop(mark) shouldBe listOf("setSource", "seekTo(120.0)", "play")

            rig.engine.emit(EngineEvent.Failed(FailureKind.Stream))
            rig.settle()
            mark = rig.engine.calls.size
            rig.advance(2_999)
            rig.engine.calls.drop(mark).none { it == "setSource" } shouldBe true
            rig.advance(1)
            rig.engine.calls.drop(mark) shouldBe listOf("setSource", "seekTo(120.0)", "play")
            rig.controller.state.value.error shouldBe null
            rig.controller.state.value.isPlaying shouldBe true
        }
    }

    describe("resume rules") {
        // TC-38: a paused item is rebuilt but stays paused.
        it("resumes only if playback was under way or owed (I-HLTH-2)") {
            val paused = newRig()
            paused.loadAndSettle(autoplay = false)
            paused.engine.emit(EngineEvent.Failed(FailureKind.Stream))
            paused.settle()
            val mark = paused.engine.calls.size
            paused.advance(1_000)
            paused.engine.calls.drop(mark) shouldBe listOf("setSource", "seekTo(0.0)")
            paused.controller.state.value.isPlaying shouldBe false

            // A start that is owed by a load still in flight resumes once the rebuild lands.
            val held = CompletableDeferred<Double?>()
            val owed = newRig(resolver = ScriptedResolver(settleAnswer = held))
            owed.loadAndSettle()
            owed.engine.calls.contains("setSource") shouldBe true
            owed.engine.emit(EngineEvent.Failed(FailureKind.Stream))
            owed.settle()
            val owedMark = owed.engine.calls.size
            owed.advance(1_000)
            owed.engine.calls.drop(owedMark).shouldBeInOrder("setSource", "play")
            held.complete(null)
            owed.settle()
            owed.controller.state.value.isLoading shouldBe false
        }

        // TC-38: a pause during the rebuild wait cancels the resume, but the rebuild still happens, paused.
        it("a pause during the wait cancels the resume, not the rebuild (I-HLTH-4)") {
            val rig = newRig()
            rig.loadAndSettle()
            rig.engine.emit(EngineEvent.Failed(FailureKind.Stream))
            rig.settle()
            rig.controller.pause()
            rig.settle()
            val mark = rig.engine.calls.size
            rig.advance(1_000)
            rig.engine.calls.drop(mark) shouldBe listOf("setSource", "seekTo(0.0)")
            rig.controller.state.value.isPlaying shouldBe false
        }
    }

    describe("surrender") {
        // TC-39: the fourth consecutive failure leaves the item paused with StoppedAfterRetries, and no rebuild follows.
        it("stops after 3 retries (I-HLTH-3)") {
            val rig = newRig()
            rig.loadAndSettle()
            repeat(4) { rig.engine.fail(FailureKind.Stream) }
            rig.settle()
            rig.controller.state.value.error shouldBe PlaybackError.StoppedAfterRetries
            rig.controller.state.value.isPlaying shouldBe false
            val mark = rig.engine.calls.size
            rig.advance(10_000)
            rig.engine.calls.drop(mark).none { it == "setSource" } shouldBe true
        }

        // TC-39: play clears the error, refills the budget and rebuilds, then plays.
        it("play refills the budget and rebuilds (I-HLTH-3)") {
            val rig = newRig()
            rig.loadAndSettle()
            repeat(4) { rig.engine.fail(FailureKind.Stream) }
            rig.settle()
            val mark = rig.engine.calls.size
            rig.controller.play()
            rig.settle()
            rig.advance(1_000)
            rig.engine.calls.drop(mark) shouldBe listOf("setSource", "seekTo(0.0)", "play")
            rig.controller.state.value.error shouldBe null
            rig.controller.state.value.isPlaying shouldBe true
        }

        // TC-39: a real playback between failures resets the count, so three later failures still rebuild.
        it("playing resets the count (I-HLTH-2)") {
            val rig = newRig()
            rig.loadAndSettle()
            rig.engine.fail(FailureKind.Stream)
            rig.engine.fail(FailureKind.Stream)
            rig.engine.push(EngineSnapshot(hasMedia = true, isPlaying = true, isAudible = true))
            repeat(3) { rig.engine.fail(FailureKind.Stream) }
            rig.settle()
            rig.controller.state.value.error shouldBe null
        }
    }

    describe("buffering") {
        // TC-40: buffering while playing shows buffering and never rebuilds.
        it("shows buffering and never rebuilds (I-HLTH-1)") {
            val rig = newRig()
            rig.loadAndSettle()
            rig.engine.push(EngineSnapshot(hasMedia = true, isPlaying = true, isBuffering = true))
            rig.settle()
            rig.controller.state.value.isBuffering shouldBe true
            val mark = rig.engine.calls.size
            rig.advance(10_000)
            rig.engine.calls.drop(mark).none { it == "setSource" } shouldBe true
            rig.engine.push(EngineSnapshot(hasMedia = true, isPlaying = true, isAudible = true))
            rig.controller.state.value.isBuffering shouldBe false
        }

        // TC-40: buffering while paused (playWhenReady false) does not show buffering, and does not rebuild.
        it("paused buffering leaves isBuffering false (I-HLTH-1)") {
            val rig = newRig()
            rig.loadAndSettle(autoplay = false)
            val mark = rig.engine.calls.size
            rig.engine.push(EngineSnapshot(hasMedia = true, isPlaying = false, isBuffering = true))
            rig.controller.state.value.isBuffering shouldBe false
            rig.advance(10_000)
            rig.engine.calls.drop(mark).none { it == "setSource" } shouldBe true
        }
    }

    describe("load failures") {
        // TC-41: a part with no positive, finite duration is TimelineFailed.
        it("bad durations are TimelineFailed (P-PLAY-10)") {
            listOf(0.0, Double.NaN, -1.0, Double.POSITIVE_INFINITY).forEach { seconds ->
                val rig = newRig()
                rig.repository.result = AudiobookManifestResult.Success(manifestWithFirstPart(seconds))
                rig.loadAndSettle()
                withClue("duration $seconds") {
                    rig.controller.state.value.error shouldBe PlaybackError.TimelineFailed
                    rig.controller.state.value.isLoading shouldBe false
                }
            }
        }

        // TC-41: a media source that fails to build is TimelineFailed.
        it("a source that fails to build is TimelineFailed (P-PLAY-10)") {
            val rig = newRig()
            rig.engine.setSourceFailure = IllegalStateException("no source")
            rig.loadAndSettle()
            rig.controller.state.value.error shouldBe PlaybackError.TimelineFailed
            rig.controller.state.value.isLoading shouldBe false
        }

        // TC-41: no audio track is PartUnreadable, and it is terminal, so no rebuild is scheduled (I-LOAD-4).
        it("no audio track is PartUnreadable and terminal (I-LOAD-4)") {
            val rig = newRig()
            rig.loadAndSettle()
            rig.engine.emit(EngineEvent.NoAudioTrack)
            rig.settle()
            rig.controller.state.value.error shouldBe PlaybackError.PartUnreadable
            rig.controller.state.value.isLoading shouldBe false
            val mark = rig.engine.calls.size
            rig.advance(10_000)
            rig.engine.calls.drop(mark).none { it == "setSource" } shouldBe true
        }

        // TC-41: a parse or decode error before the first ready state is PartUnreadable.
        it("an error before first ready is PartUnreadable (P-PLAY-10)") {
            val rig = newRig()
            rig.loadAndSettle(autoplay = false)
            rig.engine.emit(EngineEvent.Failed(FailureKind.Unreadable))
            rig.settle()
            rig.controller.state.value.error shouldBe PlaybackError.PartUnreadable
            rig.controller.state.value.isLoading shouldBe false
        }

        // TC-41: the same error after the first ready state takes the rebuild path instead.
        it("the same error after ready takes the rebuild path (P-PLAY-10)") {
            val rig = newRig()
            rig.loadAndSettle(autoplay = false)
            rig.engine.emit(EngineEvent.Ready)
            rig.engine.emit(EngineEvent.Failed(FailureKind.Unreadable))
            rig.settle()
            rig.controller.state.value.error shouldBe null
            val mark = rig.engine.calls.size
            rig.advance(1_000)
            rig.engine.calls.drop(mark).contains("setSource") shouldBe true
        }
    }

    describe("rebuild and listeners") {
        // TC-42: a rebuild's own stop and restart fire no onPause, and no duplicate onPlay.
        it("does not report pause for a rebuild (R10)") {
            val listener = CallRecorder()
            val rig = newRig(listeners = setOf(listener))
            rig.loadAndSettle()
            listener.count("settled") shouldBe 1
            listener.count("play") shouldBe 1

            rig.engine.fail(FailureKind.Stream)
            rig.settle()
            rig.advance(1_000)
            rig.controller.state.value.isPlaying shouldBe true
            listener.count("pause") shouldBe 0
            listener.count("play") shouldBe 1
            listener.count("settled") shouldBe 1
        }
    }
})
