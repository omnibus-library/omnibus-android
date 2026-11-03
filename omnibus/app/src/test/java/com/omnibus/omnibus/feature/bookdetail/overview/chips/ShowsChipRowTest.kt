package com.omnibus.omnibus.feature.bookdetail.overview.chips

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ShowsChipRowTest : DescribeSpec({

    describe("showsChipRow") {

        it("TC-4: an editor always sees the row, even with nothing to show") {
            showsChipRow(emptyList(), canEdit = true) shouldBe true
            showsChipRow(listOf("", "   "), canEdit = true) shouldBe true
            showsChipRow(listOf("Fantasy"), canEdit = true) shouldBe true
        }

        it("TC-5: a non-editor keeps #100 behaviour, and the default is not an editor") {
            showsChipRow(emptyList(), canEdit = false) shouldBe false
            showsChipRow(listOf("", "   "), canEdit = false) shouldBe false
            showsChipRow(listOf("Fantasy"), canEdit = false) shouldBe true
            showsChipRow(emptyList()) shouldBe showsChipRow(emptyList(), canEdit = false)
            showsChipRow(listOf("Fantasy")) shouldBe showsChipRow(listOf("Fantasy"), canEdit = false)
        }
    }
})
