package com.omnibus.omnibus.feature.bookdetail.files.details

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection

class MiddleEllipsisTest : DescribeSpec({

    describe("cutting a value in the middle") {

        context("when the value fits") {
            it("#114 AC3: leaves it whole") {
                fitMiddle("short.epub") { it.length <= 20 } shouldBe "short.epub"
            }
        }

        context("when the value is too long") {
            it("#114 AC3: keeps its start and its end around an ellipsis") {
                fitMiddle("The_Complete_Sherlock_Holmes_Illustrated_Edition.epub") { it.length <= 20 } shouldBe
                    "The_Comple…tion.epub"
            }

            it("#114 AC3: keeps as much as fits at every limit") {
                val text = "abcdefghijklmnopqrstuvwxyz"

                checkAll(Exhaustive.collection((6..text.length).toList())) { limit ->
                    fitMiddle(text) { it.length <= limit }.length shouldBe limit
                }
            }
        }

        context("when not even one character fits") {
            it("#114 AC3: leaves only the ellipsis") {
                fitMiddle("abcdef") { it.length <= 1 } shouldBe "…"
            }
        }

        context("when the value is made of surrogate pairs") {
            it("#114 AC3: never splits one, at any length kept") {
                val text = "😀".repeat(20)

                checkAll(Exhaustive.collection((1..text.length).toList())) { keep ->
                    val cut = cutMiddle(text, keep)

                    cut.filterNot { it == '…' }.chunked(2).forEach { it shouldBe "😀" }
                }
            }
        }
    }
})
