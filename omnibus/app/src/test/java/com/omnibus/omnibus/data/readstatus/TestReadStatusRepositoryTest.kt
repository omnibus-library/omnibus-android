package com.omnibus.omnibus.data.readstatus

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher

class TestReadStatusRepositoryTest : DescribeSpec({

    val reading = ReadStatusRecord(ReadStatus.Reading, updatedAtMillis = 100L, finishedAtMillis = null)
    val finished = ReadStatusRecord(ReadStatus.Finished, updatedAtMillis = 200L, finishedAtMillis = 150L)

    describe("writes") {

        it("TC-34: a scripted failure applies the optimistic value, rolls back, and is recorded") {
            val fake = TestReadStatusRepository(now = { 1_000L })
            fake.seed("book", ReadStatusState.Known(reading))
            fake.writeResults.addLast(ReadStatusWriteResult.Failed(ReadStatusError.Unreachable))
            val seen = mutableListOf<ReadStatusState>()
            val scope = CoroutineScope(UnconfinedTestDispatcher())
            scope.launch { fake.getReadStatusFlow("book").collect { seen += it } }

            fake.setStatus("book", ReadStatus.Finished) shouldBe ReadStatusWriteResult.Failed(ReadStatusError.Unreachable)

            fake.setStatusCalls shouldBe listOf("book" to ReadStatus.Finished)
            seen shouldContain ReadStatusState.Known(ReadStatusRecord(ReadStatus.Finished, 1_000L, 1_000L))
            seen.last() shouldBe ReadStatusState.Known(reading)
            scope.cancel()
        }

        it("TC-35: choosing the current status returns Unchanged and records no call") {
            val fake = TestReadStatusRepository()
            fake.seed("book", ReadStatusState.Known(reading))

            fake.setStatus("book", ReadStatus.Reading) shouldBe ReadStatusWriteResult.Unchanged

            fake.setStatusCalls.shouldBeEmpty()
        }

        it("TC-36: matches the production rules for finished_at, null reads, refreshes, held writes and per-uuid flows") {
            val fake = TestReadStatusRepository(now = { 5_000L })

            // Finished -> Finished keeps finished_at (Unchanged); Finished -> Reading clears it.
            fake.seed("book", ReadStatusState.Known(finished))
            fake.setStatus("book", ReadStatus.Finished) shouldBe ReadStatusWriteResult.Unchanged
            fake.setStatus("book", ReadStatus.Reading) shouldBe
                ReadStatusWriteResult.Saved(ReadStatusRecord(ReadStatus.Reading, 5_000L, null))

            // Moving into Finished takes now.
            fake.setStatus("book", ReadStatus.Finished) shouldBe
                ReadStatusWriteResult.Saved(ReadStatusRecord(ReadStatus.Finished, 5_000L, 5_000L))

            // An empty GET answers the server's null: Known Unread.
            fake.refresh("other")
            fake.getReadStatusFlow("other").first() shouldBe
                ReadStatusState.Known(ReadStatusRecord(ReadStatus.Unread, null, null))
            fake.refreshCount shouldBe 1

            // A write held in flight shows its optimistic value before it finishes.
            val gate = CompletableDeferred<Unit>()
            fake.writeGates.addLast(gate)
            val scope = CoroutineScope(Dispatchers.Unconfined)
            val held = scope.async { fake.setStatus("book", ReadStatus.Reading) }
            fake.getReadStatusFlow("book").first() shouldBe
                ReadStatusState.Known(ReadStatusRecord(ReadStatus.Reading, 5_000L, null))
            gate.complete(Unit)
            held.await()

            // One flow per uuid.
            fake.getReadStatusFlow("book") shouldBeSameInstanceAs fake.getReadStatusFlow("book")
            scope.cancel()
        }

        it("TC-37: superseded writes resolve the way production does, with two gates") {
            val scope = CoroutineScope(Dispatchers.Unconfined)

            // A succeeds after B was issued: B's optimistic value stays, and B's failure rolls back to A's record.
            val fake = TestReadStatusRepository(now = { 9_000L })
            fake.seed("book", ReadStatusState.Known(ReadStatusRecord(ReadStatus.Unread, 1L, null)))
            val gateA = CompletableDeferred<Unit>()
            val gateB = CompletableDeferred<Unit>()
            fake.writeGates.addLast(gateA)
            fake.writeGates.addLast(gateB)
            fake.writeResults.addLast(ReadStatusWriteResult.Saved(reading))
            fake.writeResults.addLast(ReadStatusWriteResult.Failed(ReadStatusError.Http(500)))
            val a = scope.async { fake.setStatus("book", ReadStatus.Reading) }
            val b = scope.async { fake.setStatus("book", ReadStatus.Finished) }
            fake.getReadStatusFlow("book").first() shouldBe
                ReadStatusState.Known(ReadStatusRecord(ReadStatus.Finished, 9_000L, 9_000L))
            gateA.complete(Unit)
            a.await() shouldBe ReadStatusWriteResult.Saved(reading)
            fake.getReadStatusFlow("book").first() shouldBe ReadStatusState.Known(ReadStatusRecord(ReadStatus.Finished, 9_000L, 9_000L))
            gateB.complete(Unit)
            b.await() shouldBe ReadStatusWriteResult.Failed(ReadStatusError.Http(500))
            fake.getReadStatusFlow("book").first() shouldBe ReadStatusState.Known(reading)

            // A fails after B was issued, then B fails: the state returns to what it was before A.
            val other = TestReadStatusRepository(now = { 9_000L })
            other.seed("book", ReadStatusState.Known(finished))
            val gateC = CompletableDeferred<Unit>()
            val gateD = CompletableDeferred<Unit>()
            other.writeGates.addLast(gateC)
            other.writeGates.addLast(gateD)
            other.writeResults.addLast(ReadStatusWriteResult.Failed(ReadStatusError.Http(500)))
            other.writeResults.addLast(ReadStatusWriteResult.Failed(ReadStatusError.Unreachable))
            val c = scope.async { other.setStatus("book", ReadStatus.Reading) }
            val d = scope.async { other.setStatus("book", ReadStatus.Unread) }
            gateC.complete(Unit)
            c.await() shouldBe ReadStatusWriteResult.Failed(ReadStatusError.Http(500))
            other.getReadStatusFlow("book").first() shouldBe
                ReadStatusState.Known(ReadStatusRecord(ReadStatus.Unread, 9_000L, null))
            gateD.complete(Unit)
            d.await() shouldBe ReadStatusWriteResult.Failed(ReadStatusError.Unreachable)
            other.getReadStatusFlow("book").first() shouldBe ReadStatusState.Known(finished)
            scope.cancel()
        }
    }
})
