package com.omnibus.omnibus.feature.bookdetail.more.export

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.data.auth.CurrentUser
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.feature.bookdetail.more.BookMoreMenuEntry
import com.omnibus.omnibus.feature.bookdetail.more.BookMoreTestTags
import com.omnibus.omnibus.feature.bookdetail.more.setMoreButton
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

    // TC-75
    @Test
    fun issue118_tc75_tappingTheMenuRowClosesTheMenuThenOpensTheSheet() {
        var opened = 0
        rule.setMoreButton(listOf(MenuRowEntry(downloader) { opened++ }))
        rule.onNodeWithTag(BookMoreTestTags.MORE_BUTTON).performClick()

        rule.onNodeWithTag(ExportTestTags.MENU_ROW).performClick()

        // Waits for the menu's exit animation to finish before asserting it has left the composition.
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag(BookMoreTestTags.MENU).fetchSemanticsNodes().isEmpty()
        }
        assertEquals(1, opened)
    }
}
