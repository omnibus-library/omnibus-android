package com.omnibus.omnibus.feature.bookdetail.slots

import com.omnibus.omnibus.feature.bookdetail.overview.activity.HouseholdActivitySection
import com.omnibus.omnibus.feature.bookdetail.overview.chips.GenresTagsSection
import com.omnibus.omnibus.feature.bookdetail.overview.reading.ReadingCardSection
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan

class ReadingCardActivationTest : DescribeSpec({

    describe("reading card slot") {

        it("TC-42: the overview registers the reading card between genres and household activity") {
            val sections = overviewSections
            val card = sections.indexOf(ReadingCardSection)
            card shouldBeGreaterThan -1
            card shouldBeGreaterThan sections.indexOf(GenresTagsSection)
            card shouldBeLessThan sections.indexOf(HouseholdActivitySection)

            BookDetailSlots.Registered.overviewSections shouldContain ReadingCardSection
        }
    }
})
