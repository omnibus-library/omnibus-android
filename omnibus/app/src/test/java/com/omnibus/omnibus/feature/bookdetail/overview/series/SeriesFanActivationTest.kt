package com.omnibus.omnibus.feature.bookdetail.overview.series

import com.omnibus.omnibus.feature.bookdetail.overview.description.BookDescriptionSection
import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class SeriesFanActivationTest : DescribeSpec({
    describe("the place of the series section on the Overview tab") {
        it("#104 TC-65: comes after the description") {
            val sections = BookDetailSlots.Registered.overviewSections

            (sections.indexOf(SeriesFanSection) > sections.indexOf(BookDescriptionSection)) shouldBe true
        }
    }
})
