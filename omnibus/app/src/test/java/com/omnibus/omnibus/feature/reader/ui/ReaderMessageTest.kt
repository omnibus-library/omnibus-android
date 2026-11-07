package com.omnibus.omnibus.feature.reader.ui

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class ReaderMessageTest : DescribeSpec({

    describe("parsing a posted envelope") {
        it("TC-239: keeps a JSON-in-a-string payload as the string the glue sent") {
            val relocate = """{"type":"relocate","payload":"{\"cfi\":\"epubcfi(/6/2)\",\"echo\":true}"}"""

            ReaderMessage.parse(relocate) shouldBe ReaderMessage("relocate", """{"cfi":"epubcfi(/6/2)","echo":true}""")
        }

        it("TC-240: treats a null or missing payload as none") {
            checkAll(Exhaustive.of("""{"type":"hostReady","payload":null}""", """{"type":"hostReady"}""")) { envelope ->
                ReaderMessage.parse(envelope) shouldBe ReaderMessage("hostReady", null)
            }
        }

        it("TC-241: rejects anything that isn't an envelope with a string type") {
            checkAll(Exhaustive.of("", "not json", "[]", "\"status\"", """{"payload":"x"}""", """{"type":3}""", """{"type":null}""")) { text ->
                ReaderMessage.parse(text) shouldBe null
            }
        }
    }
})
