package com.omnibus.omnibus.feature.reader.ui

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ReaderMessageTest : DescribeSpec({

    describe("ReaderMessage.parse") {

        it("TC-238: reads the type and a string payload") {
            ReaderMessage.parse("""{"type":"status","payload":"ready"}""") shouldBe
                ReaderMessage("status", "ready")
        }

        it("TC-239: keeps a JSON-in-a-string payload as the string the glue sent") {
            val relocate = """{"type":"relocate","payload":"{\"cfi\":\"epubcfi(/6/2)\",\"echo\":true}"}"""

            ReaderMessage.parse(relocate) shouldBe
                ReaderMessage("relocate", """{"cfi":"epubcfi(/6/2)","echo":true}""")
        }

        it("TC-240: treats a null or missing payload as none and a number as its text") {
            ReaderMessage.parse("""{"type":"hostReady","payload":null}""") shouldBe ReaderMessage("hostReady", null)
            ReaderMessage.parse("""{"type":"hostReady"}""") shouldBe ReaderMessage("hostReady", null)
            ReaderMessage.parse("""{"type":"turnRequest","payload":-1}""") shouldBe ReaderMessage("turnRequest", "-1")
        }

        it("TC-241: rejects anything that isn't an envelope with a string type") {
            listOf("", "not json", "[]", "\"status\"", """{"payload":"x"}""", """{"type":3}""", """{"type":null}""")
                .forEach { ReaderMessage.parse(it) shouldBe null }
        }
    }
})
