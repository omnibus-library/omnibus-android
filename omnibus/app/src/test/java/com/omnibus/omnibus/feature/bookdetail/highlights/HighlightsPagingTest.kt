package com.omnibus.omnibus.feature.bookdetail.highlights

import com.omnibus.omnibus.testing.randomInt
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class HighlightsPagingTest : DescribeSpec({

    describe("the paging step") {

        it("TC-25: adds one page") {
            val shown = randomInt(0..1_000)
            val page = randomInt(1..100)

            nextShownCount(shown, total = shown + page + randomInt(0..100), page = page) shouldBe shown + page
        }

        context("when a page would pass the total") {
            it("TC-25: stops at the total") {
                val shown = randomInt(0..1_000)
                val page = randomInt(2..100)
                val total = shown + randomInt(1 until page)

                nextShownCount(shown, total, page) shouldBe total
            }
        }
    }
})
