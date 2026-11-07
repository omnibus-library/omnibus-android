package com.omnibus.omnibus.feature.bookdetail.more.export

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.auth.CurrentUser
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.more.BookMoreMenuEntry
import com.omnibus.omnibus.feature.bookdetail.more.BookMoreTestTags
import com.omnibus.omnibus.feature.bookdetail.more.TestEntry
import com.omnibus.omnibus.feature.bookdetail.more.setMoreButton
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Stands in for [ExportMenuEntry]'s row without its ViewModel: the same gate and row, with [onOpen] for the tap.
 */
private class MenuRowEntry(
    private val user: CurrentUser?,
    private val onOpen: () -> Unit,
) : BookMoreMenuEntry {

    @Composable
    override fun Rows(context: BookDetailSectionContext, dismissMenu: () -> Unit) {
        if (!showsExportRow(context.book, user)) return
        ExportMenuRow {
            dismissMenu()
            onOpen()
        }
    }

    @Composable
    override fun Overlay(context: BookDetailSectionContext) = Unit
}

class ExportMenuRowTest {

    @get:Rule
    val rule = createComposeRule()

    private val exportLabel get() = detailText(R.string.book_detail_export_menu)
    private val needsConnection get() = detailText(R.string.book_detail_export_needs_connection)

    // TC-75
    @Test
    fun issue118_tc75_menuRowShowsExportAndTapClosesTheMenuAndOpensTheSheet() {
        var opened = 0
        rule.setMoreButton(listOf(MenuRowEntry(downloader) { opened++ }))

        rule.onNodeWithTag(BookMoreTestTags.MORE_BUTTON).performClick()
        rule.onNodeWithTag(ExportTestTags.MENU_ROW).assertIsDisplayed().assertIsEnabled()
        rule.onNodeWithText(exportLabel).assertIsDisplayed()
        rule.onAllNodesWithText(needsConnection).assertCountEquals(0)

        rule.onNodeWithTag(ExportTestTags.MENU_ROW).performClick()
        // Waits for the menu's exit animation to finish before asserting it has left the composition.
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag(BookMoreTestTags.MENU).fetchSemanticsNodes().isEmpty()
        }
        assertEquals(1, opened)
    }

    // TC-76
    @Test
    fun issue118_tc76_menuRowIsHiddenWhenTheReaderCannotExportTheBook() {
        // Formats name an audiobook only, and no user is loaded, so the gate is closed.
        rule.setMoreButton(
            listOf(TestEntry("Row"), MenuRowEntry(user = null) {}),
            testSectionContext(book = testDetailBook(formats = listOf("m4b"))),
        )

        rule.onNodeWithTag(BookMoreTestTags.MORE_BUTTON).performClick()
        rule.onNodeWithTag(BookMoreTestTags.MENU).assertIsDisplayed()
        rule.onAllNodesWithTag(ExportTestTags.MENU_ROW).assertCountEquals(0)
    }

    // TC-77
    @Test
    fun issue118_tc77_overlayDoesNotRequestTheViewModelBeforeTheMenuOpens() {
        // A plain compose rule has no Hilt: the overlay must return before any ViewModel is requested.
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ExportMenuEntry.Overlay(testSectionContext())
            }
        }
        rule.waitForIdle()
        rule.onAllNodesWithTag(ExportTestTags.SHEET).assertCountEquals(0)
    }
}
