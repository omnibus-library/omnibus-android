package com.omnibus.omnibus.feature.bookdetail.overview.chips

import com.omnibus.omnibus.feature.bookdetail.slots.overviewSections
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ChipValuesTest : DescribeSpec({

    describe("genre and tag chip rows") {

        it("#100 AC5: a row is shown only when it has a value to show") {
            showsChipRow(listOf("Fantasy")) shouldBe true
            showsChipRow(listOf("Fantasy", "Epic")) shouldBe true
            showsChipRow(emptyList()) shouldBe false
        }

        it("#100 AC5: blank values do not make a row") {
            showsChipRow(listOf("", "   ")) shouldBe false
            showsChipRow(listOf("", "Fantasy")) shouldBe true
        }

        it("#100 AC1: chips keep their order, trimmed, with blanks and repeats removed") {
            chipValues(listOf(" Science Fiction ", "", "Dystopia", "Science Fiction", "  ")) shouldBe
                listOf("Science Fiction", "Dystopia")
        }

        it("#100 AC1: the section is registered after the description in the Overview slots") {
            overviewSections.indexOf(GenresTagsSection) shouldBe 1
        }
    }
})
