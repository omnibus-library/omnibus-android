package com.omnibus.omnibus.feature.bookdetail.overview.activity

import com.omnibus.omnibus.data.library.testBook
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ShowsHouseholdActivityTest : DescribeSpec({
    describe("showsHouseholdActivity") {
        it("TC-16: a book with no copy to read or listen to shows no activity, so it needs no ViewModel") {
            showsHouseholdActivity(testBook(1, formats = emptyList())) shouldBe false
        }

        it("TC-17: an ebook or an audiobook shows activity") {
            showsHouseholdActivity(testBook(1, formats = listOf("epub"))) shouldBe true
            showsHouseholdActivity(testBook(2, formats = listOf("m4b"))) shouldBe true
        }
    }
})
