package com.omnibus.omnibus.feature.search.rows

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class SearchBookRowTextTest : DescribeSpec({

    describe("the byline") {

        it("#351 TC-19: joins the author and the year with a dot") {
            bookByline("Robert Holdstock", "1984") shouldBe "Robert Holdstock · 1984"
        }

        it("#351 TC-19: drops the year when it is missing") {
            bookByline("Robert Holdstock", null) shouldBe "Robert Holdstock"
        }

        it("#351 TC-19: drops a blank author with its dot") {
            bookByline("", "1984") shouldBe "1984"
        }

        it("#351 TC-19: is empty when there is neither an author nor a year") {
            bookByline("  ", null) shouldBe ""
        }
    }

    describe("the row description") {

        it("#351 TC-19: joins every part with a comma, in order") {
            rowDescription(listOf("Book", "Mythago Wood", "Robert Holdstock", "1984", "ebook", "print")) shouldBe
                "Book, Mythago Wood, Robert Holdstock, 1984, ebook, print"
        }

        it("#351 TC-19: leaves out missing and blank parts") {
            rowDescription(listOf("Book", "Mythago Wood", null, "  ", "ebook")) shouldBe "Book, Mythago Wood, ebook"
        }
    }
})
