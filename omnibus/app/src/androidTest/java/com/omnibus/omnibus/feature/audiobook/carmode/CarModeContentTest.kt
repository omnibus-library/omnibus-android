package com.omnibus.omnibus.feature.audiobook.carmode

import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.feature.audiobook.player.textOf
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Callback counts and the state of [CarModeContent], so tests can change the state between frames. */
private class CarInputs(initial: CarModeUIState = readyState()) {
    var state by mutableStateOf(initial)
    var dismissed = 0
    var back = 0
    var forward = 0
    var toggles = 0
    var previous = 0
    var next = 0
}

private fun readyState() = CarModeUIState(
    title = "004",
    bookSecondsLeftAtRate = 20 * 3600.0 + 25 * 60.0,
    isPlaying = false,
    canPreviousChapter = true,
    canNextChapter = true,
)

private fun ComposeContentTestRule.setCarMode(inputs: CarInputs) {
    setContent {
        OmnibusTheme(darkTheme = true) {
            CarModeContent(
                uiState = inputs.state,
                onDismiss = { inputs.dismissed++ },
                onSkipBack = { inputs.back++ },
                onSkipForward = { inputs.forward++ },
                onPlayPauseToggled = { inputs.toggles++ },
                onPreviousChapter = { inputs.previous++ },
                onNextChapter = { inputs.next++ },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Text, callbacks, semantics and keep-screen-on. CarModeScreenshotTest covers how the screen looks. */
class CarModeContentTest {

    @get:Rule
    val rule = createComposeRule()

    // P-CAR-AC1, P-CAR-2: the chapter and the time left
    @Test
    fun showsChapterTitleAndTimeLeft() {
        rule.setCarMode(CarInputs())

        assertEquals("004", rule.onNodeWithTag(CarModeTestTags.TITLE).textOf())
        assertEquals("20h 25m left", rule.onNodeWithTag(CarModeTestTags.TIME_LEFT).textOf())
    }

    // P-CAR-AC2, AC5: each tile is labelled, and a press runs its action
    @Test
    fun everyTileHasALabelAndRunsItsAction() {
        val inputs = CarInputs()
        rule.setCarMode(inputs)

        rule.onNodeWithContentDescription("Back 15 seconds").performClick()
        rule.onNodeWithContentDescription("Forward 30 seconds").performClick()
        rule.onNodeWithContentDescription("Play").performClick()
        rule.onNodeWithContentDescription("Previous chapter").performClick()
        rule.onNodeWithContentDescription("Next chapter").performClick()
        rule.onNodeWithContentDescription("Leave car mode").performClick()
        rule.waitForIdle()

        assertEquals(1, inputs.back)
        assertEquals(1, inputs.forward)
        assertEquals(1, inputs.toggles)
        assertEquals(1, inputs.previous)
        assertEquals(1, inputs.next)
        assertEquals(1, inputs.dismissed)
    }

    // P-CAR-AC5: the big tile says what a press will do
    @Test
    fun playTileLabelFollowsPlayState() {
        val inputs = CarInputs()
        rule.setCarMode(inputs)
        rule.onNodeWithContentDescription("Play").assertIsEnabled()

        rule.runOnIdle { inputs.state = inputs.state.copy(isPlaying = true) }
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Pause").performClick()
        rule.waitForIdle()
        assertEquals(1, inputs.toggles)
    }

    // P-CAR-3 edge case: no chapters
    @Test
    fun chapterTilesAreDisabledWithoutChapters() {
        val inputs = CarInputs(readyState().copy(canPreviousChapter = false, canNextChapter = false))
        rule.setCarMode(inputs)

        rule.onNodeWithTag(CarModeTestTags.PREVIOUS).assertIsNotEnabled()
        rule.onNodeWithTag(CarModeTestTags.NEXT).assertIsNotEnabled()
        rule.onNodeWithTag(CarModeTestTags.PREVIOUS).performClick()
        rule.onNodeWithTag(CarModeTestTags.NEXT).performClick()
        rule.onNodeWithTag(CarModeTestTags.BACK_15).assertIsEnabled()
        rule.onNodeWithTag(CarModeTestTags.FORWARD_30).assertIsEnabled()
        rule.onNodeWithTag(CarModeTestTags.PLAY_PAUSE).assertIsEnabled()
        rule.waitForIdle()

        assertEquals(0, inputs.previous)
        assertEquals(0, inputs.next)
    }

    // P-CAR-AC3, P-CAR-5, AND-UI-3: the screen stays on only while car mode is composed
    @Test
    fun keepsTheScreenOnWhileComposed() {
        lateinit var view: View
        var open by mutableStateOf(true)
        rule.setContent {
            view = LocalView.current
            OmnibusTheme(darkTheme = true) {
                if (open) {
                    CarModeContent(
                        uiState = readyState(),
                        onDismiss = {},
                        onSkipBack = {},
                        onSkipForward = {},
                        onPlayPauseToggled = {},
                        onPreviousChapter = {},
                        onNextChapter = {},
                    )
                }
            }
        }
        rule.waitForIdle()
        assertTrue("the screen should stay on while car mode is open", view.keepScreenOn)

        rule.runOnIdle { open = false }
        rule.waitForIdle()
        assertFalse("the screen may sleep again after car mode", view.keepScreenOn)
    }
}
