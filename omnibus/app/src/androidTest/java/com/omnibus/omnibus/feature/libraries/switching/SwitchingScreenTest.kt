package com.omnibus.omnibus.feature.libraries.switching

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.data.libraries.Library
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/** The switching screen's copy, stages, buttons, back handling and TalkBack semantics (#369). Relative asserts only. */
class SwitchingScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val home = library("home", "Home", isActive = true)
    private val work = library("work", "Work", isActive = false)

    private fun pressBack() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
    }

    @Test
    fun issue369_tc24_eachStageShowsItsCopy() {
        val state = mutableStateOf(SwitchingUIState(stage = SwitchingStage.Syncing, current = home, target = work))
        rule.setContent {
            OmnibusTheme {
                SwitchingScreenContent(uiState = state.value, onStay = {}, onSwitchAnyway = {})
            }
        }

        rule.onNodeWithText("Home").assertIsDisplayed()
        rule.onNodeWithTag(SwitchingTestTags.STATUS).assertTextEquals("Syncing Home")

        rule.runOnIdle { state.value = SwitchingUIState(stage = SwitchingStage.Opening, current = home, target = work) }

        rule.onNodeWithText("Work").assertIsDisplayed()
        rule.onNodeWithTag(SwitchingTestTags.STATUS).assertTextEquals("Opening Work")

        rule.runOnIdle {
            state.value = SwitchingUIState(stage = SwitchingStage.SyncFailed, current = home, target = work)
        }

        rule.onNodeWithTag(SwitchingTestTags.FAILURE_TITLE).assertTextEquals("Couldn't sync Home")
        rule.onNodeWithText("Some changes on this device haven't reached Home. If you switch now, they may be lost.")
            .assertIsDisplayed()
        rule.onNodeWithTag(SwitchingTestTags.STAY).assertTextEquals("Stay on Home")
        rule.onNodeWithTag(SwitchingTestTags.SWITCH_ANYWAY).assertTextEquals("Switch anyway")
    }

    @Test
    fun issue369_tc25_buttonsAndBackFollowTheRules() {
        var stays = 0
        var switches = 0
        val state = mutableStateOf(SwitchingUIState(stage = SwitchingStage.Syncing, current = home, target = work))
        rule.setContent {
            OmnibusTheme {
                SwitchingScreenContent(
                    uiState = state.value,
                    onStay = { stays++ },
                    onSwitchAnyway = { switches++ },
                )
            }
        }

        pressBack()
        rule.runOnIdle { state.value = SwitchingUIState(stage = SwitchingStage.Lifting, current = home, target = work) }
        pressBack()
        rule.runOnIdle { state.value = SwitchingUIState(stage = SwitchingStage.Opening, current = home, target = work) }
        pressBack()

        assertEquals(0, stays)
        assertEquals(0, switches)
        assertFalse(rule.activity.isFinishing)

        rule.runOnIdle {
            state.value = SwitchingUIState(stage = SwitchingStage.SyncFailed, current = home, target = work)
        }
        rule.onNodeWithTag(SwitchingTestTags.SWITCH_ANYWAY).performClick()
        rule.onNodeWithTag(SwitchingTestTags.STAY).performClick()
        assertEquals(1, switches)
        assertEquals(1, stays)

        pressBack()

        assertEquals(2, stays)
        assertFalse(rule.activity.isFinishing)
    }

    @Test
    fun issue369_tc26_talkBackSeesTheRightNodes() {
        rule.setContent {
            OmnibusTheme {
                SwitchingScreenContent(
                    uiState = SwitchingUIState(stage = SwitchingStage.SyncFailed, current = home, target = work),
                    onStay = {},
                    onSwitchAnyway = {},
                )
            }
        }

        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Switching to Work"))
            .assertIsDisplayed()
        rule.onNodeWithTag(SwitchingTestTags.FAILURE_TITLE)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assertIsFocused()
        rule.onNodeWithTag(SwitchingTestTags.STAY).assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag(SwitchingTestTags.SWITCH_ANYWAY).assertHeightIsAtLeast(48.dp)
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)).assertCountEquals(0)
    }
}

/** A library with fixed fields, so the screen under test shows the same copy every run. */
private fun library(id: String, name: String, isActive: Boolean): Library = Library(
    id = id,
    address = "https://$id.example",
    name = name,
    username = null,
    addedAt = 0,
    lastUsedAt = 0,
    isActive = isActive,
)
