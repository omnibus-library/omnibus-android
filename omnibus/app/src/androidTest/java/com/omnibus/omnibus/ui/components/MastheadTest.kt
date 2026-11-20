package com.omnibus.omnibus.ui.components

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MastheadTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun tc49_mastheadIsOneHeadingWithWordmarkAndTitle() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Masthead(title = "Library")
            }
        }

        rule.onNode(isHeading())
            .assertTextContains("Omnibus", substring = true)
            .assertTextContains("Library", substring = true)
    }

    @Test
    fun tc51_addButtonHasLabelAndInvokesCallbackOnClick() {
        var taps = 0
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                MastheadAddButton(onAddBooks = { taps++ })
            }
        }

        rule.onNodeWithContentDescription(ADD_LABEL).performClick()
        assertEquals(1, taps)
    }

    // TC-9
    @Test
    fun issue319_tc9_addButtonIsAtLeast48dp() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                MastheadAddButton(onAddBooks = {})
            }
        }

        rule.onNodeWithContentDescription(ADD_LABEL)
            .assertTouchWidthIsEqualTo(48.dp)
            .assertTouchHeightIsEqualTo(48.dp)
    }

    private companion object {
        const val ADD_LABEL = "Add books"
    }
}
