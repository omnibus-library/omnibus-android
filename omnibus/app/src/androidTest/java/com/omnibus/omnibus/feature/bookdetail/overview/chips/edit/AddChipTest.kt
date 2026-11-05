package com.omnibus.omnibus.feature.bookdetail.overview.chips.edit

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.metadata.MetadataListKind
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The dashed "+" chip: its names, role, touch target, and what a tap does. */
class AddChipTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-62
    @Test
    fun issue101_ac7_addChipsHaveTheirDescriptionsAndButtonRole() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                AddChip(MetadataListKind.Genres, enabled = true, onClick = {})
                AddChip(MetadataListKind.Tags, enabled = true, onClick = {})
            }
        }

        rule.onNodeWithContentDescription(detailText(R.string.book_detail_chipedit_add_genres))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        rule.onNodeWithContentDescription(detailText(R.string.book_detail_chipedit_add_tags))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    // TC-64, TC-65
    @Test
    fun issue101_ac2_disabledChipIsShownButDoesNothing() {
        var clicks = 0
        val enabled = mutableStateOf(false)
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                AddChip(MetadataListKind.Genres, enabled = enabled.value, onClick = { clicks++ })
            }
        }

        rule.onNodeWithTag(ChipEditTestTags.ADD_GENRES).assertIsDisplayed().assertIsNotEnabled().performClick()
        rule.waitForIdle()
        assertEquals(0, clicks)

        rule.runOnIdle { enabled.value = true }
        rule.onNodeWithTag(ChipEditTestTags.ADD_GENRES).performClick()
        rule.waitForIdle()
        assertEquals(1, clicks)
    }

    // TC-65
    @Test
    fun issue101_ac1_enabledChipInvokesOnClick() {
        var clicks = 0
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                AddChip(MetadataListKind.Tags, enabled = true, onClick = { clicks++ })
            }
        }

        rule.onNodeWithTag(ChipEditTestTags.ADD_TAGS).performClick()
        rule.waitForIdle()
        assertEquals(1, clicks)
    }
}
