package com.omnibus.omnibus.feature.bookdetail.more.merge

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Renders [MergeMenuRow] for [row] inside a menu that is always open, and counts its taps. */
private fun ComposeContentTestRule.setRow(row: MergeRow, onClick: () -> Unit) {
    setContent {
        OmnibusTheme(darkTheme = true) {
            Box(Modifier.fillMaxSize()) {
                DropdownMenu(expanded = true, onDismissRequest = {}) {
                    MergeMenuRow(row = row, onClick = onClick)
                }
            }
        }
    }
}

class MergeMenuRowTest {

    @get:Rule
    val rule = createComposeRule()

    private val label get() = detailText(R.string.book_detail_merge_menu_row)
    private val needsConnection get() = detailText(R.string.book_detail_more_needs_connection)

    // TC-62
    @Test
    fun issue120_tc62_enabledRowClicksAndHiddenDrawsNothing() {
        var taps = 0
        rule.setRow(MergeRow.Enabled) { taps++ }

        rule.onNodeWithTag(MergeTestTags.MENU_ROW)
            .assertIsEnabled()
            .assertHasClickAction()
        rule.onNodeWithText(label).assertIsDisplayed()
        rule.onAllNodesWithText(needsConnection).assertCountEquals(0)
        rule.onNodeWithTag(MergeTestTags.MENU_ROW).performClick()
        rule.waitForIdle()
        assertEquals(1, taps)
    }

    // TC-62
    @Test
    fun issue120_tc62_hiddenRowDrawsNothing() {
        rule.setRow(MergeRow.Hidden) {}

        rule.onNodeWithTag(MergeTestTags.MENU_ROW).assertDoesNotExist()
        rule.onAllNodesWithText(label).assertCountEquals(0)
    }

    // TC-63
    @Test
    fun issue120_tc63_offlineRowIsDisabledAndReadsItsSubLine() {
        var taps = 0
        rule.setRow(MergeRow.DisabledOffline) { taps++ }

        rule.onNodeWithTag(MergeTestTags.MENU_ROW).assertIsNotEnabled()
        val texts = rule.onNodeWithTag(MergeTestTags.MENU_ROW).fetchSemanticsNode()
            .config[SemanticsProperties.Text].joinToString(" ") { it.text }
        assertTrue("merged text was '$texts'", texts.contains(label) && texts.contains(needsConnection))
        rule.onNodeWithTag(MergeTestTags.MENU_ROW).performTouchInput { click() }
        rule.waitForIdle()
        assertEquals(0, taps)
    }
}
