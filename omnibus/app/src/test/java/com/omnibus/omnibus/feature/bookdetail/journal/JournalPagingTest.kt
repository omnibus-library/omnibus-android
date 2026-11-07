package com.omnibus.omnibus.feature.bookdetail.journal

import com.omnibus.omnibus.testing.randomInt
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class JournalPagingTest : DescribeSpec({

    describe("the paging step") {

        it("TC-45: adds one page") {
            val shown = randomInt(0..1_000)
            val page = randomInt(1..100)

            nextJournalShownCount(shown, total = shown + page + randomInt(0..100), page = page) shouldBe shown + page
        }

        context("when a page would pass the total") {
            it("TC-45: stops at the total") {
                val shown = randomInt(0..1_000)
                val page = randomInt(2..100)
                val total = shown + randomInt(1 until page)

                nextJournalShownCount(shown, total, page) shouldBe total
            }
        }
    }
})
