package com.omnibus.omnibus.data.audiobook.position

import com.omnibus.omnibus.data.audiobook.AudiobookManifestError
import com.omnibus.omnibus.data.audiobook.AudiobookManifestResult
import com.omnibus.omnibus.data.audiobook.playback.CallRecorder
import com.omnibus.omnibus.data.audiobook.playback.ControllerRig
import com.omnibus.omnibus.data.audiobook.playback.PlaybackListener
import com.omnibus.omnibus.data.readstatus.FakeReadStatusRepository
import com.omnibus.omnibus.data.readstatus.ReadStatus
import com.omnibus.omnibus.di.ClientIdSource
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
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
 * The position feature wired through a real [com.omnibus.omnibus.data.audiobook.playback.DefaultAudiobookController]
 * (C5, C4, C8): the resume start reaches the load, the deadline runs from the start of the load, the cancellation rules
 * (CC1), the late offer, and the three listeners together. Every fake is shared with the unit specs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListeningPositionWiringTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }
    afterSpec { unmockAndroidLog() }

    lateinit var scheduler: TestCoroutineScheduler
    val wirings = mutableListOf<Wiring>()

    fun newWiring(order: MutableList<String>? = null) = Wiring(scheduler, order).also { wirings += it }

    beforeEach {
        scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
    }

    afterEach {
        wirings.forEach { it.close() }
        wirings.clear()
        Dispatchers.resetMain()
    }

    describe("resume") {
        // TC-80: the server's answer, once in hand, is the start the controller seeks to before auto-start.
        it("TC-80: settle feeds the start of the load (I-RES-3)") {
            val w = newWiring()
            w.repo.serverAnswers["book-1"] = answered(ServerAudioPosition(120.0, 11L, 20L))
            w.rig.loadAndSettle()
            w.rig.engine.calls.contains("seekTo(120.0)") shouldBe true
            w.recorder.calls.contains("settled:120.0") shouldBe true
        }

        // TC-81: with no answer, the local start waits for the deadline, counted from the start of the load.
        it("TC-81: the deadline runs from the start of the load (I-RES-2)") {
            val w = newWiring()
            w.repo.locals["book-1"] = AudioPosition("book-1", 90.0, 11L, 10L)
            w.repo.serverAnswers["book-1"] = CompletableDeferred()
            w.rig.controller.load("book-1", null, null, true)
            w.rig.advance(1_199)
            w.recorder.count("settled") shouldBe 0
            w.rig.advance(1)
            w.recorder.calls.contains("settled:90.0") shouldBe true
        }

        // TC-82: a pause during a load is not an abandonment: the load still settles and the resolver is not cancelled.
        it("TC-82: a pause during a load does not cancel the resolver (CC1)") {
            val w = newWiring()
            val gate = CompletableDeferred<ServerAudioPosition?>()
            w.repo.serverAnswers["book-1"] = gate
            w.rig.controller.load("book-1", null, null, true)
            w.rig.settle()
            w.rig.controller.pause()
            w.rig.advance(1_200)
            w.recorder.count("settled:") shouldBe 1
            w.rig.controller.state.value.isLoading shouldBe false
            w.repo.serverCancellations shouldBe 0
        }

        // TC-83: stop, a new load and a failed manifest each abandon the load, cancelling its server read; a late answer
        // after that is never offered.
        it("TC-83: stop, a new load and a failure cancel the pending resume (CC1)") {
            val rows = listOf<Pair<String, (Wiring) -> Unit>>(
                "stop mid-load" to { w ->
                    w.rig.repository.pending = CompletableDeferred()
                    w.rig.controller.load("book-1", null, null, true)
                    w.rig.settle()
                    w.rig.controller.stop()
                    w.rig.settle()
                },
                "new load mid-load" to { w ->
                    w.rig.repository.pending = CompletableDeferred()
                    w.rig.controller.load("book-1", null, null, true)
                    w.rig.settle()
                    w.rig.controller.load("book-2", null, null, true)
                    w.rig.settle()
                },
                "manifest failure" to { w ->
                    w.rig.repository.result = AudiobookManifestResult.Failure(AudiobookManifestError.Unreachable)
                    w.rig.controller.load("book-1", null, null, true)
                    w.rig.settle()
                },
            )
            rows.forEach { (name, act) ->
                withClue(name) {
                    val w = newWiring()
                    val gate = CompletableDeferred<ServerAudioPosition?>()
                    w.repo.serverAnswers["book-1"] = gate
                    act(w)
                    w.repo.serverCancellations shouldBe 1
                    gate.complete(ServerAudioPosition(600.0, 11L, 20L))
                    w.rig.settle()
                    w.sync.lateServerPosition.value shouldBe null
                    w.repo.saves.filter { it.first.bookUuid == "book-1" }.shouldBeEmpty()
                }
            }
        }
    }

    describe("late answer") {
        // TC-84: a server answer after the deadline reaches lateServerPosition, and stop clears it.
        it("TC-84: a late answer reaches lateServerPosition and clears on stop (I-RES-6)") {
            val w = newWiring()
            w.repo.locals["book-1"] = AudioPosition("book-1", 30.0, 11L, 10L)
            val gate = CompletableDeferred<ServerAudioPosition?>()
            w.repo.serverAnswers["book-1"] = gate
            w.rig.controller.load("book-1", null, null, true)
            w.rig.advance(1_200)
            w.recorder.calls.contains("settled:30.0") shouldBe true
            gate.complete(ServerAudioPosition(300.0, 11L, 20L))
            w.rig.settle()
            w.sync.lateServerPosition.value shouldBe LatePosition("book-1", 11L, 300.0)
            w.rig.controller.stop()
            w.rig.settle()
            w.sync.lateServerPosition.value shouldBe null
        }
    }

    describe("listeners") {
        // TC-85: a listened stretch with no resume answer. Ticks save locally and push at 1000 ms and 6500 ms only; pause
        // and stop force a push; the 10 s of listening is one report; and the first play marks the unread book reading.
        it("TC-85: the three listeners work together through the controller (I-SAVE-2, I-SAVE-3, I-SESS-3, I-SESS-4)") {
            val w = newWiring()
            w.rig.loadAndSettle()
            w.rig.engine.snapshot = w.rig.engine.snapshot.copy(positionSeconds = 100.0)
            w.rig.advance(10_000)
            w.rig.controller.pause()
            w.rig.settle()
            w.rig.controller.stop()
            w.rig.settle()

            val pushes = w.repo.saves.map { it.second }
            pushes shouldBe List(20) { index -> index == 1 || index == 12 } + listOf(true, true)
            w.repo.saves.all { it.first.bookUuid == "book-1" && it.first.positionSeconds == 100.0 } shouldBe true

            w.repo.reports.size shouldBe 1
            w.repo.reports.single().progressUnits shouldBe 10L
            w.readStatus.setStatusCalls shouldBe listOf("book-1" to ReadStatus.Reading)
        }

        // TC-86: switching books saves and queues the book left before the next book's resume read starts.
        it("TC-86: switching saves the book left before resolving the next (I-SAVE-3, I-SESS-3)") {
            val order = mutableListOf<String>()
            val w = newWiring(order)
            w.rig.loadAndSettle()
            w.rig.engine.snapshot = w.rig.engine.snapshot.copy(positionSeconds = 100.0)
            w.rig.advance(6_000)
            w.rig.controller.load("book-2", null, null, true)
            w.rig.settle()

            val leaving = order.takeWhile { it != "localCandidate:book-2" }
            leaving.count { it == "save:book-1:true" } shouldBe 2
            leaving.count { it == "queueSession:book-1" } shouldBe 1
            order.contains("localCandidate:book-2") shouldBe true
            w.repo.reports.single().progressUnits shouldBe 6L
        }
    }
})

