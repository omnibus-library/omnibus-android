package com.omnibus.omnibus.feature.bookdetail.more.bookmarks

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.more.BookMoreTestTags
import com.omnibus.omnibus.feature.bookdetail.more.pressBackKey
import com.omnibus.omnibus.feature.bookdetail.more.setMoreButton
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BookmarksOverlayTest {

    @get:Rule
    val rule = createComposeRule()

    /** The sheet window with one row titled "Barrel", recording its close and open calls in [events]. */
    private fun setSheetWindow(events: MutableList<String>, rows: List<BookmarkRowModel> = listOf(rowModel(bookmark(1, title = "Barrel")))) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                BookmarksSheet(
                    content = BookmarksContent.Loaded(rows),
                    nowMillis = SHEET_NOW,
                    ribbonColor = TestRibbon,
                    onRetry = {},
                    onClose = { events += "close" },
                    onOpen = { events += "open:$it" },
                )
            }
        }
    }

    // TC-10
    @Test
    fun issue117_tc10_closeButtonClosesTheSheetOnly() {
        val events = mutableListOf<String>()
        setSheetWindow(events)

        rule.onNodeWithTag(BookmarksTestTags.CLOSE).performClick()
        rule.waitUntil(timeoutMillis = 5_000) { events.size == 1 }
        rule.waitForIdle()

        assertEquals(listOf("close"), events)
    }

    // TC-10
    @Test
    fun issue117_tc10_systemBackClosesTheSheetOnly() {
        val events = mutableListOf<String>()
        setSheetWindow(events)
        rule.onNodeWithTag(BookmarksTestTags.SHEET).assertIsDisplayed()

        rule.pressBackKey()
        rule.waitUntil(timeoutMillis = 5_000) { events.size == 1 }
        rule.waitForIdle()

        assertEquals(listOf("close"), events)
    }

    // TC-44
    @Test
    fun issue117_tc44_rowTapClosesBeforeNavigating() {
        val events = mutableListOf<String>()
        setSheetWindow(events)

        rule.onNodeWithTag(BookmarksTestTags.row("id:1")).performClick()
        rule.waitUntil(timeoutMillis = 5_000) { events.size == 2 }
        rule.waitForIdle()

        assertEquals(
            listOf("close", "open:${BookDetailDestination.Reader("u", "epubcfi(/6/14!/4/2)")}"),
            events,
        )
    }

    // TC-45
    @Test
    fun issue117_tc45_secondTapDuringHideIsIgnored() {
        val events = mutableListOf<String>()
        setSheetWindow(
            events,
            rows = listOf(
                rowModel(bookmark(1, title = "One")),
                rowModel(bookmark(2, title = "Two", position = "epubcfi(/6/20!/4/2)")),
            ),
        )

        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag(BookmarksTestTags.row("id:1")).performClick()
        rule.onNodeWithTag(BookmarksTestTags.row("id:2")).performClick()
        rule.mainClock.autoAdvance = true

        rule.waitUntil(timeoutMillis = 5_000) { events.size >= 2 }
        rule.waitForIdle()

        assertEquals(
            listOf("close", "open:${BookDetailDestination.Reader("u", "epubcfi(/6/14!/4/2)")}"),
            events,
        )
    }

    // TC-7
    @Test
    fun issue117_tc7_rowTapClosesTheMenuThenOpensTheSheet() {
        val sheetOpen = mutableStateOf(false)
        rule.setMoreButton(
            listOf(
                BookmarksTestEntry(
                    onOpen = { sheetOpen.value = true },
                    overlay = {
                        BookmarksOverlayContent(
                            state = BookmarksUIState(
                                sheetOpen = sheetOpen.value,
                                content = BookmarksContent.Loaded(emptyList()),
                                nowMillis = SHEET_NOW,
                            ),
                            context = testSectionContext(),
                            onRetry = {},
                            onClose = { sheetOpen.value = false },
                        )
                    },
                ),
            ),
        )

        rule.onNodeWithTag(BookMoreTestTags.MORE_BUTTON).performClick()
        rule.onNodeWithTag(BookmarksTestTags.MENU_ROW).assertIsEnabled().performClick()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag(BookMoreTestTags.MENU).fetchSemanticsNodes().isEmpty()
        }
        rule.onNodeWithText(detailText(R.string.book_detail_bookmarks_title)).assertIsDisplayed()
    }
}
