package com.omnibus.omnibus.data.audiobook.playback

import com.omnibus.omnibus.data.audiobook.AudiobookManifestError
import com.omnibus.omnibus.data.audiobook.AudiobookManifestResult
import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.data.library.ResumePoint
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** Commands: "play with nothing loaded", same-book loads, switching and failed items, pause, stop, and transport. */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAudiobookControllerCommandsTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }
    afterSpec { unmockAndroidLog() }

    lateinit var scheduler: TestCoroutineScheduler
    val rigs = mutableListOf<ControllerRig>()

    fun newRig(
        resolver: ResumeStartResolver? = null,
        sleepTimer: SleepTimer? = null,
        listeners: Set<PlaybackListener> = emptySet(),
    ) = ControllerRig(scheduler, resolver = resolver, sleepTimer = sleepTimer, listeners = listeners).also { rigs += it }

    beforeEach {
        scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
    }

    afterEach {
        rigs.forEach { it.close() }
        rigs.clear()
        Dispatchers.resetMain()
    }

    describe("play with nothing loaded") {
        // TC-49: the most recent Listening point's book loads and starts at its saved position, with no resolver bound.
        it("loads the most recent audiobook (I-SYS-5)") {
            val rig = newRig()
            rig.recent.points = listOf(resumePoint("book-R", bookFileId = 7L, position = 120.0))
            rig.controller.play()
            rig.settle()
            rig.repository.requests shouldBe listOf("book-R" to 7L)
            rig.availability.calls shouldBe listOf("book-R" to ProgressFormat.Listening)
            rig.engine.calls.contains("seekTo(120.0)") shouldBe true
            rig.controller.state.value.book?.bookUuid shouldBe "book-R"
            rig.controller.state.value.isPlaying shouldBe true
        }

        // TC-49: with a resolver bound, no position is passed, because the resolver decides it.
        it("passes no position when a resolver is bound (I-SYS-5)") {
            val resolver = ScriptedResolver()
            val rig = newRig(resolver = resolver)
            rig.recent.points = listOf(resumePoint("book-R", bookFileId = 7L, position = 120.0))
            rig.controller.play()
            rig.settle()
            resolver.begins shouldBe listOf(Triple("book-R", 7L, null))
        }

        // TC-49: the book cannot open, so nothing loads and nothing changes.
        it("does nothing when it cannot open (I-SYS-5)") {
            val rig = newRig()
            rig.availability.answer = false
            rig.recent.points = listOf(resumePoint("book-R", bookFileId = 7L, position = 120.0))
            rig.controller.play()
            rig.settle()
            rig.repository.requests.shouldBeEmpty()
            rig.controller.state.value.book shouldBe null
            rig.controller.state.value.isPlaying shouldBe false
        }

        // TC-49: only the first Listening point is considered. An older point never stands in for it (I-SYS-5).
        it("never falls back to an older one (I-SYS-5)") {
            val rig = newRig()
            rig.availability.answer = false
            rig.recent.points = listOf(resumePoint("book-first", 1L, 10.0), resumePoint("book-older", 2L, 20.0))
            rig.controller.play()
            rig.settle()
            rig.availability.calls shouldBe listOf("book-first" to ProgressFormat.Listening)
            rig.repository.requests.shouldBeEmpty()
        }

        // TC-49: a failed recent read is an empty list, and nothing throws (I-SYS-5).
        it("survives a failed recent read (I-SYS-5)") {
            val rig = newRig()
            rig.recent.pending = CompletableDeferred<List<ResumePoint>>()
                .apply { completeExceptionally(IllegalStateException("offline")) }
            rig.controller.play()
            rig.settle()
            rig.controller.state.value.book shouldBe null
            rig.repository.requests.shouldBeEmpty()
        }

        // TC-49: a pause before the recent read answers cancels the load (I-SYS-5).
        it("a pause before the read answers cancels the load (I-SYS-5)") {
            val rig = newRig()
            val held = CompletableDeferred<List<ResumePoint>>()
            rig.recent.pending = held
            rig.controller.play()
            rig.settle()
            rig.controller.pause()
            rig.settle()
            held.complete(listOf(resumePoint("book-R", 7L, 120.0)))
            rig.settle()
            rig.repository.requests.shouldBeEmpty()
            rig.controller.state.value.book shouldBe null
            rig.controller.state.value.isPlaying shouldBe false
        }
    }

    describe("same-book load") {
        // TC-53: a same-book load with no position is a no-op that keeps playing.
        it("is a no-op that keeps playing (C2)") {
            val listener = CallRecorder()
            val rig = newRig(listeners = setOf(listener))
            rig.loadAndSettle()
            val mark = rig.engine.calls.size
            rig.controller.load("book-1", null, null, true)
            rig.settle()
            rig.engine.sources.size shouldBe 1
            rig.repository.requests.size shouldBe 1
            rig.engine.calls.drop(mark).shouldBeEmpty()
            rig.controller.state.value.isPlaying shouldBe true
            listener.count("unload") shouldBe 0
            listener.count("settled") shouldBe 1
        }

        // TC-53: a same-book autoplay load while paused plays.
        it("plays if paused with autoplay (C2)") {
            val rig = newRig()
            rig.loadAndSettle()
            rig.controller.pause()
            rig.settle()
            rig.controller.state.value.isPlaying shouldBe false
            val mark = rig.engine.calls.size
            rig.controller.load("book-1", null, null, true)
            rig.engine.calls.drop(mark) shouldBe listOf("play")
            rig.controller.state.value.isPlaying shouldBe true
        }

        // TC-54: a position on a same-book load seeks there, with no reload.
        it("seeks when a position is given (C2)") {
            val rig = newRig()
            rig.loadAndSettle()
            val mark = rig.engine.calls.size
            rig.controller.load("book-1", null, 300.0, true)
            rig.settle()
            rig.repository.requests.size shouldBe 1
            rig.engine.sources.size shouldBe 1
            rig.engine.calls.drop(mark) shouldBe listOf("seekTo(300.0)")
            rig.controller.state.value.positionSeconds shouldBe 300.0
        }

        // TC-54: a different file id is a different item, so it reloads.
        it("a different file id reloads (C2)") {
            val rig = newRig()
            rig.loadAndSettle()
            rig.controller.load("book-1", 33L, null, true)
            rig.settle()
            rig.repository.requests.last() shouldBe ("book-1" to 33L)
            rig.engine.sources.size shouldBe 2
        }
    }

    describe("failed item") {
        // TC-55: a same-book load of an item whose error is set rebuilds it, instead of reusing it.
        it("rebuilds a failed item (C2)") {
            val rig = newRig()
            rig.loadAndSettle()
            repeat(4) { rig.engine.fail(FailureKind.Stream) }
            rig.controller.state.value.error shouldBe PlaybackError.StoppedAfterRetries
            rig.controller.load("book-1", null, null, true)
            rig.settle()
            rig.engine.sources.size shouldBe 2
            rig.repository.requests.size shouldBe 2
            rig.controller.state.value.error shouldBe null
            rig.controller.state.value.isPlaying shouldBe true
        }
    }

    describe("switching books") {
        // TC-56: the old book is unloaded and awaited before the sleep timer is cleared and the new manifest is read.
        it("awaits onUnload(SwitchBook) then clears the sleep timer (I-SLP-3)") {
            val sleep = FakeSleepTimer()
            val gate = CompletableDeferred<Unit>()
            val listener = CallRecorder(unloadGate = gate)
            val rig = ControllerRig(scheduler, sleepTimer = sleep, listeners = setOf(listener)).also { rigs += it }
            rig.loadAndSettle()
            listener.count("unload") shouldBe 0

            rig.controller.load("book-2", null, null, true)
            rig.settle()
            listener.calls.contains("unload:SwitchBook") shouldBe true
            sleep.sets.shouldBeEmpty()
            rig.repository.requests.size shouldBe 1

            gate.complete(Unit)
            rig.settle()
            listener.calls.contains("unloaded:SwitchBook") shouldBe true
            sleep.sets shouldBe listOf(null)
            rig.repository.requests.size shouldBe 2
            rig.engine.sources.last().mediaId shouldBe "book-2"
        }
    }

    describe("pause during a load") {
        // TC-57: a pause during the manifest fetch cancels the auto-start. The load still finishes paused.
        it("cancels the auto-start (I-HLTH-4)") {
            val resolver = ScriptedResolver(settleAnswer = answering(300.0))
            val rig = newRig(resolver = resolver)
            val held = CompletableDeferred<AudiobookManifestResult>()
            rig.repository.pending = held
            rig.loadAndSettle()
            rig.controller.pause()
            rig.settle()
            held.complete(AudiobookManifestResult.Success(multiPartManifest()))
            rig.settle()
            rig.engine.calls.contains("play") shouldBe false
            rig.controller.state.value.isLoading shouldBe false
            rig.controller.state.value.isPlaying shouldBe false
            // The resume position still applies to a paused load.
            rig.engine.calls.contains("seekTo(300.0)") shouldBe true
            rig.controller.state.value.positionSeconds shouldBe 300.0
        }

        // TC-57: togglePlayPause during the load counts as playing, so it pauses and cancels the auto-start.
        it("toggle also cancels (I-HLTH-4)") {
            val rig = newRig()
            val held = CompletableDeferred<AudiobookManifestResult>()
            rig.repository.pending = held
            rig.loadAndSettle()
            rig.controller.state.value.isPlaying shouldBe true
            rig.controller.togglePlayPause()
            rig.settle()
            held.complete(AudiobookManifestResult.Success(multiPartManifest()))
            rig.settle()
            rig.engine.calls.contains("play") shouldBe false
            rig.controller.state.value.isLoading shouldBe false
            rig.controller.state.value.isPlaying shouldBe false
        }

        // TC-57: during a rebuild wait the item counts as playing. A pause then cancels the resume, and the rebuild
        // still happens, paused.
        it("a rebuild counts as playing (I-HLTH-4)") {
            val rig = newRig()
            rig.loadAndSettle()
            rig.engine.fail(FailureKind.Stream)
            rig.settle()
            rig.controller.state.value.isPlaying shouldBe true
            rig.controller.togglePlayPause()
            rig.settle()
            val mark = rig.engine.calls.size
            rig.advance(1_000)
            rig.engine.calls.drop(mark).contains("setSource") shouldBe true
            rig.engine.calls.drop(mark).contains("play") shouldBe false
            rig.controller.state.value.isPlaying shouldBe false
        }
    }

    describe("play after a failed load") {
        // TC-58: play() after a failed load runs the same load again, with its file, position and autoplay, and it plays.
        it("runs the load again (C2)") {
            val rig = newRig()
            rig.repository.result = AudiobookManifestResult.Failure(AudiobookManifestError.Unreachable)
            rig.loadAndSettle(bookFileId = 33L, positionSeconds = 120.0, autoplay = false)
            rig.controller.state.value.error shouldBe PlaybackError.Server(null)

            rig.repository.result = AudiobookManifestResult.Success(multiPartManifest(bookFileId = 33L))
            rig.controller.play()
            rig.settle()
            rig.repository.requests shouldBe listOf("book-1" to 33L, "book-1" to 33L)
            rig.controller.state.value.error shouldBe null
            rig.controller.state.value.isPlaying shouldBe true
            rig.controller.state.value.positionSeconds shouldBe 120.0
        }
    }

    describe("stop") {
        // TC-59: stop waits for onUnload(Stop), then stops the engine, clears the timer and resets the state.
        it("awaits onUnload(Stop) then clears everything (C2)") {
            val sleep = FakeSleepTimer()
            val gate = CompletableDeferred<Unit>()
            val listener = CallRecorder(unloadGate = gate)
            val rig = ControllerRig(scheduler, sleepTimer = sleep, listeners = setOf(listener)).also { rigs += it }
            rig.loadAndSettle()
            rig.controller.stop()
            rig.settle()
            rig.engine.calls.contains("stop") shouldBe false
            sleep.sets.shouldBeEmpty()
            rig.controller.state.value.book shouldNotBe null

            gate.complete(Unit)
            rig.settle()
            rig.engine.calls.contains("stop") shouldBe true
            sleep.sets shouldBe listOf(null)
            rig.controller.state.value shouldBe PlaybackState()
        }

        // TC-59: stop with nothing loaded is a safe no-op.
        it("with nothing loaded does nothing (C2)") {
            val rig = newRig()
            rig.controller.stop()
            rig.settle()
            rig.engine.calls.shouldBeEmpty()
            rig.controller.state.value shouldBe PlaybackState()
        }

        // TC-59: stop during a load cancels the load, and nothing resurrects the state afterwards.
        it("abandons a load in flight (C2)") {
            val resolver = ScriptedResolver()
            val rig = newRig(resolver = resolver)
            val held = CompletableDeferred<AudiobookManifestResult>()
            rig.repository.pending = held
            rig.loadAndSettle()
            rig.controller.stop()
            rig.settle()
            held.complete(AudiobookManifestResult.Success(multiPartManifest()))
            rig.settle()
            rig.controller.state.value shouldBe PlaybackState()
            resolver.pendings.single().cancelCount shouldBe 1
            resolver.pendings.single().settleCalls.shouldBeEmpty()
            rig.engine.sources.shouldBeEmpty()
        }
    }

    describe("before the MediaController connects") {
        // TC-60: with the engine disconnected, commands wait and then apply in call order, before the rebuild they start.
        it("commands wait and apply in order (C2)") {
            val rig = newRig()
            rig.loadAndSettle()
            rig.engine.ready = CompletableDeferred()
            rig.engine.emit(EngineEvent.Disconnected)
            val mark = rig.engine.calls.size
            rig.controller.seekTo(50.0)
            rig.controller.setRate(2.0, persist = false)
            rig.controller.pause()
            rig.controller.play()
            rig.settle()
            rig.engine.calls.drop(mark) shouldBe listOf("awaitReady")

            rig.engine.ready!!.complete(Unit)
            rig.settle()
            rig.advance(1_000)
            rig.engine.calls.drop(mark) shouldBe listOf(
                "awaitReady", "seekTo(50.0)", "setSpeed(2.0)", "pause", "setSource", "seekTo(50.0)", "play",
            )
        }

        // TC-60: during a first load, the rate change applies after the load's reset, and a seek is ignored.
        it("a load's queued calls apply in order and a seek during the load is ignored (C2)") {
            val rig = newRig()
            rig.engine.ready = CompletableDeferred()
            rig.controller.load("book-1", null, null, autoplay = false)
            rig.controller.setRate(2.0, persist = false)
            rig.controller.seekTo(50.0)
            rig.engine.ready!!.complete(Unit)
            rig.settle()
            rig.engine.calls.none { it == "seekTo(50.0)" } shouldBe true
            rig.engine.calls.shouldBeInOrder("awaitReady", "pause", "setSpeed(1.0)", "setSpeed(2.0)", "setSource")
            rig.controller.state.value.rate shouldBe 2.0
        }
    }

    describe("engine failures") {
        // TC-61: no command throws when the engine throws, and stop still resets the state.
        it("never throw and run on main (C2)") {
            val rig = newRig()
            rig.loadAndSettle()
            rig.engine.commandFailure = IllegalStateException("player gone")
            rig.controller.pause()
            rig.controller.play()
            rig.controller.togglePlayPause()
            rig.controller.seekTo(10.0)
            rig.controller.skipBack()
            rig.controller.skipForward()
            rig.controller.setRate(2.0, persist = false)
            rig.controller.stop()
            rig.settle()
            rig.controller.state.value shouldBe PlaybackState()
        }
    }

    describe("transport") {
        // TC-63: skips move 15 s back and 30 s forward in book time, clamped to the book (P-PLAY-3).
        it("skips by 15 and 30 (P-PLAY-3)") {
            val rig = newRig()
            rig.loadAndSettle()
            rig.controller.seekTo(1_000.0)
            rig.controller.skipBack()
            rig.controller.state.value.positionSeconds shouldBe 985.0
            rig.controller.skipForward()
            rig.controller.state.value.positionSeconds shouldBe 1_015.0
            rig.controller.seekTo(10.0)
            rig.controller.skipBack()
            rig.controller.state.value.positionSeconds shouldBe 0.0
            rig.controller.seekTo(2_990.0)
            rig.controller.skipForward()
            rig.controller.state.value.positionSeconds shouldBe 3_000.0
        }

        // TC-63: previous restarts a chapter more than 3 s in, and next goes to the next start (P-PLAY-4, P-PLAY-5).
        it("previous and next chapter (P-PLAY-4, P-PLAY-5)") {
            val rig = newRig()
            rig.loadAndSettle()
            rig.controller.seekTo(800.0)
            rig.controller.previousChapter()
            rig.controller.state.value.positionSeconds shouldBe 700.0
            rig.controller.previousChapter()
            rig.controller.state.value.positionSeconds shouldBe 0.0
            rig.controller.nextChapter()
            rig.controller.state.value.positionSeconds shouldBe 700.0
            rig.controller.seekTo(2_000.0)
            rig.controller.nextChapter()
            rig.controller.state.value.positionSeconds shouldBe 2_000.0
        }

        // TC-63: with no chapters, previous and next do nothing (P-PLAY-4, P-PLAY-5).
        it("does nothing without chapters (P-PLAY-4, P-PLAY-5)") {
            val rig = newRig()
            rig.repository.result = AudiobookManifestResult.Success(multiPartManifest().copy(chapters = emptyList()))
            rig.loadAndSettle()
            val mark = rig.engine.calls.size
            rig.controller.previousChapter()
            rig.controller.nextChapter()
            rig.engine.calls.drop(mark).shouldBeEmpty()
            rig.controller.state.value.positionSeconds shouldBe 0.0
        }

        // TC-63: the state moves at once, before the engine answers.
        it("seeks optimistically (C2)") {
            val rig = newRig()
            rig.loadAndSettle()
            rig.engine.emit(EngineEvent.Disconnected)
            rig.controller.seekTo(1_300.0)
            rig.controller.state.value.positionSeconds shouldBe 1_300.0
            rig.controller.state.value.currentChapterIndex shouldBe 1
        }

        // TC-63: with nothing loaded, or while a load runs, every transport command does nothing.
        it("does nothing with nothing loaded, or while loading (C2)") {
            val idle = newRig()
            idle.controller.skipBack()
            idle.controller.skipForward()
            idle.controller.previousChapter()
            idle.controller.nextChapter()
            idle.controller.seekTo(100.0)
            idle.settle()
            idle.engine.calls.shouldBeEmpty()
            idle.controller.state.value shouldBe PlaybackState()

            val loading = newRig()
            loading.repository.pending = CompletableDeferred()
            loading.controller.load("book-1", null, null, autoplay = false)
            loading.settle()
            val mark = loading.engine.calls.size
            loading.controller.skipBack()
            loading.controller.skipForward()
            loading.controller.previousChapter()
            loading.controller.nextChapter()
            loading.controller.seekTo(100.0)
            loading.settle()
            loading.engine.calls.drop(mark).none { it.startsWith("seekTo") } shouldBe true
            loading.controller.state.value.positionSeconds shouldBe 0.0
            loading.controller.state.value.isLoading shouldBe true
        }
    }
})
