package com.omnibus.omnibus.feature.bookdetail.more.bookmarks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.more.BookMoreMenu
import com.omnibus.omnibus.feature.bookdetail.more.BookMoreMenuEntry
import com.omnibus.omnibus.feature.bookdetail.more.BookMoreTestTags
import com.omnibus.omnibus.feature.bookdetail.more.FakeEntry
import com.omnibus.omnibus.feature.bookdetail.more.MORE_HOST
import com.omnibus.omnibus.feature.bookdetail.more.rowTag
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BookmarksMenuEntryTest {

    @get:Rule
    val rule = createComposeRule()

    private val bookmarksLabel get() = detailText(R.string.book_detail_bookmarks_title)

    /** Sets a More menu that is always open, with [entries] for [book], in [darkTheme]. */
    private fun setMenu(book: Book, entries: List<BookMoreMenuEntry>, darkTheme: Boolean = true) {
        rule.setContent {
            OmnibusTheme(darkTheme = darkTheme) {
                Box(Modifier.fillMaxSize().testTag(MORE_HOST)) {
                    BookMoreMenu(
                        expanded = true,
                        onDismiss = {},
                        context = testSectionContext(book = book),
                        entries = entries,
                    )
                }
            }
        }
    }

    /** The menu rule nodes that were placed, not the surplus ones composed but left unplaced. */
    private fun placedRules(): Int = rule.onAllNodesWithTag(BookMoreTestTags.MENU_RULE, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .count { it.layoutInfo.isPlaced }

    // TC-1
    @Test
    fun issue117_tc1_bookmarksRowFollowsAddToShelfInTheSameGroup() {
        setMenu(
            testDetailBook(formats = listOf("epub")),
            listOf(FakeEntry("Add to shelf"), BookmarksTestEntry(), FakeEntry("Export")),
        )
        rule.onNodeWithTag(BookMoreTestTags.MENU).assertIsDisplayed()

        val shelf = rule.onNodeWithTag(rowTag("Add to shelf")).fetchSemanticsNode().boundsInRoot
        val bookmarks = rule.onNodeWithTag(BookmarksTestTags.MENU_ROW).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val export = rule.onNodeWithTag(rowTag("Export")).fetchSemanticsNode().boundsInRoot
        rule.onNodeWithText(bookmarksLabel).assertIsDisplayed()

        assertTrue("the bookmarks row sits below add to shelf", bookmarks.top >= shelf.bottom - 1f)
        assertEquals("one thick rule, between bookmarks and export", 1, placedRules())
        val placed = rule.onAllNodesWithTag(BookMoreTestTags.MENU_RULE, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .single { it.layoutInfo.isPlaced }
            .boundsInRoot
        assertTrue(
            "the rule sits between bookmarks and export",
            placed.top >= bookmarks.bottom - 1f && placed.bottom <= export.top + 1f,
        )
    }

    // TC-6
    @Test
    fun issue117_tc6_rowIsAbsentWithoutFilesAndLeavesNoRule() {
        setMenu(
            testDetailBook(formats = emptyList()),
            listOf(FakeEntry("Add to shelf"), BookmarksTestEntry(), FakeEntry("Export")),
        )
        rule.onNodeWithTag(BookMoreTestTags.MENU).assertIsDisplayed()

        rule.onAllNodesWithTag(BookmarksTestTags.MENU_ROW).assertCountEquals(0)
        assertEquals("one rule, between add to shelf and export", 1, placedRules())
    }

    // TC-64
    @Test
    fun issue117_tc64_menuRowMatchesSiblingRowsInLightTheme() {
        assertRowMatchesSibling(darkTheme = false)
    }

    // TC-64
    @Test
    fun issue117_tc64_menuRowMatchesSiblingRowsInDarkTheme() {
        assertRowMatchesSibling(darkTheme = true)
    }

    /** The row carries its label and is as tall as a sibling row, in [darkTheme]. */
    private fun assertRowMatchesSibling(darkTheme: Boolean) {
        setMenu(
            testDetailBook(formats = listOf("epub")),
            listOf(FakeEntry("Add to shelf"), BookmarksTestEntry(), FakeEntry("Export")),
            darkTheme = darkTheme,
        )
        rule.onNodeWithTag(BookMoreTestTags.MENU).assertIsDisplayed()

        rule.onNodeWithText(bookmarksLabel).assertIsDisplayed()
        val bookmarksHeight = rule.onNodeWithTag(BookmarksTestTags.MENU_ROW).fetchSemanticsNode().boundsInRoot.height
        val siblingHeight = rule.onNodeWithTag(rowTag("Add to shelf")).fetchSemanticsNode().boundsInRoot.height
        assertEquals("row height matches its sibling", siblingHeight, bookmarksHeight, 1f)
        rule.onNodeWithTag(BookmarksTestTags.MENU_ROW).assertIsEnabled()
    }
}
