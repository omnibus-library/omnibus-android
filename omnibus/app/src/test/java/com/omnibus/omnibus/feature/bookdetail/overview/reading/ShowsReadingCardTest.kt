package com.omnibus.omnibus.feature.bookdetail.overview.reading

import com.omnibus.omnibus.data.library.randomBook
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class ShowsReadingCardTest : DescribeSpec({
    describe("whether the reading card appears") {
        context("when the book has a copy") {
            it("TC-39: shows the card, for an ebook, an audiobook, a pdf or a physical copy") {
                val noCopy = randomBook().copy(formats = emptyList(), hasPhysical = false)
                val books = Exhaustive.of(
                    noCopy.copy(formats = listOf("epub")),
                    noCopy.copy(formats = listOf("m4b")),
                    noCopy.copy(formats = listOf("pdf")),
                    noCopy.copy(hasPhysical = true),
                )

                checkAll(books) { book -> showsReadingCard(book) shouldBe true }
            }
        }

        context("when the book has no copy") {
            it("TC-39: leaves the card out") {
                showsReadingCard(randomBook().copy(formats = emptyList(), hasPhysical = false)) shouldBe false
            }
        }
    }
})
