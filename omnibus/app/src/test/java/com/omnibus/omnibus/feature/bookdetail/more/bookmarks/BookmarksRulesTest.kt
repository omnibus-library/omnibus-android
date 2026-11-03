package com.omnibus.omnibus.feature.bookdetail.more.bookmarks

import com.omnibus.omnibus.data.library.BookFile
import com.omnibus.omnibus.data.library.testBook
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

private val oneFile = BookFile(
    id = 9,
    format = "Epub",
    filename = "dune.epub",
    ordinal = 0,
    label = null,
    sizeBytes = 1_024,
    etag = null,
)

class BookmarksRulesTest : DescribeSpec({

    describe("the menu row's visibility") {

        it("#117 TC-4: shows for a book with files") {
            showsBookmarksRow(testBook(1, formats = listOf("epub"), bookFiles = emptyList())) shouldBe true
            showsBookmarksRow(testBook(2, formats = emptyList(), bookFiles = listOf(oneFile))) shouldBe true
            showsBookmarksRow(testBook(3, formats = listOf("epub"), bookFiles = listOf(oneFile))) shouldBe true
        }

        it("#117 TC-5: hidden for a book without files") {
            showsBookmarksRow(testBook(4, formats = emptyList(), bookFiles = emptyList())) shouldBe false
        }
    }
})
