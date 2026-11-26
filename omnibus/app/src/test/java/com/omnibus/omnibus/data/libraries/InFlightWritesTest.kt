package com.omnibus.omnibus.data.libraries

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class InFlightWritesTest : DescribeSpec({

    describe("tracking a write") {

        it("#369 TC-7: reports pending while the write is in flight") {
            runTest {
                val writes = InFlightWrites()
                writes.track(CompletableDeferred<String>()) { true }

                writes.hasPending().shouldBeTrue()
            }
        }

        it("#369 TC-7: reports nothing pending once the write has succeeded") {
            runTest {
                val writes = InFlightWrites()
                val write = CompletableDeferred<String>()
                writes.track(write) { it == "ok" }

                write.complete("ok")

                writes.hasPending().shouldBeFalse()
            }
        }

        it("#369 TC-7: reports nothing pending once the write has failed") {
            runTest {
                val writes = InFlightWrites()
                val write = CompletableDeferred<String>()
                writes.track(write) { it == "ok" }

                write.complete("rejected")

                writes.hasPending().shouldBeFalse()
            }
        }

        it("#369 TC-7: reports nothing pending once the write has thrown") {
            runTest {
                val writes = InFlightWrites()
                val write = CompletableDeferred<String>()
                writes.track(write) { true }

                write.completeExceptionally(IllegalStateException("disk full"))

                writes.hasPending().shouldBeFalse()
            }
        }

        it("#369 TC-7: returns the write it was given") {
            runTest {
                val write = CompletableDeferred<String>()

                InFlightWrites().track(write) { true } shouldBe write
            }
        }
    }

    describe("waiting for the writes in flight") {

        it("#369 TC-7: returns true at once when nothing is in flight") {
            runTest {
                InFlightWrites().awaitAll().shouldBeTrue()
            }
        }

        it("#369 TC-7: waits until the write in flight has finished") {
            runTest {
                val writes = InFlightWrites()
                val write = CompletableDeferred<String>()
                writes.track(write) { true }
                val waiting = async { writes.awaitAll() }
                runCurrent()

                waiting.isCompleted.shouldBeFalse()
                write.complete("ok")
                runCurrent()

                waiting.isCompleted.shouldBeTrue()
            }
        }

        it("#369 TC-7: returns true when every write succeeded") {
            runTest {
                val writes = InFlightWrites()
                writes.track(CompletableDeferred("ok")) { it == "ok" }
                writes.track(CompletableDeferred("ok")) { it == "ok" }

                writes.awaitAll().shouldBeTrue()
            }
        }

        it("#369 TC-7: returns false when a write's result is not a success") {
            runTest {
                val writes = InFlightWrites()
                writes.track(CompletableDeferred("ok")) { it == "ok" }
                writes.track(CompletableDeferred("rejected")) { it == "ok" }

                writes.awaitAll().shouldBeFalse()
            }
        }

        it("#369 TC-7: returns false when a write throws") {
            runTest {
                val writes = InFlightWrites()
                writes.track(CompletableDeferred<String>().apply { completeExceptionally(IllegalStateException()) }) { true }

                writes.awaitAll().shouldBeFalse()
            }
        }
    }

    describe("the tracking block form") {

        it("#369 TC-7: reports pending while the block runs") {
            runTest {
                val writes = InFlightWrites()
                val gate = CompletableDeferred<String>()
                val running = async { writes.tracking<String>({ it == "ok" }) { gate.await() } }
                runCurrent()

                writes.hasPending().shouldBeTrue()
                gate.complete("ok")
                running.await()
            }
        }

        it("#369 TC-7: reports nothing pending once the block has returned") {
            runTest {
                val writes = InFlightWrites()

                writes.tracking<String>({ it == "ok" }) { "ok" }

                writes.hasPending().shouldBeFalse()
            }
        }

        it("#369 TC-7: reports nothing pending once the block has thrown") {
            runTest {
                val writes = InFlightWrites()

                runCatching { writes.tracking<String>({ true }) { error("disk full") } }

                writes.hasPending().shouldBeFalse()
            }
        }

        it("#369 TC-7: makes awaitAll false when the block's result is not a success") {
            runTest {
                val writes = InFlightWrites()

                writes.tracking<String>({ it == "ok" }) { "rejected" }

                writes.awaitAll().shouldBeFalse()
            }
        }
    }
})
