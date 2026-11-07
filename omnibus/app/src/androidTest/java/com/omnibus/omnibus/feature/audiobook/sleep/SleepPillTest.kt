package com.omnibus.omnibus.feature.audiobook.sleep

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerOption
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerState
import com.omnibus.omnibus.feature.audiobook.player.PlayerActions
import com.omnibus.omnibus.feature.audiobook.player.fullUiState
import com.omnibus.omnibus.feature.audiobook.player.setPlayer
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Rule
import org.junit.Test

/** The moon pill: its one merged description, and whether the top bar shows it (R13, R14, R15). */
class SleepPillTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-40, R13, R14: one merged, non-interactive node speaks the clock
    @Test
    fun issue169_tc40_pillSpeaksOneMergedDescription() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                SleepPill(SleepTimerState(SleepTimerOption.After(15), 872.0))
            }
        }
        rule.onAllNodesWithTag(SleepTestTags.PILL_MOON, useUnmergedTree = true).assertCountEquals(1)
        rule.onNodeWithTag(SleepTestTags.PILL).assertContentDescriptionEquals("Sleep timer, 14:32 remaining")
        rule.onNodeWithTag(SleepTestTags.PILL).assertHasNoClickAction()
        rule.onNodeWithTag(SleepTestTags.PILL).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
        rule.onNodeWithTag(SleepTestTags.PILL).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
    }

    // TC-41, R13, R15: the top bar shows the pill while the timer has time left, and not after
    @Test
    fun issue169_tc41_theTopBarShowsThePillOnlyWhileTheTimerHasTimeLeft() {
        val ui = mutableStateOf(fullUiState(sleepTimer = SleepTimerState(SleepTimerOption.After(10), 600.0)))
        val actions = mutableStateOf(
            PlayerActions(
                onOpenSleep = {},
                sleepPill = { ui.value.sleepTimer?.let { SleepPill(it) } },
            ),
        )
        rule.setPlayer(uiState = ui, actions = actions)
        rule.onNodeWithTag(SleepTestTags.PILL).assertIsDisplayed()

        rule.runOnIdle { ui.value = fullUiState(sleepTimer = SleepTimerState(SleepTimerOption.After(10), 0.0)) }
        rule.onAllNodesWithTag(SleepTestTags.PILL).assertCountEquals(0)

        rule.runOnIdle { ui.value = fullUiState(sleepTimer = null) }
        rule.onAllNodesWithTag(SleepTestTags.PILL).assertCountEquals(0)
    }
}
