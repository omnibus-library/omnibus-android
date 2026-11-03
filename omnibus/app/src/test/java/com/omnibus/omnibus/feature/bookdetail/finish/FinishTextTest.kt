package com.omnibus.omnibus.feature.bookdetail.finish

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

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

        it("TC-15: the totals join with a middle dot, and an empty list is no line") {
            listOf(FinishTotal.Days(17), FinishTotal.Sessions(13), FinishTotal.Highlights(4))
                .totalsText(testWording) shouldBe "17 days · 13 sessions · 4 highlights"
            listOf(FinishTotal.Highlights(4)).totalsText(testWording) shouldBe "4 highlights"
            emptyList<FinishTotal>().totalsText(testWording) shouldBe null
        }
    }

    describe("the next book's line") {

        it("TC-16: the next book's line names its number in the series") {
            nextDetail("3", "Sherlock Holmes", testWording) shouldBe "Book 3 of Sherlock Holmes"
            nextDetail(" 2 ", "Dune", testWording) shouldBe "Book 2 of Dune"
            nextDetail(null, "Dune", testWording) shouldBe "In Dune"
            nextDetail("  ", "Dune", testWording) shouldBe "In Dune"
        }
    }
})
