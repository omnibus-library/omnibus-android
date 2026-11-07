package com.omnibus.omnibus.data.audiobook.position

import com.omnibus.omnibus.data.audiobook.playback.AudiobookController
import com.omnibus.omnibus.data.audiobook.playback.TestAudiobookController
import com.omnibus.omnibus.data.audiobook.playback.PendingResume
import com.omnibus.omnibus.testing.TestTimeSource
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** One resolver with its repository and sync, on one scope that shares [scheduler]'s virtual clock. */
private class Harness(scheduler: TestCoroutineScheduler) {
    val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
    val repo = TestListeningProgressRepository()
    val sync = DefaultListeningPositionSync(
        repo,
        dagger.Lazy<AudiobookController> { TestAudiobookController() },
        TestTimeSource(),
        scope,
    )
    val resolver = DefaultResumeStartResolver(repo, sync, scope)
}

/** A row of the "settle picks the newer candidate" table. */
private data class ChoiceRow(
    val name: String,
    val local: AudioPosition?,
    val server: ServerAudioPosition?,
    val expected: Double?,
)

/** A local position of book-1 at [seconds], saved against [fileId] at [clock]. */
private fun local(seconds: Double, fileId: Long, clock: Long) = AudioPosition("book-1", seconds, fileId, clock)

/** Starts [PendingResume.settle] for file 6 and a 3000 s book on this scope. */
@OptIn(ExperimentalCoroutinesApi::class)
private fun CoroutineScope.settleAsync(pending: PendingResume): Deferred<Double?> =
    async { pending.settle(6L, 3000.0) }

