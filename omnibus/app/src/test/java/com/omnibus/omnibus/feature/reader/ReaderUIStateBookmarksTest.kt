package com.omnibus.omnibus.feature.reader

import com.omnibus.omnibus.data.bookmarks.Bookmark
import com.omnibus.omnibus.feature.reader.ui.RelocateData
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ReaderUIStateBookmarksTest : DescribeSpec({

    fun bookmark(position: String) = Bookmark(1L, "client-1", "book-1", position, null, null, null, null)

    fun stateAt(cfi: String?, vararg bookmarks: Bookmark) = ReaderUIState(
        location = RelocateData(cfi = cfi),
        bookmarks = bookmarks.toList(),
    )

    describe("bookmark derivations") {

        // TC-41: the count is the list size, and a page is bookmarked only by a trimmed exact cfi match.
        it("TC-41: bookmarkCount and isPageBookmarked derive from bookmarks and location") {
            ReaderUIState().bookmarkCount shouldBe 0
            stateAt("epubcfi(/6/8)", bookmark("epubcfi(/6/4)"), bookmark("epubcfi(/6/8)")).bookmarkCount shouldBe 2

            stateAt("epubcfi(/6/8)", bookmark("epubcfi(/6/8)")).isPageBookmarked shouldBe true
            stateAt("  epubcfi(/6/8)  ", bookmark("epubcfi(/6/8)")).isPageBookmarked shouldBe true
            stateAt("epubcfi(/6/8)", bookmark("  epubcfi(/6/8)\n")).isPageBookmarked shouldBe true
            stateAt("epubcfi(/6/8)", bookmark("epubcfi(/6/10)")).isPageBookmarked shouldBe false
            stateAt(null, bookmark("epubcfi(/6/8)")).isPageBookmarked shouldBe false
            stateAt("", bookmark("")).isPageBookmarked shouldBe false
            stateAt("   ", bookmark("   ")).isPageBookmarked shouldBe false
            ReaderUIState(bookmarks = listOf(bookmark("epubcfi(/6/8)"))).isPageBookmarked shouldBe false
            stateAt("epubcfi(/6/8)", bookmark("")).isPageBookmarked shouldBe false
        }
    }
})
