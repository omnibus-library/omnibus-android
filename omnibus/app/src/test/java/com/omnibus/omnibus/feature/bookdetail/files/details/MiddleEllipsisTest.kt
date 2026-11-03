package com.omnibus.omnibus.feature.bookdetail.files.details

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class MiddleEllipsisTest : DescribeSpec({

    describe("cutting a value in the middle") {

        it("#114 AC3: a value that fits is left whole") {
            fitMiddle("short.epub") { it.length <= 20 } shouldBe "short.epub"
        }

        it("#114 AC3: a long value keeps its start and its end around an ellipsis") {
            val cut = fitMiddle("The_Complete_Sherlock_Holmes_Illustrated_Edition.epub") { it.length <= 20 }

            cut shouldBe "The_Comple…tion.epub"
            cut.length shouldBe 20
        }

        it("#114 AC3: it keeps as much as fits, no less") {
            val text = "abcdefghijklmnopqrstuvwxyz"
            (6..text.length).forEach { limit ->
                val cut = fitMiddle(text) { it.length <= limit }
                cut.length shouldBe minOf(limit, text.length)
            }
        }

        it("#114 AC3: an even keep splits equally between the start and the end") {
            val cut = fitMiddle("0123456789".repeat(10)) { it.length <= 11 }

            cut.substringBefore("…").length - cut.substringAfter("…").length shouldBe 0
        }

        it("#114 AC3: when not even one character fits, only the ellipsis is left") {
            fitMiddle("abcdef") { it.length <= 1 } shouldBe "…"
        }

        it("#114 AC3: a surrogate pair is never split") {
            val text = "😀".repeat(20)

            (1..text.length).forEach { keep ->
                val cut = cutMiddle(text, keep)
                cut.forEachIndexed { i, c ->
                    if (c.isHighSurrogate()) cut[i + 1].isLowSurrogate() shouldBe true
                    if (c.isLowSurrogate()) cut[i - 1].isHighSurrogate() shouldBe true
                }
            }
        }
    }
})