/**
 * The resume decision (C5): the local read at begin, the server read with its 1200 ms deadline, the choice of start, the
 * late offer and cancellation. All time is virtual, on one [TestCoroutineScheduler].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultResumeStartResolverTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }
    afterSpec { unmockAndroidLog() }

    lateinit var scheduler: TestCoroutineScheduler
    val harnesses = mutableListOf<Harness>()

    fun harness(): Harness = Harness(scheduler).also { harnesses += it }

    beforeEach {
        scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
    }

    afterEach {
        harnesses.forEach { it.scope.cancel() }
        harnesses.clear()
        Dispatchers.resetMain()
    }

    describe("begin") {
        // TC-35: begin returns at once and starts both reads (I-RES-2)
        it("TC-35: begin returns at once and starts both reads (I-RES-2)") {
            val h = harness()
            h.resolver.begin("book-1", null, null)
            h.repo.localCalls shouldBe 0
            h.repo.serverCalls shouldBe 0
            scheduler.runCurrent()
            h.repo.localCalls shouldBe 1
            h.repo.serverCalls shouldBe 1
            scheduler.currentTime shouldBe 0
        }

        // TC-36: an explicit position wins as given and skips the GET (I-RES-4)
        it("TC-36: an explicit position wins as given and skips the GET (I-RES-4)") {
            listOf(42.5, 0.5).forEach { explicit ->
                val h = harness()
                h.repo.serverAnswers["book-1"] = CompletableDeferred(ServerAudioPosition(300.0, 6, 20))
                val pending = h.resolver.begin("book-1", null, explicit)
                val result = h.scope.settleAsync(pending)
                scheduler.runCurrent()
                withClue("explicit $explicit") {
                    result.isCompleted shouldBe true
                    result.getCompleted() shouldBe explicit
                    scheduler.currentTime shouldBe 0
                    h.repo.serverCalls shouldBe 0
                }
            }
        }
    }

    describe("savedFileId") {
        // TC-37: local file id only, never waits for the server (I-RES-1)
        it("TC-37: local file id only, never waits for the server (I-RES-1)") {
            listOf<AudioPosition?>(local(100.0, 7, 10), null).forEach { saved ->
                val h = harness()
                if (saved != null) h.repo.locals["book-1"] = saved
                // A server read that never completes: savedFileId must not wait for it.
                h.repo.serverAnswers["book-1"] = CompletableDeferred()
                val pending = h.resolver.begin("book-1", null, null)
                val fileId = h.scope.async { pending.savedFileId() }
                scheduler.runCurrent()
                withClue("saved $saved") {
                    fileId.isCompleted shouldBe true
                    fileId.getCompleted() shouldBe saved?.bookFileId
                    scheduler.currentTime shouldBe 0
                }
            }
        }
    }

    describe("choice") {
        // TC-38: settle picks the newer candidate and applies the file and 1 s rules (I-RES-1, I-RES-3)
        it("TC-38: settle picks the newer candidate and applies the file and 1 s rules (I-RES-1, I-RES-3)") {
            listOf(
                ChoiceRow("local 100@10 file 6, server 200@20 file 6", local(100.0, 6, 10), ServerAudioPosition(200.0, 6, 20), 200.0),
                ChoiceRow("local 100@20, server 200@10", local(100.0, 6, 20), ServerAudioPosition(200.0, 6, 10), 100.0),
                ChoiceRow("local 100@10, server 200@10 (tie)", local(100.0, 6, 10), ServerAudioPosition(200.0, 6, 10), 100.0),
                ChoiceRow("server 200@20 file 5", null, ServerAudioPosition(200.0, 5, 20), null),
                ChoiceRow("server 200@20 file null", null, ServerAudioPosition(200.0, null, 20), 200.0),
                ChoiceRow("server 1.0@20 file 6", null, ServerAudioPosition(1.0, 6, 20), null),
                ChoiceRow("server 1.01@20 file 6", null, ServerAudioPosition(1.01, 6, 20), 1.01),
            ).forEach { row ->
                val h = harness()
                row.local?.let { h.repo.locals["book-1"] = it }
                row.server?.let { h.repo.serverAnswers["book-1"] = CompletableDeferred(it) }
                val pending = h.resolver.begin("book-1", null, null)
                val result = h.scope.settleAsync(pending)
                scheduler.runCurrent()
                withClue(row.name) {
                    result.isCompleted shouldBe true
                    result.getCompleted() shouldBe row.expected
                }
            }
        }
    }

    describe("deadline") {
        // TC-39: a completed no-answer returns at once (I-RES-2)
        it("TC-39: a completed no-answer returns at once (I-RES-2)") {
            val h = harness()
            val pending = h.resolver.begin("book-1", null, null)
            val result = h.scope.settleAsync(pending)
            scheduler.runCurrent()
            result.isCompleted shouldBe true
            result.getCompleted() shouldBe null
            scheduler.currentTime shouldBe 0
        }

        // TC-40: settle waits until 1200 ms and no longer (I-RES-2)
        it("TC-40: settle waits until 1200 ms and no longer (I-RES-2)") {
            // Row 1: the server answers at 1199 ms, just inside the deadline.
            run {
                val h = harness()
                h.repo.locals["book-1"] = local(100.0, 6, 10)
                val gate = CompletableDeferred<ServerAudioPosition?>()
                h.repo.serverAnswers["book-1"] = gate
                val pending = h.resolver.begin("book-1", null, null)
                val result = h.scope.settleAsync(pending)
                scheduler.runCurrent()
                scheduler.advanceTimeBy(1_199)
                scheduler.runCurrent()
                gate.complete(ServerAudioPosition(200.0, 6, 20))
                scheduler.runCurrent()
                withClue("answer at 1199 ms") {
                    result.isCompleted shouldBe true
                    result.getCompleted() shouldBe 200.0
                    scheduler.currentTime shouldBe 1_199
                }
            }
            // Row 2: the server never answers, so settle returns the local start at 1200 ms.
            run {
                val startedAt = scheduler.currentTime
                val h = harness()
                h.repo.locals["book-1"] = local(100.0, 6, 10)
                h.repo.serverAnswers["book-1"] = CompletableDeferred()
                val pending = h.resolver.begin("book-1", null, null)
                val result = h.scope.settleAsync(pending)
                scheduler.runCurrent()
                scheduler.advanceTimeBy(1_199)
                scheduler.runCurrent()
                withClue("still waiting at 1199 ms") {
                    result.isCompleted shouldBe false
                }
                scheduler.advanceTimeBy(1)
                scheduler.runCurrent()
                withClue("answered at 1200 ms") {
                    result.isCompleted shouldBe true
                    result.getCompleted() shouldBe 100.0
                    scheduler.currentTime - startedAt shouldBe 1_200
                }
            }
        }

        // TC-41: the deadline runs from begin, not from settle (I-RES-2)
        it("TC-41: the deadline runs from begin, not from settle (I-RES-2)") {
            // Row 1: settle called at 1000 ms waits until the 1200 ms deadline, 200 ms after the call.
            run {
                val h = harness()
                h.repo.locals["book-1"] = local(100.0, 6, 10)
                h.repo.serverAnswers["book-1"] = CompletableDeferred()
                val pending = h.resolver.begin("book-1", null, null)
                scheduler.advanceTimeBy(1_000)
                scheduler.runCurrent()
                val result = h.scope.settleAsync(pending)
                scheduler.runCurrent()
                scheduler.advanceTimeBy(199)
                scheduler.runCurrent()
                withClue("200 ms after the call, before the deadline") {
                    result.isCompleted shouldBe false
                }
                scheduler.advanceTimeBy(1)
                scheduler.runCurrent()
                withClue("at the deadline") {
                    result.isCompleted shouldBe true
                    result.getCompleted() shouldBe 100.0
                    scheduler.currentTime shouldBe 1_200
                }
            }
            // Row 2: settle called at 5000 ms, after the deadline, returns the local start with no wait.
            run {
                val startedAt = scheduler.currentTime
                val h = harness()
                h.repo.locals["book-1"] = local(100.0, 6, 10)
                h.repo.serverAnswers["book-1"] = CompletableDeferred()
                val pending = h.resolver.begin("book-1", null, null)
                scheduler.advanceTimeBy(5_000)
                scheduler.runCurrent()
                val result = h.scope.settleAsync(pending)
                scheduler.runCurrent()
                withClue("after the deadline") {
                    result.isCompleted shouldBe true
                    result.getCompleted() shouldBe 100.0
                    scheduler.currentTime - startedAt shouldBe 5_000
                }
            }
        }

        // TC-42: an answer already in hand is used past the deadline (I-RES-2)
        it("TC-42: an answer already in hand is used past the deadline (I-RES-2)") {
            val h = harness()
            h.repo.locals["book-1"] = local(100.0, 6, 10)
            val gate = CompletableDeferred<ServerAudioPosition?>()
            h.repo.serverAnswers["book-1"] = gate
            val pending = h.resolver.begin("book-1", null, null)
            scheduler.runCurrent()
            scheduler.advanceTimeBy(500)
            scheduler.runCurrent()
            gate.complete(ServerAudioPosition(200.0, 6, 20))
            scheduler.runCurrent()
            scheduler.advanceTimeBy(2_500)
            scheduler.runCurrent()
            val result = h.scope.settleAsync(pending)
            scheduler.runCurrent()
            result.isCompleted shouldBe true
            result.getCompleted() shouldBe 200.0
            scheduler.currentTime shouldBe 3_000
        }

        // TC-43: settle waits for the local read (I-RES-2)
        it("TC-43: settle waits for the local read (I-RES-2)") {
            val h = harness()
            val localGate = CompletableDeferred<Unit>()
            h.repo.localGate = localGate
            h.repo.locals["book-1"] = local(100.0, 6, 10)
            h.repo.serverAnswers["book-1"] = CompletableDeferred(ServerAudioPosition(200.0, 6, 20))
            val pending = h.resolver.begin("book-1", null, null)
            val result = h.scope.settleAsync(pending)
            scheduler.runCurrent()
            scheduler.advanceTimeBy(1_999)
            scheduler.runCurrent()
            withClue("at 1999 ms, local still reading") {
                result.isCompleted shouldBe false
            }
            scheduler.advanceTimeBy(1)
            localGate.complete(Unit)
            scheduler.runCurrent()
            result.isCompleted shouldBe true
            result.getCompleted() shouldBe 200.0
            scheduler.currentTime shouldBe 2_000
        }
    }

    describe("late answer") {
        // TC-44: a late answer ahead of the start is offered (I-RES-6)
        it("TC-44: a late answer ahead of the start is offered (I-RES-6)") {
            val h = harness()
            h.repo.locals["book-1"] = local(30.0, 6, 10)
            val gate = CompletableDeferred<ServerAudioPosition?>()
            h.repo.serverAnswers["book-1"] = gate
            val pending = h.resolver.begin("book-1", null, null)
            val result = h.scope.settleAsync(pending)
            scheduler.runCurrent()
            scheduler.advanceTimeBy(1_199)
            scheduler.runCurrent()
            scheduler.advanceTimeBy(1)
            scheduler.runCurrent()
            result.getCompleted() shouldBe 30.0
            gate.complete(ServerAudioPosition(50.0, 6, 20))
            scheduler.runCurrent()
            h.sync.lateServerPosition.value shouldBe LatePosition("book-1", 6, 50.0)
        }

        // TC-45: a late answer that fails a rule is not offered (I-RES-6)
        it("TC-45: a late answer that fails a rule is not offered (I-RES-6)") {
            listOf<Pair<String, ServerAudioPosition?>>(
                "31.0 at clock 20 (1 s ahead is not more than 1 s)" to ServerAudioPosition(31.0, 6, 20),
                "60 at clock 10 (not newer)" to ServerAudioPosition(60.0, 6, 10),
                "60 at clock 9 (older)" to ServerAudioPosition(60.0, 6, 9),
                "60 at clock 20 for file 7" to ServerAudioPosition(60.0, 7, 20),
                "no answer" to null,
            ).forEach { (name, late) ->
                val h = harness()
                h.repo.locals["book-1"] = local(30.0, 6, 10)
                val gate = CompletableDeferred<ServerAudioPosition?>()
                h.repo.serverAnswers["book-1"] = gate
                val pending = h.resolver.begin("book-1", null, null)
                val result = h.scope.settleAsync(pending)
                scheduler.runCurrent()
                scheduler.advanceTimeBy(1_200)
                scheduler.runCurrent()
                withClue("settled before the late answer: $name") {
                    result.getCompleted() shouldBe 30.0
                }
                gate.complete(late)
                scheduler.runCurrent()
                withClue(name) {
                    h.sync.lateServerPosition.value shouldBe null
                }
            }
        }

        // TC-46: an answer that arrived in time never offers (I-RES-6)
        it("TC-46: an answer that arrived in time never offers (I-RES-6)") {
            val h = harness()
            val gate = CompletableDeferred<ServerAudioPosition?>()
            h.repo.serverAnswers["book-1"] = gate
            val pending = h.resolver.begin("book-1", null, null)
            val result = h.scope.settleAsync(pending)
            scheduler.runCurrent()
            scheduler.advanceTimeBy(100)
            gate.complete(ServerAudioPosition(500.0, 6, 20))
            scheduler.runCurrent()
            result.getCompleted() shouldBe 500.0
            scheduler.advanceTimeBy(10_000)
            scheduler.runCurrent()
            h.sync.lateServerPosition.value shouldBe null
        }

        // TC-47: a late answer for a replaced load is dropped (I-RES-6)
        it("TC-47: a late answer for a replaced load is dropped (I-RES-6)") {
            val h = harness()
            h.repo.locals["book-1"] = local(30.0, 6, 10)
            val gate = CompletableDeferred<ServerAudioPosition?>()
            h.repo.serverAnswers["book-1"] = gate
            val pending = h.resolver.begin("book-1", null, null)
            val result = h.scope.settleAsync(pending)
            scheduler.runCurrent()
            scheduler.advanceTimeBy(1_200)
            scheduler.runCurrent()
            result.getCompleted() shouldBe 30.0
            h.resolver.begin("book-2", null, null)
            scheduler.runCurrent()
            gate.complete(ServerAudioPosition(60.0, 6, 20))
            scheduler.runCurrent()
            h.sync.lateServerPosition.value shouldBe null
        }
    }

    describe("cancel") {
        // TC-48: cancel stops the GET and any late offer (CC1)
        it("TC-48: cancel stops the GET and any late offer (CC1)") {
            // Row 1: cancelled while settle waits on the deadline.
            run {
                val h = harness()
                h.repo.locals["book-1"] = local(30.0, 6, 10)
                val gate = CompletableDeferred<ServerAudioPosition?>()
                h.repo.serverAnswers["book-1"] = gate
                val pending = h.resolver.begin("book-1", null, null)
                val result = h.scope.settleAsync(pending)
                scheduler.runCurrent()
                result.cancel()
                pending.cancel()
                scheduler.runCurrent()
                gate.complete(ServerAudioPosition(60.0, 6, 20))
                scheduler.runCurrent()
                withClue("row 1: cancel while waiting") {
                    h.repo.serverCancellations shouldBe 1
                    h.sync.lateServerPosition.value shouldBe null
                }
            }
            // Row 2: cancelled after settle returned on the deadline, before the late answer.
            run {
                val h = harness()
                h.repo.locals["book-1"] = local(30.0, 6, 10)
                val gate = CompletableDeferred<ServerAudioPosition?>()
                h.repo.serverAnswers["book-1"] = gate
                val pending = h.resolver.begin("book-1", null, null)
                val result = h.scope.settleAsync(pending)
                scheduler.runCurrent()
                scheduler.advanceTimeBy(1_200)
                scheduler.runCurrent()
                result.getCompleted() shouldBe 30.0
                pending.cancel()
                scheduler.runCurrent()
                gate.complete(ServerAudioPosition(60.0, 6, 20))
                scheduler.runCurrent()
                withClue("row 2: cancel after the deadline") {
                    h.repo.serverCancellations shouldBe 1
                    h.sync.lateServerPosition.value shouldBe null
                }
            }
        }

        // TC-49: a new load after cancel resolves normally (CC1)
        it("TC-49: a new load after cancel resolves normally (CC1)") {
            val h = harness()
            val gate = CompletableDeferred<ServerAudioPosition?>()
            h.repo.serverAnswers["book-1"] = gate
            val first = h.resolver.begin("book-1", null, null)
            scheduler.runCurrent()
            first.cancel()
            scheduler.runCurrent()
            h.repo.serverCancellations shouldBe 1

            h.repo.serverAnswers["book-1"] = CompletableDeferred(ServerAudioPosition(200.0, 6, 20))
            val second = h.resolver.begin("book-1", null, null)
            val result = h.scope.settleAsync(second)
            scheduler.runCurrent()
            result.getCompleted() shouldBe 200.0
            h.repo.serverCalls shouldBe 2
        }

        // TC-50: settle is idempotent (CC1)
        it("TC-50: settle is idempotent (CC1)") {
            val h = harness()
            h.repo.locals["book-1"] = local(100.0, 6, 10)
            h.repo.serverAnswers["book-1"] = CompletableDeferred(ServerAudioPosition(200.0, 6, 20))
            val pending = h.resolver.begin("book-1", null, null)
            val first = h.scope.settleAsync(pending)
            scheduler.runCurrent()
            first.getCompleted() shouldBe 200.0

            // The answers change, but a second settle returns the first answer without a repository call.
            h.repo.serverAnswers["book-1"] = CompletableDeferred(ServerAudioPosition(999.0, 6, 99))
            h.repo.locals["book-1"] = local(5.0, 6, 99)
            val localCallsBefore = h.repo.localCalls
            val serverCallsBefore = h.repo.serverCalls
            val second = h.scope.settleAsync(pending)
            scheduler.runCurrent()
            second.getCompleted() shouldBe 200.0
            h.repo.localCalls shouldBe localCallsBefore
            h.repo.serverCalls shouldBe serverCallsBefore
        }
    }
})
