package com.omnibus.omnibus.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Rule
import org.junit.Test

class ReaderAvatarTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-36
    @Test
    fun tc36_withoutAPictureItShowsTheInitialOnADiscOfTheGivenSize() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box { ReaderAvatar(userId = 3, name = "maren", hasAvatar = false, size = 32.dp, modifier = Modifier.testTag("avatar")) }
            }
        }

        rule.onNodeWithTag("avatar").assertWidthIsEqualTo(32.dp).assertHeightIsEqualTo(32.dp)
        rule.onNodeWithText("M", useUnmergedTree = true).assertIsDisplayed()
    }

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
