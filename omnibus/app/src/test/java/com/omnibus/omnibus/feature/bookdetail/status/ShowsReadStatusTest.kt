package com.omnibus.omnibus.feature.bookdetail.status

import com.omnibus.omnibus.data.library.testBook
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ShowsReadStatusTest : DescribeSpec({

    describe("showsReadStatus") {

        it("TC-77: a book shows the status unless it has no ebook, audiobook, physical copy or wishlist entry") {
            for (ebook in listOf(false, true)) {
                for (audiobook in listOf(false, true)) {
                    for (physical in listOf(false, true)) {
                        for (wishlisted in listOf(false, true)) {
                            val formats = buildList {
                                if (ebook) add("epub")
                                if (audiobook) add("m4b")
                            }
                            val book = testBook(1).copy(formats = formats, hasPhysical = physical, isWishlisted = wishlisted)

                            showsReadStatus(book) shouldBe (ebook || audiobook || physical || wishlisted)
                        }
                    }
                }
            }
        }
    }
})