/** A completed deferred answer for a server read. */
private fun answered(answer: ServerAudioPosition?): CompletableDeferred<ServerAudioPosition?> =
    CompletableDeferred<ServerAudioPosition?>().apply { complete(answer) }

/**
 * One app-like graph for a spec row: the resume resolver, the position sync, the session tracker and the read-status
 * marker around one controller, on one virtual clock. [close] cancels the rig and the application scope.
 */
private class Wiring(scheduler: TestCoroutineScheduler, order: MutableList<String>?) {
    val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
    val time = TimeSource { scheduler.currentTime }
    val repo = FakeListeningProgressRepository(order)
    private var minted = 0
    lateinit var rig: ControllerRig
    val sync = DefaultListeningPositionSync(repo, dagger.Lazy { rig.controller }, time, scope)
    val tracker = ListeningSessionTracker(repo, time, ClientIdSource { "c-${++minted}" }, FakeDeviceTimeZone(), scope)
    val readStatus = FakeReadStatusRepository()
    val marker = ReadStatusMarker(readStatus, scope)
    val recorder = CallRecorder()

    init {
        val listeners: Set<PlaybackListener> = linkedSetOf(sync, tracker, marker, recorder)
        rig = ControllerRig(
            scheduler,
            resolver = DefaultResumeStartResolver(repo, sync, scope),
            listeners = listeners,
            order = order,
        )
    }

    fun close() {
        rig.close()
        scope.cancel()
    }
}
