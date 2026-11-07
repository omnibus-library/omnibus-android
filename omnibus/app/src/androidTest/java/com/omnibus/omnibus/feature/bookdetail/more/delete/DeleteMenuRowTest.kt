package com.omnibus.omnibus.feature.bookdetail.more.delete

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DeleteMenuRowTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-41
    @Test
    fun issue121_tc41_onlyTheEnabledRowTaps() {
        var row by mutableStateOf<DeleteRow>(DeleteRow.Enabled(DeleteRowLabel.Files))
        var taps = 0
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                DeleteMenuRow(row = row, onClick = { taps++ })
            }
        }

        rule.onNodeWithTag(DeleteTestTags.MENU_ROW).performClick()
        rule.waitForIdle()
        assertEquals(1, taps)

        rule.runOnIdle { row = DeleteRow.DisabledOffline(DeleteRowLabel.Files) }
        rule.onNodeWithTag(DeleteTestTags.MENU_ROW).assertIsNotEnabled()
        rule.onNodeWithTag(DeleteTestTags.MENU_ROW).performClick()
        rule.waitForIdle()

        assertEquals(1, taps)
    }

    // TC-42
    @Test
    fun issue121_tc42_theRowIsADestructiveButtonWithTheDeleteClickLabel() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                DeleteMenuRow(row = DeleteRow.Enabled(DeleteRowLabel.Files), onClick = {})
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
    }
}
