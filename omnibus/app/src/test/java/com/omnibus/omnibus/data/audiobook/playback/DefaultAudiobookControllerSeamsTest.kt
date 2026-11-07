package com.omnibus.omnibus.data.audiobook.playback

import com.omnibus.omnibus.data.audiobook.AudiobookManifestError
import com.omnibus.omnibus.data.audiobook.AudiobookManifestResult
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** The optional seams: resume start, rate store and sleep timer, plus the controller with nothing bound (R19). */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAudiobookControllerSeamsTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }
    afterSpec { unmockAndroidLog() }

    lateinit var scheduler: TestCoroutineScheduler
    val rigs = mutableListOf<ControllerRig>()

    fun newRig(
        resolver: ResumeStartResolver? = null,
        rateStore: PlaybackRateStore? = null,
        sleepTimer: SleepTimer? = null,
        listeners: Set<PlaybackListener> = emptySet(),
        order: MutableList<String>? = null,
    ) = ControllerRig(
        scheduler,
        resolver = resolver,
        rateStore = rateStore,
        sleepTimer = sleepTimer,
        listeners = listeners,
        order = order,
    ).also { rigs += it }

    beforeEach {
        scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
    }

    afterEach {
        rigs.forEach { it.close() }
        rigs.clear()
        Dispatchers.resetMain()
    }

    describe("ResumeStartResolver") {
        // TC-72: begin is recorded as the load starts, before the book and the manifest are read.
        it("begin is called as the load starts (I-RES-2)") {
            val order = mutableListOf<String>()
            val resolver = ScriptedResolver(order = order)
            val rig = newRig(resolver = resolver, order = order)
            rig.repository.pending = CompletableDeferred()
            rig.controller.load("book-1", 33L, 120.0, true)
            rig.settle()
            resolver.begins shouldBe listOf(Triple("book-1", 33L, 120.0))
            order.shouldBeInOrder("begin", "getBook", "getManifest")
        }
    }

    describe("settle") {
        // TC-73: a positive answer seeks before auto-start, and settle sees the manifest's file and duration.
        it("seeks to a positive answer before auto-start (I-RES-3)") {
            val listener = CallRecorder()
            val resolver = ScriptedResolver(settleAnswer = answering(300.0))
            val rig = newRig(resolver = resolver, listeners = setOf(listener))
            rig.loadAndSettle()
            resolver.pendings.single().settleCalls shouldBe listOf(11L to 3_000.0)
            rig.controller.state.value.positionSeconds shouldBe 300.0
            rig.engine.calls.shouldBeInOrder("seekTo(300.0)", "play")
            listener.calls shouldBe listOf("settled:300.0", "play")
        }

        // TC-73: a null, zero or negative answer starts at 0 with no seek.
        it("starts at 0 for a null, zero or negative answer (I-RES-3)") {
            listOf<Double?>(null, 0.0, -5.0).forEach { answer ->
                val rig = newRig(resolver = ScriptedResolver(settleAnswer = answering(answer)))
                rig.loadAndSettle()
                withClue("answer $answer") {
                    rig.controller.state.value.positionSeconds shouldBe 0.0
                    rig.engine.calls.any { it.startsWith("seekTo") } shouldBe false
                }
            }
        }

        // TC-73: settle runs once on a completed load, and a paused load still applies its position.
        it("settle is always called once on a completed load (I-RES-5)") {
            val resolver = ScriptedResolver()
            val rig = newRig(resolver = resolver)
            rig.loadAndSettle(autoplay = false)
            resolver.pendings.single().settleCalls.size shouldBe 1

            val pausedResolver = ScriptedResolver(settleAnswer = answering(300.0))
            val paused = newRig(resolver = pausedResolver)
            paused.loadAndSettle(autoplay = false)
            pausedResolver.pendings.single().settleCalls.size shouldBe 1
            paused.controller.state.value.positionSeconds shouldBe 300.0
            paused.engine.calls.contains("play") shouldBe false
        }

        // TC-74: an hls item settles only once the player is ready, with the duration the player reports.
        it("settles after the player is ready (I-RES-3)") {
            val resolver = ScriptedResolver(settleAnswer = answering(300.0))
            val rig = newRig(resolver = resolver)
            rig.repository.result = AudiobookManifestResult.Success(hlsManifest())
            rig.controller.load("book-1", null, null, true)
            rig.settle()
            resolver.pendings.single().settleCalls.shouldBeEmpty()

            rig.engine.snapshot = rig.engine.snapshot.copy(durationSeconds = 1_800.0)
            rig.engine.emit(EngineEvent.Ready)
            rig.settle()
            resolver.pendings.single().settleCalls shouldBe listOf(11L to 1_800.0)
            rig.controller.state.value.positionSeconds shouldBe 300.0
            rig.controller.state.value.durationSeconds shouldBe 1_800.0
        }

        // TC-75: cancel runs only when a load is abandoned: a stop, a new load, or a failure. Never on a pause or a
        // completed load.
        it("only when the load is abandoned (C5)") {
            val done = ScriptedResolver()
            newRig(resolver = done).loadAndSettle()
            done.pendings.single().cancelCount shouldBe 0

            val paused = ScriptedResolver()
            val pausedRig = newRig(resolver = paused)
            val held = CompletableDeferred<AudiobookManifestResult>()
            pausedRig.repository.pending = held
            pausedRig.loadAndSettle()
            pausedRig.controller.pause()
            pausedRig.settle()
            held.complete(AudiobookManifestResult.Success(multiPartManifest()))
            pausedRig.settle()
            paused.pendings.single().cancelCount shouldBe 0

            val stopped = ScriptedResolver()
            val stoppedRig = newRig(resolver = stopped)
            val stopHeld = CompletableDeferred<AudiobookManifestResult>()
            stoppedRig.repository.pending = stopHeld
            stoppedRig.loadAndSettle()
            stoppedRig.controller.stop()
            stoppedRig.settle()
            stopped.pendings.single().cancelCount shouldBe 1

            val replaced = ScriptedResolver()
            val replacedRig = newRig(resolver = replaced)
            replacedRig.repository.pending = CompletableDeferred()
            replacedRig.loadAndSettle()
            replacedRig.repository.pending = null
            replacedRig.controller.load("book-2", null, null, true)
            replacedRig.settle()
            replaced.pendings[0].cancelCount shouldBe 1
            replaced.pendings[1].cancelCount shouldBe 0

            val failed = ScriptedResolver()
            val failedRig = newRig(resolver = failed)
            failedRig.repository.result = AudiobookManifestResult.Failure(AudiobookManifestError.Unreachable)
            failedRig.loadAndSettle()
            failed.pendings.single().cancelCount shouldBe 1
        }
    }

    describe("no resolver") {
        // TC-76: with no resolver, a load starts at the given position, or at 0 when there is none.
        it("starts at positionSeconds ?: 0.0 (C5)") {
            val withPosition = CallRecorder()
            val seeking = newRig(listeners = setOf(withPosition))
            seeking.loadAndSettle(positionSeconds = 120.0)
            seeking.controller.state.value.positionSeconds shouldBe 120.0
            seeking.engine.calls.contains("seekTo(120.0)") shouldBe true
            withPosition.calls.first() shouldBe "settled:120.0"

            val fromStart = CallRecorder()
            val starting = newRig(listeners = setOf(fromStart))
            starting.loadAndSettle(positionSeconds = null)
            starting.controller.state.value.positionSeconds shouldBe 0.0
            starting.engine.calls.any { it.startsWith("seekTo") } shouldBe false
            fromStart.calls.first() shouldBe "settled:0.0"

            // The manifest is asked for the explicit file id only, with no saved file to fall back on.
            val explicit = newRig()
            explicit.loadAndSettle(bookFileId = 33L)
            explicit.repository.requests shouldBe listOf("book-1" to 33L)
        }
    }

    describe("PlaybackRateStore") {
        // TC-77: the rate read never delays the start.
        it("does not delay the start (C6)") {
            val store = TestPlaybackRateStore()
            store.answer = CompletableDeferred()
            val rig = newRig(rateStore = store)
            rig.loadAndSettle()
            rig.engine.calls.contains("play") shouldBe true
            rig.controller.state.value.isPlaying shouldBe true
        }

        // TC-77: a saved rate is applied when it arrives, clamped, and a missing one leaves 1.0. rate(uuid) is read once per load.
        it("applies a clamped answer (C6)") {
            listOf(1.75 to 1.75, 9.0 to 3.0, null to 1.0).forEach { (answer, expected) ->
                val store = TestPlaybackRateStore()
                store.answer = answering(answer)
                val rig = newRig(rateStore = store)
                rig.loadAndSettle()
                withClue("answer $answer") {
                    rig.controller.state.value.rate shouldBe expected
                    store.rateCalls shouldBe listOf("book-1")
                }
            }
        }

        // TC-77: a rate read that arrives after setRate is ignored.
        it("a late answer loses to setRate (C6)") {
            val store = TestPlaybackRateStore()
            val late = CompletableDeferred<Double?>()
            store.answer = late
            val rig = newRig(rateStore = store)
            rig.loadAndSettle()
            rig.controller.setRate(2.0, persist = false)
            late.complete(1.75)
            rig.settle()
            rig.controller.state.value.rate shouldBe 2.0
        }
    }

    describe("setRate") {
        // TC-78: a rate applies at once with only the speed passed, and saves when asked.
        it("applies at once and persists when asked (I-SPD-1)") {
            val store = TestPlaybackRateStore()
            val rig = newRig(rateStore = store)
            rig.loadAndSettle()
            val mark = rig.engine.calls.size
            rig.controller.setRate(2.0, persist = true)
            rig.engine.calls.drop(mark) shouldBe listOf("setSpeed(2.0)")
            rig.controller.state.value.rate shouldBe 2.0
            rig.settle()
            store.saves shouldBe listOf("book-1" to 2.0)

            rig.controller.setRate(2.0, persist = false)
            rig.settle()
            store.saves.size shouldBe 1
        }

        // TC-78: a rate is clamped, and the clamped value is what is saved.
        it("clamps (I-SPD-1)") {
            val store = TestPlaybackRateStore()
            val rig = newRig(rateStore = store)
            rig.loadAndSettle()
            rig.controller.setRate(9.0, persist = true)
            rig.controller.state.value.rate shouldBe 3.0
            rig.settle()
            store.saves shouldBe listOf("book-1" to 3.0)
            rig.controller.setRate(0.1, persist = false)
            rig.controller.state.value.rate shouldBe 0.5
        }

        // TC-78: a NaN rate changes nothing and saves nothing.
        it("ignores NaN (I-SPD-1)") {
            val store = TestPlaybackRateStore()
            val rig = newRig(rateStore = store)
            rig.loadAndSettle()
            val mark = rig.engine.calls.size
            rig.controller.setRate(Double.NaN, persist = true)
            rig.settle()
            rig.controller.state.value.rate shouldBe 1.0
            store.saves.shouldBeEmpty()
            rig.engine.calls.drop(mark).shouldBeEmpty()
        }

        // TC-78: with no store bound, the rate starts at 1.0 and setRate still works.
        it("works with no store (C6)") {
            val rig = newRig()
            rig.loadAndSettle()
            rig.controller.state.value.rate shouldBe 1.0
            rig.controller.setRate(2.0, persist = true)
            rig.settle()
            rig.controller.state.value.rate shouldBe 2.0
            rig.engine.calls.contains("setSpeed(2.0)") shouldBe true
        }
    }

    describe("SleepTimer") {
        // TC-79: the timer's state is mirrored into the controller's state.
        it("mirrors the timer state (C7)") {
            val timer = TestSleepTimer()
            val rig = newRig(sleepTimer = timer)
            rig.settle()
            val running = SleepTimerState(SleepTimerOption.After(10), 600.0)
            timer.state.value = running
            rig.settle()
            rig.controller.state.value.sleepTimer shouldBe running
            timer.state.value = null
            rig.settle()
            rig.controller.state.value.sleepTimer shouldBe null
        }

        // TC-79: setSleepTimer is passed on to the timer.
        it("delegates set (C7)") {
            val timer = TestSleepTimer()
            val rig = newRig(sleepTimer = timer)
            rig.controller.setSleepTimer(SleepTimerOption.EndOfChapter)
            timer.sets shouldBe listOf(SleepTimerOption.EndOfChapter)
        }

        // TC-79: a stop and a book switch clear the timer, and a same-book load does not touch it (I-SLP-3).
        it("stop and a book switch clear it (I-SLP-3)") {
            val timer = TestSleepTimer()
            val rig = newRig(sleepTimer = timer)
            rig.loadAndSettle()
            rig.controller.load("book-1", null, null, true)
            rig.settle()
            timer.sets.shouldBeEmpty()

            rig.controller.load("book-2", null, null, true)
            rig.settle()
            timer.sets shouldBe listOf(null)

            rig.controller.stop()
            rig.settle()
            timer.sets shouldBe listOf(null, null)
        }
    }

    describe("no sleep timer") {
        // TC-80: with no timer bound, setting one does nothing, and a switch or a stop still works.
        it("is a no-op (C7)") {
            val rig = newRig()
            rig.loadAndSettle()
            rig.controller.setSleepTimer(SleepTimerOption.After(5))
            rig.settle()
            rig.controller.state.value.sleepTimer shouldBe null
            rig.controller.load("book-2", null, null, true)
            rig.settle()
            rig.controller.stop()
            rig.settle()
            rig.controller.state.value shouldBe PlaybackState()
        }
    }

    describe("no seams bound") {
        // TC-82: built with every seam empty and no listeners, the whole flow works (R19).
        it("runs end to end with nothing bound (R19)") {
            val rig = newRig()
            rig.loadAndSettle()
            rig.controller.state.value.isPlaying shouldBe true
            rig.controller.pause()
            rig.settle()
            rig.controller.play()
            rig.settle()
            rig.controller.skipForward()
            rig.controller.setRate(2.0, persist = true)
            rig.settle()
            rig.controller.stop()
            rig.settle()
            rig.controller.state.value shouldBe PlaybackState()
        }
    }
})
