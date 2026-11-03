package com.omnibus.omnibus.data.series

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class FakeSeriesRepositoryTest : DescribeSpec({

    describe("FakeSeriesRepository") {

        it("#104 TC-19: it returns queued results and records calls") {
            val fake = FakeSeriesRepository()
            val success = SeriesResult.Success(listOf(SeriesMember("a", "A", "1", null, null, false)))
            fake.results.addLast(success)
            fake.results.addLast(SeriesResult.NoServer)
            fake.results.addLast(SeriesResult.Failure(SeriesError.NotFound))

            fake.getSeries(4) shouldBe success
            fake.getSeries(4) shouldBe SeriesResult.NoServer
            fake.getSeries(9) shouldBe SeriesResult.Failure(SeriesError.NotFound)

            fake.calls shouldContainExactly listOf(4L, 4L, 9L)
        }

        it("#104 TC-20: an empty queue gives the default NoServer, still records the call, and does not throw") {
            val fake = FakeSeriesRepository()

            fake.getSeries(7) shouldBe SeriesResult.NoServer

            fake.calls shouldContainExactly listOf(7L)
        }

        it("#104 TC-21: a gate holds the call until completed, and the result is the one taken before waiting") {
            val fake = FakeSeriesRepository()
            val queued = SeriesResult.Failure(SeriesError.Unreachable)
            val gate = CompletableDeferred<Unit>()
            fake.results.addLast(queued)
            fake.gates.addLast(gate)

            coroutineScope {
                val call = async(start = CoroutineStart.UNDISPATCHED) { fake.getSeries(4) }
                call.isCompleted shouldBe false
                gate.complete(Unit)
                call.await() shouldBe queued
            }

            // A second call, gated and then cancelled, ends cleanly without a result.
            fake.results.addLast(SeriesResult.NoServer)
            fake.gates.addLast(CompletableDeferred())
            coroutineScope {
                val call = async(start = CoroutineStart.UNDISPATCHED) { fake.getSeries(5) }
                call.cancel()
                shouldThrow<CancellationException> { call.await() }
            }
        }
    }
})
