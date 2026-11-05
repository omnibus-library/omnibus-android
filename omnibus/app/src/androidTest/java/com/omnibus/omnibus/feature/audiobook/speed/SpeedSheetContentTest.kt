package com.omnibus.omnibus.feature.audiobook.speed

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Locale

/** Callbacks, gestures, semantics and locale formatting. SpeedSheetScreenshotTest covers how the sheet looks. */
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

    // #168 TC-77: R2: the rate is in the locale's digits
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

        rule.runOnIdle { german = true }
        rule.waitForIdle()

        rule.onNodeWithTag(SpeedSheetTags.HEADER_RATE).assertTextEquals("0,75×")
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

        // The swipe stays off the window's edges: a full-width slider's own edge is the system back-gesture zone.
        rule.onNodeWithTag(SpeedSheetTags.SLIDER).performTouchInput {
            swipe(
                start = Offset(width * 0.25f, centerY),
                end = Offset(width * 0.75f, centerY),
            )
        }
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
