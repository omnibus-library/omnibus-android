package com.omnibus.omnibus.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Rule
import org.junit.Test

/** The fallback when a picture can't load. ReaderAvatarScreenshotTest covers how the initial looks. */
class ReaderAvatarTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-37: a picture that can't load (no app repository in a plain host) falls back to the initial
    @Test
    fun tc37_aPictureThatCannotLoadFallsBackToTheInitial() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box { ReaderAvatar(userId = 3, name = "Theo", hasAvatar = true, size = 40.dp) }
            }
        }

        rule.onNodeWithText("T", useUnmergedTree = true).assertIsDisplayed()
    }
}
