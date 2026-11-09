package com.omnibus.omnibus.feature.reader.ui

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive

class ReaderPageCallsTest : DescribeSpec({

    /** The id the call that sent [script] is tagged with. */
    fun idIn(script: String): Int = Regex("""id:(\d+)""").find(script)!!.groupValues[1].toInt()

    describe("awaiting a page call") {

        it("TC-306: returns the value of the reply with the call's id") {
            runTest {
                val calls = ReaderPageCalls()
                val scripts = mutableListOf<String>()
                val reply = async { calls.call("1+1", evaluate = { scripts += it }) }
                runCurrent()

                calls.resolve(idIn(scripts.single()), JsonPrimitive(2))

                reply.await() shouldBe JsonPrimitive(2)
            }
        }

        it("TC-306: returns null when no reply comes within 3 seconds") {
            runTest {
                val calls = ReaderPageCalls()
                val reply = async { calls.call("1+1", evaluate = {}) }
                runCurrent()

                advanceTimeBy(ReaderPageCalls.TIMEOUT_MS - 1)
                runCurrent()
                reply.isCompleted shouldBe false

                advanceTimeBy(1)
                runCurrent()
                reply.await().shouldBeNull()
            }
        }

        it("TC-306: ignores a reply that arrives after the timeout") {
            runTest {
                val calls = ReaderPageCalls()
                val scripts = mutableListOf<String>()
                val reply = async { calls.call("1+1", evaluate = { scripts += it }) }
                runCurrent()
                advanceTimeBy(ReaderPageCalls.TIMEOUT_MS + 1)
                runCurrent()

                calls.resolve(idIn(scripts.single()), JsonPrimitive(2))

                reply.await().shouldBeNull()
            }
        }

        it("TC-306: returns null at once when there is no page to run the call") {
            runTest {
                ReaderPageCalls().call("1+1", evaluate = null).shouldBeNull()

                currentTime shouldBe 0L
            }
        }
    }
})
