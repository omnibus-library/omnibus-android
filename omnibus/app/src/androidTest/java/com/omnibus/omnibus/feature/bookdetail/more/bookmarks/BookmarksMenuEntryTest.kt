package com.omnibus.omnibus.feature.bookdetail.more.bookmarks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.feature.bookdetail.more.BookMoreMenu
import com.omnibus.omnibus.feature.bookdetail.more.BookMoreMenuEntry
import com.omnibus.omnibus.feature.bookdetail.more.BookMoreTestTags
import com.omnibus.omnibus.feature.bookdetail.more.TestEntry
import com.omnibus.omnibus.feature.bookdetail.more.MORE_HOST
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BookmarksMenuEntryTest {

    @get:Rule
    val rule = createComposeRule()

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

    // TC-6
    @Test
    fun issue117_tc6_rowIsAbsentWithoutFilesAndLeavesNoRule() {
        setMenu(
            testDetailBook(formats = emptyList()),
            listOf(TestEntry("Add to shelf"), BookmarksTestEntry(), TestEntry("Export")),
        )
        rule.onNodeWithTag(BookMoreTestTags.MENU).assertIsDisplayed()

        rule.onAllNodesWithTag(BookmarksTestTags.MENU_ROW).assertCountEquals(0)
        assertEquals("one rule, between add to shelf and export", 1, placedRules())
    }
}
