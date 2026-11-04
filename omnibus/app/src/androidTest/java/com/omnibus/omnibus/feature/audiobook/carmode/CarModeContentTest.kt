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
import com.omnibus.omnibus.feature.audiobook.player.LONG_CHAPTER_TITLE
import com.omnibus.omnibus.feature.audiobook.player.textLayout
import com.omnibus.omnibus.feature.audiobook.player.textOf
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

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

class CarModeContentTest {

    @get:Rule
    val rule = createComposeRule()

    private fun boundsOf(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    // P-CAR-AC1, P-CAR-2: the chapter and the time left
    @Test
    fun showsChapterTitleAndTimeLeft() {
        rule.setCarMode(CarInputs())

        assertEquals("004", rule.onNodeWithTag(CarModeTestTags.TITLE).textOf())
        assertEquals("20h 25m left", rule.onNodeWithTag(CarModeTestTags.TIME_LEFT).textOf())
        assertTrue(
            "the time left should sit under the title",
            boundsOf(CarModeTestTags.TIME_LEFT).top >= boundsOf(CarModeTestTags.TITLE).bottom - 1f,
        )
    }

    // P-CAR-2: up to two lines
    @Test
    fun longTitleStopsAtTwoLines() {
        rule.setCarMode(CarInputs(readyState().copy(title = LONG_CHAPTER_TITLE.repeat(3))))

        val layout = rule.onNodeWithTag(CarModeTestTags.TITLE).textLayout()
        assertEquals(2, layout.lineCount)
        assertTrue("the title should be cut off", layout.hasVisualOverflow)
    }

    // P-CAR-AC1, P-CAR-3: the five tiles, in the reference layout
    @Test
    fun tilesFollowTheReferenceLayout() {
        rule.setCarMode(CarInputs())

        val back = boundsOf(CarModeTestTags.BACK_15)
        val forward = boundsOf(CarModeTestTags.FORWARD_30)
        val play = boundsOf(CarModeTestTags.PLAY_PAUSE)
        val previous = boundsOf(CarModeTestTags.PREVIOUS)
        val next = boundsOf(CarModeTestTags.NEXT)
        val root = boundsOf(CarModeTestTags.ROOT)

        assertTrue("back 15 should sit left of forward 30", back.right <= forward.left + 1f)
        assertTrue("back 15 and forward 30 should share a row", abs(back.top - forward.top) < 1f)
        assertTrue("previous should sit left of next", previous.right <= next.left + 1f)
        assertTrue("previous and next should share a row", abs(previous.top - next.top) < 1f)
        assertTrue("play should sit under the skip row", play.top >= back.bottom - 1f)
        assertTrue("the chapter row should sit under play", previous.top >= play.bottom - 1f)
        assertTrue("play should be wider than a skip tile", play.width > 1.5f * back.width)
        assertTrue("play should be taller than a skip tile", play.height > back.height)
        assertTrue("play should be taller than a chapter tile", play.height > previous.height)
        assertTrue("play should be full width, as the rows are", abs(play.width - (forward.right - back.left)) < 1f)
        assertTrue("the tiles should be inside the screen", previous.bottom <= root.bottom + 1f && back.left >= root.left)
    }

    // P-CAR-3 edge case: the tiles scale with the screen
    @Test
    fun tilesFillTheLowerPartOfTheScreen() {
        rule.setCarMode(CarInputs())

        val root = boundsOf(CarModeTestTags.ROOT)
        val tiles = boundsOf(CarModeTestTags.TILES)
        assertTrue("the tiles should take a large share of the screen's height", tiles.height > root.height * 0.4f)
        assertTrue("the tiles should sit in the lower part", tiles.top > root.top + root.height * 0.25f)
        assertTrue("the tiles should reach most of the width", tiles.width > root.width * 0.8f)
        assertTrue(
            "the tiles should sit under the time left",
            tiles.top >= boundsOf(CarModeTestTags.TIME_LEFT).bottom - 1f,
        )
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
