package com.omnibus.omnibus.feature.reader.ui.curl

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

/** Takes every queued direction, in order. */
private fun TurnQueue.drain(): List<Int> = generateSequence { next() }.toList()

class TurnQueueTest : DescribeSpec({

    describe("queueing turns") {
        it("TC-316: holds at most two turns ahead") {
            checkAll(Exhaustive.of(1, -1)) { dir ->
                val queue = TurnQueue().apply { repeat(5) { offer(dir) } }

                queue.drain() shouldBe listOf(dir, dir)
            }
        }

        it("TC-316: replaces the queue with one turn in the opposite direction") {
            checkAll(Exhaustive.of(1, -1)) { dir ->
                val queue = TurnQueue().apply {
                    repeat(5) { offer(dir) }
                    offer(-dir)
                }

                queue.drain() shouldBe listOf(-dir)
            }
        }

        it("TC-316: has nothing to take when no turn is queued") {
            TurnQueue().next().shouldBeNull()
        }

        it("TC-316: has nothing to take after clearing") {
            val queue = TurnQueue().apply {
                offer(1)
                clear()
            }

            queue.drain().shouldBeEmpty()
        }
    }
})
