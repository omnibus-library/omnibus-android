package com.omnibus.omnibus.feature.bookdetail.status

import com.omnibus.omnibus.data.library.randomBook
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.boolean
import io.kotest.property.exhaustive.of

class ShowsReadStatusTest : DescribeSpec({

    describe("whether a book shows its read status") {

        context("when the book has an ebook or an audiobook") {
            it("TC-77: shows it, regardless of a physical copy or wishlist entry") {
                checkAll(Exhaustive.of("epub", "m4b"), Exhaustive.boolean(), Exhaustive.boolean()) { format, physical, wishlisted ->
                    val book = randomBook().copy(formats = listOf(format), hasPhysical = physical, isWishlisted = wishlisted)

                    showsReadStatus(book) shouldBe true
                }
            }
        }

        context("when the book has no file") {
            it("TC-77: shows it only for a physical copy or wishlist entry") {
                checkAll(Exhaustive.boolean(), Exhaustive.boolean()) { physical, wishlisted ->
                    val book = randomBook().copy(formats = emptyList(), hasPhysical = physical, isWishlisted = wishlisted)

                    showsReadStatus(book) shouldBe (physical || wishlisted)
                }
            }
        }
    }
})
