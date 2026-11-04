package com.omnibus.omnibus.feature.audiobook.speed

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class SpeedSheetContentTest {

    @get:Rule
    val rule = createComposeRule()

    // #168 TC-75: R1, R2: the header is the sheet's title, marked as a heading
    @Test
    fun headerShowsTitleAsHeading() {
        rule.setContent {
            LocalizedSpeedSheetContent(locale = Locale.US, uiState = SpeedUIState.forRate(1.0))
        }

        rule.onNodeWithTag(SpeedSheetTags.TITLE)
            .assertTextEquals("Playback speed")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    // #168 TC-76: R1: the body scrolls when the window is shorter than the sheet, so the "+" button stays reachable
    @Test
    fun bodyScrollsWhenWindowIsShort() {
        rule.setContent {
            LocalizedSpeedSheetContent(
                locale = Locale.US,
                uiState = SpeedUIState.forRate(1.0),
                window = Modifier.fillMaxWidth().fillMaxHeight(0.25f),
            )
        }

        val body = rule.onNodeWithTag(SpeedSheetTags.BODY)
        body.assert(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy))
        val range = body.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("the body must have something to scroll", range.maxValue() > 0f)

        rule.onNodeWithTag(SpeedSheetTags.FASTER).performScrollTo().assertIsDisplayed()
    }

    // #168 TC-77: R2: the rate is in the locale's digits and sits on the title's row
    @Test
    fun headerShowsCurrentRateInLocaleDigits() {
        var german by mutableStateOf(false)
        rule.setContent {
            LocalizedSpeedSheetContent(
                locale = if (german) Locale.GERMANY else Locale.US,
                uiState = SpeedUIState.forRate(if (german) 0.75 else 1.25),
            )
        }

        rule.onNodeWithTag(SpeedSheetTags.HEADER_RATE).assertTextEquals("1.25×")
        assertSameRow()

        rule.runOnIdle { german = true }
        rule.waitForIdle()

        rule.onNodeWithTag(SpeedSheetTags.HEADER_RATE).assertTextEquals("0,75×")
        assertSameRow()
    }

    /** The header rate's vertical centre lies within the title's top and bottom, so both share one row. */
    private fun assertSameRow() {
        val title = rule.onNodeWithTag(SpeedSheetTags.TITLE).fetchSemanticsNode().boundsInRoot
        val rate = rule.onNodeWithTag(SpeedSheetTags.HEADER_RATE).fetchSemanticsNode().boundsInRoot
        val centre = (rate.top + rate.bottom) / 2
        assertTrue("the rate centre $centre lies in the title row ${title.top}..${title.bottom}", centre >= title.top && centre <= title.bottom)
    }

    // #168 TC-78: R3: eight presets in order, two rows of four, the first four sharing a row
    @Test
    fun eightPresetsInTwoRowsOfFour() {
        rule.setContent {
            LocalizedSpeedSheetContent(locale = Locale.US, uiState = SpeedUIState.forRate(1.0))
        }
        val labels = listOf("0.5×", "0.8×", "1.0×", "1.1×", "1.2×", "1.5×", "1.8×", "2.0×")
        labels.forEachIndexed { index, label ->
            rule.onNodeWithTag(SpeedSheetTags.preset(index)).assertTextEquals(label)
        }

        val bounds = labels.indices.map { rule.onNodeWithTag(SpeedSheetTags.preset(it)).fetchSemanticsNode().boundsInRoot }
        for (column in 1..3) {
            assertEquals(bounds[0].top, bounds[column].top)
            assertTrue(bounds[column].left > bounds[column - 1].left)
        }
        for (column in 4..7) {
            assertTrue(bounds[column].top > bounds[0].top)
            assertEquals(bounds[column - 4].left, bounds[column].left)
        }
    }

    // #168 TC-79: R3: only the current preset is selected, and only it is filled
    @Test
    fun selectedPresetIsFilledAndOthersAreNot() {
        rule.setContent {
            LocalizedSpeedSheetContent(locale = Locale.US, uiState = SpeedUIState.forRate(1.5))
        }

        rule.onNodeWithTag(SpeedSheetTags.preset(5)).assertIsSelected()
        (0..7).filter { it != 5 }.forEach { rule.onNodeWithTag(SpeedSheetTags.preset(it)).assertIsNotSelected() }

        // The fill is read from the pixels, not from a colour: the selected cell differs from an unselected one
        val selectedPixel = rule.onNodeWithTag(SpeedSheetTags.preset(5)).captureToImage().toPixelMap().let { map ->
            map[map.width / 8, map.height / 2]
        }
        val otherPixel = rule.onNodeWithTag(SpeedSheetTags.preset(4)).captureToImage().toPixelMap().let { map ->
            map[map.width / 8, map.height / 2]
        }
        assertNotEquals(selectedPixel, otherPixel)
    }

    // #168 TC-80: R3, R10: a rate between presets selects no preset
    @Test
    fun noPresetSelectedBetweenPresets() {
        var rate by mutableStateOf(1.25)
        rule.setContent {
            LocalizedSpeedSheetContent(locale = Locale.US, uiState = SpeedUIState.forRate(rate))
        }

        (0..7).forEach { rule.onNodeWithTag(SpeedSheetTags.preset(it)).assertIsNotSelected() }

        rule.runOnIdle { rate = 1.33 }
        rule.waitForIdle()

        (0..7).forEach { rule.onNodeWithTag(SpeedSheetTags.preset(it)).assertIsNotSelected() }
    }

    // #168 TC-81: R4: every preset reports its own value per tap, the selected one included
    @Test
    fun tappingAPresetReportsItsValue() {
        val taps = mutableListOf<Double>()
        rule.setContent {
            LocalizedSpeedSheetContent(
                locale = Locale.US,
                uiState = SpeedUIState.forRate(1.0),
                onPresetChosen = { taps += it },
            )
        }

        SPEED_PRESETS.forEachIndexed { index, _ -> rule.onNodeWithTag(SpeedSheetTags.preset(index)).performClick() }
        rule.onNodeWithTag(SpeedSheetTags.preset(2)).performClick()

        assertEquals(SPEED_PRESETS + 1.0, taps)
    }

    // #168 TC-82: R5: the fine-tune label, the range, and the slider's range with 49 steps
    @Test
    fun fineTuneRowAndSliderRange() {
        rule.setContent {
            LocalizedSpeedSheetContent(locale = Locale.US, uiState = SpeedUIState.forRate(1.0))
        }

        rule.onNodeWithTag(SpeedSheetTags.FINE_TUNE).assertTextEquals("Fine-tune")
        rule.onNodeWithTag(SpeedSheetTags.RANGE).assertTextEquals("0.5× — 3.0×")

        val info = rule.onNodeWithTag(SpeedSheetTags.SLIDER).fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(0.5f, info.range.start, 0f)
        assertEquals(3.0f, info.range.endInclusive, 0f)
        assertEquals(49, info.steps)
    }

    // #168 TC-83: R6: a swipe and a track tap each move the slider live, then release it exactly once, last
    @Test
    fun sliderGestureReportsMoveThenRelease() {
        val log = mutableListOf<String>()
        var rate by mutableStateOf(1.0)
        rule.setContent {
            LocalizedSpeedSheetContent(
                locale = Locale.US,
                uiState = SpeedUIState.forRate(rate),
                onSliderMoved = {
                    log += "moved:$it"
                    rate = it.toDouble()
                },
                onSliderReleased = { log += "released" },
            )
        }

        rule.onNodeWithTag(SpeedSheetTags.SLIDER).performTouchInput { swipeRight() }
        rule.waitForIdle()

        val swipeMoves = log.filter { it.startsWith("moved:") }
        assertTrue("a swipe moves the slider", swipeMoves.isNotEmpty())
        swipeMoves.forEach {
            val value = it.removePrefix("moved:").toFloat()
            assertTrue("$value is in range", value >= 0.5f && value <= 3.0f)
        }
        assertEquals(1, log.count { it == "released" })
        assertEquals("released", log.last())

        log.clear()
        rule.onNodeWithTag(SpeedSheetTags.SLIDER).performTouchInput {
            click(centerRight - Offset(width * 0.1f, 0f))
        }
        rule.waitForIdle()

        assertTrue("a track tap moves the slider", log.any { it.startsWith("moved:") })
        assertEquals(1, log.count { it == "released" })
        assertEquals("released", log.last())
    }

    // #168 TC-84: R7: the step caption, the step buttons, and each button calls its own callback once
    @Test
    fun stepButtonsAndCaption() {
        var slower = 0
        var faster = 0
        rule.setContent {
            LocalizedSpeedSheetContent(
                locale = Locale.US,
                uiState = SpeedUIState.forRate(1.25),
                onSlower = { slower++ },
                onFaster = { faster++ },
            )
        }

        rule.onNodeWithTag(SpeedSheetTags.STEP_RATE).assertTextEquals("1.25×")
        rule.onNodeWithTag(SpeedSheetTags.STEP_CAPTION).assertTextEquals("0.05 steps")
        rule.onNodeWithTag(SpeedSheetTags.SLOWER).performClick()
        rule.onNodeWithTag(SpeedSheetTags.FASTER).performClick()

        assertEquals(1, slower)
        assertEquals(1, faster)
    }

    // #168 TC-85: R7, R8: a step button at its bound is disabled and does not respond; both are enabled mid-range
    @Test
    fun stepButtonsDisableAtBounds() {
        var rate by mutableStateOf(1.0)
        var slower = 0
        var faster = 0
        rule.setContent {
            LocalizedSpeedSheetContent(
                locale = Locale.US,
                uiState = SpeedUIState.forRate(rate),
                onSlower = { slower++ },
                onFaster = { faster++ },
            )
        }

        rule.onNodeWithTag(SpeedSheetTags.SLOWER).assertIsEnabled()
        rule.onNodeWithTag(SpeedSheetTags.FASTER).assertIsEnabled()

        rule.runOnIdle { rate = 0.5 }
        rule.waitForIdle()
        rule.onNodeWithTag(SpeedSheetTags.SLOWER).assertIsNotEnabled()
        rule.onNodeWithTag(SpeedSheetTags.SLOWER).performTouchInput { click() }
        assertEquals(0, slower)

        rule.runOnIdle { rate = 3.0 }
        rule.waitForIdle()
        rule.onNodeWithTag(SpeedSheetTags.FASTER).assertIsNotEnabled()
        rule.onNodeWithTag(SpeedSheetTags.FASTER).performTouchInput { click() }
        assertEquals(0, faster)
    }
}
