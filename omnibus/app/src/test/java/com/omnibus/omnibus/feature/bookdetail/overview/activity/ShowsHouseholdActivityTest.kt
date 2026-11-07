package com.omnibus.omnibus.feature.bookdetail.overview.activity

import com.omnibus.omnibus.data.library.randomBook
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class ShowsHouseholdActivityTest : DescribeSpec({
    describe("whether the activity section appears") {
        context("when the book has no copy") {
            it("TC-16: leaves the section out") {
                showsHouseholdActivity(randomBook().copy(formats = emptyList(), hasPhysical = false)) shouldBe false
            }
        }

        context("when the book has a digital copy") {
            it("TC-17: shows the section, for an ebook or an audiobook") {
                checkAll(Exhaustive.of("epub", "m4b")) { format ->
                    showsHouseholdActivity(randomBook().copy(formats = listOf(format), hasPhysical = false)) shouldBe true
                }
            }
        }
    }
})
