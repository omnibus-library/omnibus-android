package com.omnibus.omnibus.feature.audiobook.speed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class SpeedSheetAccessibilityTest {

    @get:Rule
    val rule = createComposeRule()

    // #168 TC-86: R8, P-A11Y-1: the presets are radio buttons in one selectable group, read by their label and state
    @Test
    fun presetsAreSelectableRadioButtonsInAGroup() {
        rule.setContent {
            LocalizedSpeedSheetContent(locale = Locale.US, uiState = SpeedUIState.forRate(1.5))
        }

        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup), useUnmergedTree = true)
            .assertExists()
        SPEED_PRESETS.indices.forEach { index ->
            rule.onNodeWithTag(SpeedSheetTags.preset(index))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        }
        rule.onNodeWithTag(SpeedSheetTags.preset(5)).assertTextEquals("1.5×").assertIsSelected()
        SPEED_PRESETS.indices.filter { it != 5 }.forEach { index ->
            rule.onNodeWithTag(SpeedSheetTags.preset(index)).assertIsNotSelected()
        }
    }

    // #168 TC-87: R8: the slider is named "Fine-tune speed" and speaks the current rate, and follows the rate
    @Test
    fun sliderHasDescriptionAndStateDescription() {
        var state by mutableStateOf(SpeedUIState.forRate(1.25))
        rule.setContent {
            LocalizedSpeedSheetContent(locale = Locale.US, uiState = state)
        }

        rule.onNodeWithTag(SpeedSheetTags.SLIDER)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Fine-tune speed")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "1.25×"))

        rule.runOnIdle { state = SpeedUIState.forRate(1.3) }
        rule.waitForIdle()

        rule.onNodeWithTag(SpeedSheetTags.SLIDER)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "1.30×"))
    }

    // #168 TC-88: R6, R8: a TalkBack adjust moves the slider one step and persists it; an unchanged value does nothing
    @Test
    fun talkBackSetProgressMovesAndPersists() {
        val log = mutableListOf<String>()
        var rate by mutableStateOf(1.25)
        rule.setContent {
            LocalizedSpeedSheetContent(
                locale = Locale.US,
                uiState = SpeedUIState.forRate(rate),
                onSliderMoved = {
                    log += "moved:$it"
                    rate = snapRate(it.toDouble())
                },
                onSliderReleased = { log += "released" },
            )
        }

        rule.onNodeWithTag(SpeedSheetTags.SLIDER).performSemanticsAction(SemanticsActions.SetProgress) { it(1.30f) }
        rule.waitForIdle()

        assertEquals(2, log.size)
        assertTrue("the moved value is 1.30, was ${log[0]}", log[0].startsWith("moved:"))
        assertEquals(1.30f, log[0].removePrefix("moved:").toFloat(), 0.001f)
        assertEquals("released", log[1])

        // The slider now shows 1.30, so asking for 1.30 again is unchanged and logs nothing
        log.clear()
        rule.onNodeWithTag(SpeedSheetTags.SLIDER).performSemanticsAction(SemanticsActions.SetProgress) { it(1.30f) }
        rule.waitForIdle()

        assertTrue("an unchanged value logs nothing, was $log", log.isEmpty())
    }

    // #168 TC-89: R8, P-A11Y-1: "−" and "+" are labelled buttons, not read as glyphs, dimmed at a bound, and large enough
    @Test
    fun stepButtonsAreLabelledButtonsWithMinimumTouchTarget() {
        var rate by mutableStateOf(1.0)
        rule.setContent {
            LocalizedSpeedSheetContent(locale = Locale.US, uiState = SpeedUIState.forRate(rate))
        }

        rule.onNodeWithTag(SpeedSheetTags.SLOWER)
            .assert(hasContentDescription("Slower"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
            .assertIsEnabled()
        rule.onNodeWithTag(SpeedSheetTags.FASTER)
            .assert(hasContentDescription("Faster"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
            .assertIsEnabled()

        // The visible glyphs are not part of the semantics tree
        rule.onNode(hasText("−"), useUnmergedTree = true).assertDoesNotExist()
        rule.onNode(hasText("+"), useUnmergedTree = true).assertDoesNotExist()

        rule.runOnIdle { rate = 0.5 }
        rule.waitForIdle()
        rule.onNodeWithTag(SpeedSheetTags.SLOWER).assertIsNotEnabled()

        rule.runOnIdle { rate = 3.0 }
        rule.waitForIdle()
        rule.onNodeWithTag(SpeedSheetTags.FASTER).assertIsNotEnabled()
    }
}
