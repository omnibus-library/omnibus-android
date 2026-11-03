package com.omnibus.omnibus.feature.bookdetail.more.delete

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.more.BookMoreMenuEntry
import com.omnibus.omnibus.feature.bookdetail.more.BookMoreTestTags
import com.omnibus.omnibus.feature.bookdetail.more.FakeEntry
import com.omnibus.omnibus.feature.bookdetail.more.rowTag
import com.omnibus.omnibus.feature.bookdetail.more.setMoreButton
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** An enabled delete row as a registry entry, to sit beside another entry in the real menu. */
private object EnabledDeleteEntry : BookMoreMenuEntry {
    @Composable
    override fun Rows(context: BookDetailSectionContext, dismissMenu: () -> Unit) {
        DeleteMenuRow(row = DeleteRow.Enabled(DeleteRowLabel.Files), onClick = {})
    }

    @Composable
    override fun Overlay(context: BookDetailSectionContext) {
    }
}

class DeleteMenuRowTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-41
    @Test
    fun issue121_tc41_theRowStatesShowTheRightLabelAndOnlyTheEnabledRowTaps() {
        var row by mutableStateOf<DeleteRow>(DeleteRow.Hidden)
        var taps = 0
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                DeleteMenuRow(row = row, onClick = { taps++ })
            }
        }
        rule.onAllNodesWithTag(DeleteTestTags.MENU_ROW).assertCountEquals(0)

        rule.runOnIdle { row = DeleteRow.Enabled(DeleteRowLabel.Files) }
        rule.onNodeWithText(detailText(R.string.book_detail_delete_row_files)).assertIsDisplayed()
        rule.onAllNodesWithText(detailText(R.string.book_detail_more_needs_connection)).assertCountEquals(0)
        rule.onNodeWithTag(DeleteTestTags.MENU_ROW).performClick()
        rule.waitForIdle()
        assertEquals(1, taps)

        rule.runOnIdle { row = DeleteRow.Enabled(DeleteRowLabel.Record) }
        rule.onNodeWithText(detailText(R.string.book_detail_delete_row_record)).assertIsDisplayed()

        rule.runOnIdle { row = DeleteRow.DisabledOffline(DeleteRowLabel.Files) }
        rule.onNodeWithText(detailText(R.string.book_detail_delete_row_files)).assertIsDisplayed()
        rule.onNodeWithText(detailText(R.string.book_detail_more_needs_connection)).assertIsDisplayed()
        rule.onNodeWithTag(DeleteTestTags.MENU_ROW).assertIsNotEnabled()
        rule.onNodeWithTag(DeleteTestTags.MENU_ROW).performClick()
        rule.waitForIdle()
        assertEquals(1, taps)

        rule.runOnIdle { row = DeleteRow.Hidden }
        rule.onAllNodesWithTag(DeleteTestTags.MENU_ROW).assertCountEquals(0)
    }

    // TC-42
    @Test
    fun issue121_tc42_theRowIsADestructiveButtonWithTheDeleteClickLabel() {
        var row by mutableStateOf<DeleteRow>(DeleteRow.Enabled(DeleteRowLabel.Files))
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                DeleteMenuRow(row = row, onClick = {})
            }
        }

        val enabled = rule.onNodeWithTag(DeleteTestTags.MENU_ROW)
        enabled.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        enabled.assert(
            SemanticsMatcher("click label reads delete permanently") {
                it.config.getOrNull(SemanticsActions.OnClick)?.label ==
                    detailText(R.string.book_detail_delete_a11y_destructive)
            },
        )
        enabled.assert(SemanticsMatcher.expectValue(DeleteSemantics.Destructive, true))
        rule.onNodeWithTag(DeleteTestTags.MENU_ICON, useUnmergedTree = true).assertIsDisplayed()

        rule.runOnIdle { row = DeleteRow.DisabledOffline(DeleteRowLabel.Record) }
        rule.onNodeWithTag(DeleteTestTags.MENU_ROW).assertIsNotEnabled()
        rule.onNodeWithTag(DeleteTestTags.MENU_ROW).assert(SemanticsMatcher.expectValue(DeleteSemantics.Destructive, true))
    }

    // TC-43
    @Test
    fun issue121_tc43_theRowSitsBelowAnotherEntryBehindARuleAndIsAsWide() {
        rule.setMoreButton(listOf(FakeEntry("Other row"), EnabledDeleteEntry), testSectionContext())
        rule.onNodeWithTag(BookMoreTestTags.MORE_BUTTON).performClick()
        rule.waitForIdle()

        val other = rule.onNodeWithTag(rowTag("Other row")).bounds()
        val delete = rule.onNodeWithTag(DeleteTestTags.MENU_ROW).bounds()
        assertTrue("the delete row is below the other entry", delete.top >= other.bottom - 1f)

        val rules = rule.onAllNodesWithTag(BookMoreTestTags.MENU_RULE, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .filter { it.layoutInfo.isPlaced }
        assertEquals("one rule between the two entries", 1, rules.size)
        val placed = rules.single().boundsInRoot
        assertTrue(
            "the rule sits between the entries",
            placed.top >= other.bottom - 1f && placed.bottom <= delete.top + 1f,
        )

        assertTrue("the delete row is as wide as the other row", delete.width >= other.width - 1f)
    }
}
