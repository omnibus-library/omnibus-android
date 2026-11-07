package com.omnibus.omnibus.feature.bookdetail.overview.chips

import com.omnibus.omnibus.feature.bookdetail.overview.description.BookDescriptionSection
import com.omnibus.omnibus.feature.bookdetail.slots.overviewSections
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class ChipValuesTest : DescribeSpec({
    describe("the chips of a genre or tag row") {
        it("#100 AC1: keep their order, trimmed, without blanks or repeats") {
            chipValues(listOf(" Science Fiction ", "", "Dystopia", "Science Fiction", "  ")) shouldContainExactly
                listOf("Science Fiction", "Dystopia")
        }
    }

    describe("whether a chip row appears") {
        context("when the user can edit") {
            it("TC-4: shows the row whatever its values") {
                checkAll(Exhaustive.of(emptyList(), listOf("", "   "), listOf("Fantasy"))) { values ->
                    showsChipRow(values, canEdit = true) shouldBe true
                }
            }
        }

        context("when the user cannot edit") {
            it("TC-5: leaves out a row with nothing to show") {
                checkAll(Exhaustive.of(emptyList(), listOf("", "   "))) { values ->
                    showsChipRow(values, canEdit = false) shouldBe false
                }
            }

            it("TC-5: shows a row with a value") {
                showsChipRow(listOf("", "Fantasy"), canEdit = false) shouldBe true
            }
        }
    }

    describe("the place of the section on the Overview tab") {
        it("#100 AC1: comes after the description") {
            (overviewSections.indexOf(GenresTagsSection) > overviewSections.indexOf(BookDescriptionSection)) shouldBe true
        }
    }
})
