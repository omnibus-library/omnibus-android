package com.omnibus.omnibus.feature.reader.ui

import com.omnibus.omnibus.data.bookmarks.randomBookmark
import com.omnibus.omnibus.testing.randomInt
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class ReaderContentsModelTest : DescribeSpec({

    describe("an entry's page text") {
        it("CS-14: shows only a positive page") {
            pageText(1) shouldBe "1"
            checkAll(Exhaustive.of(null, 0, -1)) { page ->
                pageText(page) shouldBe null
            }
        }
    }

    describe("the current contents entry") {
        fun toc() = List(3) { TocEntry(href = randomString()) }

        context("with a chapter inside the contents") {
            it("CS-21: is the 1-based chapter's entry, for the first and last chapters") {
                checkAll(Exhaustive.of(1, 3)) { chapter ->
                    currentTocIndex(toc(), randomRelocateData().copy(chapter = chapter)) shouldBe chapter - 1
                }
            }
        }

        context("with a chapter outside the contents") {
            it("CS-22: is nothing, on either side") {
                checkAll(Exhaustive.of(-1, 0, 4)) { chapter ->
                    currentTocIndex(toc(), randomRelocateData().copy(chapter = chapter)) shouldBe null
                }
            }
        }
    }

    describe("the header's position line") {
        context("once the book has locations") {
            it("CS-32: falls back from page of total to the percent") {
                val location = randomRelocateData().copy(totalPages = randomInt(1..1000))

                ContentsPosition.from(location.copy(page = 5)) shouldBe ContentsPosition.PageOf(5, location.totalPages)
                ContentsPosition.from(location.copy(page = 0)) shouldBe ContentsPosition.Percent(location.pct)
            }
        }

        context("before the book has locations") {
            it("CS-32: shows nothing") {
                ContentsPosition.from(randomRelocateData().copy(totalPages = 0)) shouldBe null
            }
        }
    }

    describe("a bookmark row") {
        it("TC-53: labels itself from its title, then its chapter title") {
            val bookmark = randomBookmark().copy(title = "Mine", chapterTitle = "One")

            bookmarkLabel(bookmark) shouldBe "Mine"
            bookmarkLabel(bookmark.copy(title = "  ")) shouldBe "One"
            bookmarkLabel(bookmark.copy(title = null, chapterTitle = "")) shouldBe null
        }

        it("TC-53: shows its place as a whole percent") {
            val bookmark = randomBookmark()

            bookmarkPercent(bookmark.copy(fraction = 0.426f)) shouldBe 43
            bookmarkPercent(bookmark.copy(fraction = null)) shouldBe null
        }
    }
})
