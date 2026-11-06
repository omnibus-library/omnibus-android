package com.omnibus.omnibus.feature.reader.ui

import com.omnibus.omnibus.data.bookmarks.Bookmark
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ReaderContentsModelTest : DescribeSpec({

    /** Three top-level entries, so chapter numbers 1 to 3 are in range. */
    val toc = listOf(
        TocEntry(label = "One", href = "1.xhtml", level = 0),
        TocEntry(label = "Two", href = "2.xhtml", level = 0),
        TocEntry(label = "Three", href = "3.xhtml", level = 0),
    )

    describe("ReaderContentsModel") {

        it("CS-14: pageText shows only positive pages") {
            pageText(null) shouldBe null
            pageText(0) shouldBe null
            pageText(-1) shouldBe null
            pageText(1) shouldBe "1"
            pageText(999) shouldBe "999"
        }

        it("CS-21: the current index is the 1-based chapter minus one") {
            for (chapter in 1..3) {
                currentTocIndex(toc, RelocateData(chapter = chapter)) shouldBe chapter - 1
            }
        }

        it("CS-22: a chapter outside the toc, or no location, marks nothing") {
            currentTocIndex(toc, RelocateData(chapter = 0)) shouldBe null
            currentTocIndex(toc, RelocateData(chapter = 4)) shouldBe null
            currentTocIndex(toc, RelocateData(chapter = -1)) shouldBe null
            currentTocIndex(toc, RelocateData(chapter = Int.MAX_VALUE)) shouldBe null
            currentTocIndex(toc, null) shouldBe null
            currentTocIndex(emptyList(), RelocateData(chapter = 1)) shouldBe null
        }

        it("CS-32: the position line falls back from pages to percent, and shows nothing before locations") {
            ContentsPosition.from(RelocateData(page = 5, totalPages = 20, pct = 25)) shouldBe
                ContentsPosition.PageOf(5, 20)
            ContentsPosition.from(RelocateData(page = 0, totalPages = 20, pct = 3)) shouldBe
                ContentsPosition.Percent(3)
            ContentsPosition.from(RelocateData(page = 5, totalPages = 0, pct = 42)) shouldBe null
            ContentsPosition.from(RelocateData(page = 0, totalPages = 0, pct = 0)) shouldBe null
            ContentsPosition.from(null) shouldBe null
        }

        // TC-53: a row labels itself from its title, then its chapter title, and shows a whole percent when placed.
        it("TC-53: a bookmark row labels itself from title, chapter title, or nothing") {
            fun bookmark(title: String?, chapter: String?, fraction: Float?) =
                Bookmark(1L, "client-1", "book-1", "epubcfi(/6/4)", title, chapter, fraction, null)

            bookmarkLabel(bookmark("Mine", "One", 0.5f)) shouldBe "Mine"
            bookmarkLabel(bookmark(null, "One", 0.5f)) shouldBe "One"
            bookmarkLabel(bookmark(null, null, null)) shouldBe null
            bookmarkLabel(bookmark("  ", "", null)) shouldBe null
            bookmarkLabel(bookmark("  ", "Two", null)) shouldBe "Two"

            bookmarkPercent(bookmark(null, null, 0.426f)) shouldBe 43
            bookmarkPercent(bookmark(null, null, 0f)) shouldBe 0
            bookmarkPercent(bookmark(null, null, 1f)) shouldBe 100
            bookmarkPercent(bookmark(null, null, null)) shouldBe null
        }
    }
})
