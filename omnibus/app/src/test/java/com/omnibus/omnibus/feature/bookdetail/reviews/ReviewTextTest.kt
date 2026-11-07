package com.omnibus.omnibus.feature.bookdetail.reviews

import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class ReviewTextTest : DescribeSpec({

    fun sut(md: String?, finished: Boolean = randomBoolean(), inlineSpoiler: String = randomString()) =
        reviewPlainText(md, finished, inlineSpoiler)

    describe("a review as plain text") {
        it("TC-27: turns each block marker into a plain line") {
            checkAll(
                Exhaustive.of(
                    "## Title" to "Title",
                    "> q" to "q",
                    "[t](u)" to "t",
                    "- a" to "• a",
                    "* b" to "• b",
                    "+ c" to "• c",
                    "1. d" to "1. d",
                ),
            ) { (md, text) ->
                sut(md) shouldBe text
            }
        }

        it("TC-28: removes each emphasis mark only where it wraps a word") {
            checkAll(
                Exhaustive.of(
                    "**b**" to "b",
                    "__b__" to "b",
                    "~~s~~" to "s",
                    "`c`" to "c",
                    "*i*" to "i",
                    "_i_" to "i",
                    "2 * 3" to "2 * 3",
                    "snake_case_name" to "snake_case_name",
                ),
            ) { (md, text) ->
                sut(md) shouldBe text
            }
        }

        context("when the review has an inline spoiler") {
            context("when the book is not finished") {
                it("TC-29: reads the spoiler word") {
                    sut("a ||secret|| b", finished = false, inlineSpoiler = "(spoiler)") shouldBe "a (spoiler) b"
                }
            }

            context("when the book is finished") {
                it("TC-29: reads the secret") {
                    sut("a ||secret|| b", finished = true) shouldBe "a secret b"
                }
            }
        }

        it("TC-30: normalises line endings, blank runs, edges") {
            sut("  a\r\nb\r\n\r\n\r\n\r\nc  ") shouldBe "a\nb\n\nc"
        }

        it("TC-30: is missing for each input with nothing readable") {
            checkAll(Exhaustive.of(null, "", "   ", "![a](u)")) { md ->
                sut(md) shouldBe null
            }
        }
    }
})
