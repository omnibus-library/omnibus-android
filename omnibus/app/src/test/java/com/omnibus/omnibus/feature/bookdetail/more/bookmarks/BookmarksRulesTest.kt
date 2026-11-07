package com.omnibus.omnibus.feature.bookdetail.more.bookmarks

import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.data.library.randomBookFile
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class BookmarksRulesTest : DescribeSpec({

    describe("the menu row's visibility") {
        context("when the book has files") {
            it("#117 TC-4: shows the row") {
                checkAll(
                    Exhaustive.of(
                        randomBook().copy(formats = listOf(randomString()), bookFiles = emptyList()),
                        randomBook().copy(formats = emptyList(), bookFiles = listOf(randomBookFile())),
                    ),
                ) { book ->
                    showsBookmarksRow(book) shouldBe true
                }
            }
        }

        context("when the book has no files") {
            it("#117 TC-5: hides the row") {
                showsBookmarksRow(randomBook().copy(formats = emptyList(), bookFiles = emptyList())) shouldBe false
            }
        }
    }
})
