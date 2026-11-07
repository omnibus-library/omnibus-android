package com.omnibus.omnibus.data.audiobook.position

import com.omnibus.omnibus.data.audiobook.AudiobookManifestError
import com.omnibus.omnibus.data.audiobook.AudiobookManifestResult
import com.omnibus.omnibus.data.audiobook.playback.CallRecorder
import com.omnibus.omnibus.data.audiobook.playback.ControllerRig
import com.omnibus.omnibus.data.readstatus.TestReadStatusRepository
import com.omnibus.omnibus.di.ClientIdSource
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler

/**
 * One app-like graph: the resume resolver, the position sync, the session tracker and the read-status marker around a
 * real [com.omnibus.omnibus.data.audiobook.playback.DefaultAudiobookController], on one virtual clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private class Wiring(order: MutableList<String>? = null) : AutoCloseable {
    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
    private val time = TimeSource { scheduler.currentTime }
    private var minted = 0
    val repo = TestListeningProgressRepository(order)
    val sync = DefaultListeningPositionSync(repo, dagger.Lazy { rig.controller }, time, scope)
    val recorder = CallRecorder()
    private val tracker = ListeningSessionTracker(repo, time, ClientIdSource { "c-${++minted}" }, TestDeviceTimeZone(), scope)
    private val marker = ReadStatusMarker(TestReadStatusRepository(), scope)
    val rig: ControllerRig = ControllerRig(
        scheduler,
        resolver = DefaultResumeStartResolver(repo, sync, scope),
        listeners = linkedSetOf(sync, tracker, marker, recorder),
        order = order,
    )

    override fun close() {
        rig.close()
        scope.cancel()
    }
}

/** The position feature through a real controller (C5, C4, C8): what only the wired parts together can show. */
class ListeningPositionWiringTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("resuming a load") {

        context("when the server answers") {
            // TC-80
            it("TC-80: seeks to its position before auto-start (I-RES-3)") {
                Wiring().use { w ->
                    w.repo.serverAnswers["book-1"] = CompletableDeferred(ServerAudioPosition(120.0, 11L, 20L))

                    w.rig.loadAndSettle()

                    w.rig.engine.calls shouldContain "seekTo(120.0)"
                }
            }
        }

        context("when the server stays silent") {
            // TC-81
            it("TC-81: settles at the local position 1200 ms from the load's start (I-RES-2)") {
                Wiring().use { w ->
                    w.repo.locals["book-1"] = randomAudioPosition().copy(positionSeconds = 90.0, bookFileId = 11L)
                    w.repo.serverAnswers["book-1"] = CompletableDeferred()
                    w.rig.stageBook()
                    w.rig.controller.load("book-1", null, null, true)

                    w.rig.advance(1_199)
                    w.recorder.calls.none { it.startsWith("settled") } shouldBe true

                    w.rig.advance(1)
                    w.recorder.calls shouldContain "settled:90.0"
                }
            }

            context("when the listener pauses during the load") {
                // TC-82: a pause is not an abandonment
                it("TC-82: keeps the server read going (CC1)") {
                    Wiring().use { w ->
                        w.repo.serverAnswers["book-1"] = CompletableDeferred()
                        w.rig.stageBook()
                        w.rig.controller.load("book-1", null, null, true)
                        w.rig.settle()

                        w.rig.controller.pause()
                        w.rig.advance(1_200)

                        w.repo.serverCancellations shouldBe 0
                    }
                }
            }
        }

        // TC-83
        it("TC-83: cancels the server read on a stop, a new load or a failed manifest (CC1)") {
            checkAll(
                Exhaustive.of<(Wiring) -> Unit>(
                    { w ->
                        w.rig.repository.pending = CompletableDeferred()
                        w.rig.stageBook()
                        w.rig.controller.load("book-1", null, null, true)
                        w.rig.settle()
                        w.rig.controller.stop()
                    },
                    { w ->
                        w.rig.repository.pending = CompletableDeferred()
                        w.rig.stageBook()
                        w.rig.controller.load("book-1", null, null, true)
                        w.rig.settle()
                        w.rig.controller.load(randomString(), null, null, true)
                    },
                    { w ->
                        w.rig.repository.result = AudiobookManifestResult.Failure(AudiobookManifestError.Unreachable)
                        w.rig.stageBook()
                        w.rig.controller.load("book-1", null, null, true)
                    },
                ),
            ) { abandon ->
                Wiring().use { w ->
                    w.repo.serverAnswers["book-1"] = CompletableDeferred()

                    abandon(w)
                    w.rig.settle()

                    w.repo.serverCancellations shouldBe 1
                }
            }
        }
    }

    describe("a late answer") {

        // TC-84
        it("TC-84: reaches the late position (I-RES-6)") {
            Wiring().use { w ->
                val gate = CompletableDeferred<ServerAudioPosition?>()
                w.repo.locals["book-1"] = randomAudioPosition().copy(positionSeconds = 30.0, bookFileId = 11L, clientUpdatedAt = 10L)
                w.repo.serverAnswers["book-1"] = gate
                w.rig.stageBook()
                w.rig.controller.load("book-1", null, null, true)
                w.rig.advance(1_200)

                gate.complete(ServerAudioPosition(300.0, 11L, 20L))
                w.rig.settle()

                w.sync.lateServerPosition.value shouldBe LatePosition("book-1", 11L, 300.0)
            }
        }
    }

    describe("switching books") {

        // TC-86
        it("TC-86: stores the book left before reading the next (I-SAVE-3, I-SESS-3)") {
            val order = mutableListOf<String>()
            Wiring(order).use { w ->
                w.rig.loadAndSettle()
                w.rig.engine.snapshot = w.rig.engine.snapshot.copy(positionSeconds = 100.0)
                w.rig.advance(6_000)

                w.rig.controller.load("book-2", null, null, true)
                w.rig.settle()

                val leaving = order.takeWhile { it != "localCandidate:book-2" }
                leaving.count { it == "queueSession:book-1" } shouldBe 1
                leaving.last { it.startsWith("save") } shouldBe "save:book-1:true"
            }
        }
    }
})
