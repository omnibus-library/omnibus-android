package com.omnibus.omnibus.feature.libraries.remove

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RemoveLibraryButtonTest {

    @get:Rule
    val rule = createComposeRule()

    /** Shows the button with [onClick] as its callback. */
    private fun show(onClick: () -> Unit) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                RemoveLibraryButton(onClick = onClick)
            }
        }
    }

    // TC-21 (R1, R13)
    @Test
    fun issue373_tc21_showsRemoveLibrary() {
        show(onClick = {})

        rule.onNodeWithText("Remove library").assertIsDisplayed()
    }

    @Test
    fun issue373_tc21_hasTheButtonRole() {
        show(onClick = {})

        rule.onNodeWithText("Remove library")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun issue373_tc21_isAtLeast48dpTall() {
        show(onClick = {})

        rule.onNodeWithText("Remove library").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun issue373_tc21_tapFiresOnClickOnce() {
        var clicks = 0
        show(onClick = { clicks++ })

        rule.onNodeWithText("Remove library").performClick()
        rule.waitForIdle()

        assertEquals(1, clicks)
    }
}
