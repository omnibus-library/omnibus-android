package com.omnibus.omnibus.feature.bookdetail.overview.reading

import com.omnibus.omnibus.data.library.testBook
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ShowsReadingCardTest : DescribeSpec({
    describe("showsReadingCard") {
        it("TC-39: a book with any copy shows the card, and one with no copy does not") {
            showsReadingCard(testBook(1, formats = listOf("epub"))) shouldBe true
            showsReadingCard(testBook(2, formats = listOf("m4b"))) shouldBe true
            showsReadingCard(testBook(3, formats = listOf("pdf"))) shouldBe true
            showsReadingCard(testBook(4, formats = emptyList()).copy(hasPhysical = true)) shouldBe true
            showsReadingCard(testBook(5, formats = emptyList())) shouldBe false
            showsReadingCard(testBook(6, formats = emptyList(), isWishlisted = true)) shouldBe false
        }
    }
})
