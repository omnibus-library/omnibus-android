package com.omnibus.omnibus.feature.bookdetail.more.bookmarks

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.detailText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BookmarksSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private val title get() = detailText(R.string.book_detail_bookmarks_title)
    private val closeLabel get() = detailText(R.string.book_detail_bookmarks_close)
    private val emptyCopy get() = detailText(R.string.book_detail_bookmarks_empty)
    private val loadingCopy get() = detailText(R.string.book_detail_bookmarks_loading)
    private val errorCopy get() = detailText(R.string.book_detail_bookmarks_error)
    private val retryLabel get() = detailText(R.string.book_detail_bookmarks_retry)

    /** The three sample rows: a titled text bookmark, an untitled audio one, and an untitled text one. */
    private val sampleRows = listOf(
        rowModel(bookmark(1, title = "The Episode of the Barrel"), BookmarkPlacement(7, 47)),
        rowModel(bookmark(2, position = "4332.5"), BookmarkPlacement(4, 28)),
        rowModel(bookmark(3, fraction = 0.41f), BookmarkPlacement(5, 41)),
    )

    // TC-9
    @Test
    fun issue117_tc9_titleIsAHeadingAndCloseIsLabelled() {
        rule.setSheetContent(BookmarksContent.Loaded(emptyList()))

        rule.onNodeWithText(title).assertIsDisplayed()
        rule.onNodeWithText(title).assert(SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit))
        rule.onNodeWithContentDescription(closeLabel).assertIsDisplayed().assertHasClickAction()
    }

    // TC-15
    @Test
    fun issue117_tc15_rowsRenderInOrderWithNameMetaRibbonAndChevron() {
        rule.setSheetContent(BookmarksContent.Loaded(sampleRows))

        val keys = listOf("id:1", "id:2", "id:3")
        val tops = keys.map { rule.onNodeWithTag(BookmarksTestTags.row(it)).fetchSemanticsNode().boundsInRoot.top }
        assertTrue("rows in given order", tops[0] < tops[1] && tops[1] < tops[2])

        rule.onNodeWithTag(BookmarksTestTags.name("id:1"), useUnmergedTree = true)
            .assertTextEquals("The Episode of the Barrel")
        rule.onNodeWithTag(BookmarksTestTags.meta("id:1"), useUnmergedTree = true)
            .assertTextEquals("Chapter 7 · 47% · October 28")
        rule.onNodeWithTag(BookmarksTestTags.name("id:2"), useUnmergedTree = true)
            .assertTextEquals("Listening, 1 h 12 m in")
        rule.onNodeWithTag(BookmarksTestTags.meta("id:2"), useUnmergedTree = true)
            .assertTextEquals("Chapter 4 · 28% · October 28")
        rule.onNodeWithTag(BookmarksTestTags.name("id:3"), useUnmergedTree = true)
            .assertTextEquals("Saved position")

        keys.forEach { key ->
            rule.onNodeWithTag(BookmarksTestTags.ribbon(key), useUnmergedTree = true).assertIsDisplayed()
            rule.onNodeWithTag(BookmarksTestTags.chevron(key), useUnmergedTree = true).assertIsDisplayed()
        }
    }

    // TC-16
    @Test
    fun issue117_tc16_rowWithoutMetaOmitsTheMetaLine() {
        val untitled = rowModel(bookmark(4, fraction = null, createdAtSeconds = null), BookmarkPlacement(null, null))
        rule.setSheetContent(BookmarksContent.Loaded(listOf(untitled)))

        rule.onNodeWithTag(BookmarksTestTags.name("id:4"), useUnmergedTree = true).assertIsDisplayed()
        rule.onAllNodesWithTag(BookmarksTestTags.meta("id:4"), useUnmergedTree = true).assertCountEquals(0)
    }

    // TC-37
    @Test
    fun issue117_tc37_emptyListShowsTheEmptyCopyWithoutRows() {
        rule.setSheetContent(BookmarksContent.Loaded(emptyList()))

        rule.onNodeWithText(emptyCopy).assertIsDisplayed()
        rule.onAllNodesWithTag(BookmarksTestTags.LIST, useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithTag(BookmarksTestTags.row("id:1"), useUnmergedTree = true).assertCountEquals(0)
    }

    // TC-39
    @Test
    fun issue117_tc39_loadingShowsAnIndicatorOnly() {
        rule.setSheetContent(BookmarksContent.Loading)

        rule.onNodeWithTag(BookmarksTestTags.LOADING).assertIsDisplayed()
        rule.onNodeWithText(loadingCopy).assertIsDisplayed()
        rule.onAllNodesWithText(emptyCopy).assertCountEquals(0)
        rule.onAllNodesWithTag(BookmarksTestTags.ERROR).assertCountEquals(0)
    }

    // TC-40
    @Test
    fun issue117_tc40_failedShowsTheErrorAndRetriesOnce() {
        var retries = 0
        rule.setSheetContent(BookmarksContent.Failed, onRetry = { retries++ })

        rule.onNodeWithText(errorCopy).assertIsDisplayed()
        rule.onNodeWithText(retryLabel).assertIsDisplayed().performClick()
        rule.waitForIdle()

        assertEquals(1, retries)
        rule.onAllNodesWithText(emptyCopy).assertCountEquals(0)
    }
}
