package com.omnibus.omnibus.feature.bookdetail.finish

import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

/** Wording for the tests, so the joins and the number phrases are checked without resources. */
private val testWording = FinishWording(
    title = { "You finished $it" },
    days = { "$it days" },
    sessions = { "$it sessions" },
    highlights = { "$it highlights" },
    reviewTitle = "Leave a review",
    reviewDetail = "Tell your household what you thought",
    reviewAction = "Write a review",
    nextTitle = { "Up next: $it" },
    nextDetail = { index, series -> "Book $index of $series" },
    nextDetailUnnumbered = { "In $it" },
    nextAction = "Open the book",
)

class FinishTextTest : DescribeSpec({

    describe("the totals line") {

        context("when there are several totals") {
            it("TC-15: joins them with a middle dot") {
                listOf(FinishTotal.Days(17), FinishTotal.Sessions(13), FinishTotal.Highlights(4))
                    .totalsText(testWording) shouldBe "17 days · 13 sessions · 4 highlights"
            }
        }
    }

    describe("the next book's line") {

        context("when the book has a number in the series") {
            it("TC-16: names the number, trimmed") {
                val series = randomString()

                nextDetail(" 2 ", series, testWording) shouldBe "Book 2 of $series"
            }
        }

        context("when the book has no number") {
            it("TC-16: names the series alone, for a missing or blank number") {
                checkAll(Exhaustive.of(null, "  ")) { index ->
                    val series = randomString()

                    nextDetail(index, series, testWording) shouldBe "In $series"
                }
            }
        }
    }
})
