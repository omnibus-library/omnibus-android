package com.omnibus.omnibus.data.requests

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class RequestFormatsTest : DescribeSpec({

    describe("whether the library holds a kind") {
        it("reads each format as an ebook, an audiobook or neither, regardless of case") {
            checkAll(
                Exhaustive.of(
                    Triple("EPUB", true, false),
                    Triple("epub", true, false),
                    Triple("M4B", false, true),
                    Triple("m4a", false, true),
                    Triple("Mp3", false, true),
                    Triple("PRINT", false, false),
                    Triple("print", false, false),
                    Triple("TXT", true, false),
                ),
            ) { (format, ebook, audiobook) ->
                holds(RequestKind.Ebook, listOf(format)) shouldBe ebook
                holds(RequestKind.Audiobook, listOf(format)) shouldBe audiobook
            }
        }

        it("holds both kinds when the formats hold both") {
            val formats = listOf("PRINT", "EPUB", "M4B")

            listOf(holds(RequestKind.Ebook, formats), holds(RequestKind.Audiobook, formats)) shouldBe listOf(true, true)
        }

        it("holds neither kind when there are no formats") {
            listOf(holds(RequestKind.Ebook, emptyList()), holds(RequestKind.Audiobook, emptyList())) shouldBe
                listOf(false, false)
        }
    }
})
