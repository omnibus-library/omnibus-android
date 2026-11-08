package com.omnibus.omnibus.feature.quotecard

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class QuoteColorEditorTest {

    @get:Rule
    val rule = createComposeRule()

    private val colors = mutableListOf<Int>()
    private val stops = mutableListOf<GradientStop>()
    private var rotations = 0

    private fun setContent(background: QuoteBackground, activeStop: GradientStop = GradientStop.From) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                QuoteColorEditor(background, activeStop, colors::add, stops::add, { rotations++ })
            }
        }
    }

    @Test
    fun customSolid_showsHueSaturationAndLightnessSlidersWithTheirRanges() {
        setContent(QuoteBackground.CustomSolid(0xFF336699.toInt()))

        val hue = rule.onNodeWithTag(QuoteCardTestTags.slider("hue")).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        val saturation = rule.onNodeWithTag(QuoteCardTestTags.slider("saturation")).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        val lightness = rule.onNodeWithTag(QuoteCardTestTags.slider("lightness")).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(0f..360f, hue.range)
        assertEquals(0f..0.2f, saturation.range)
        assertEquals(0.1f..0.95f, lightness.range)
        rule.onNodeWithTag(QuoteCardTestTags.slider("hue")).assertContentDescriptionEquals("Hue")
        rule.onNodeWithTag(QuoteCardTestTags.well(GradientStop.From)).assertDoesNotExist()
    }

    @Test
    fun settingASliderThroughSemantics_reportsTheNewColourOfThatChannelOnly() {
        val original = 0xFF336699.toInt()
        setContent(QuoteBackground.CustomSolid(original))
        val lightnessBefore = pickerOf(original).lightness

        rule.onNodeWithTag(QuoteCardTestTags.slider("hue"))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(30f) }

        val reported = pickerOf(colors.single())
        assertEquals(30.0, reported.hue, 2.0)
        assertEquals(lightnessBefore, reported.lightness, 0.02)
    }

    @Test
    fun tappingTheEndOfATrack_setsThatSliderToItsMaximum() {
        setContent(QuoteBackground.CustomSolid(0xFF336699.toInt()))

        rule.onNodeWithTag(QuoteCardTestTags.slider("lightness")).performTouchInput { click(centerRight) }

        assertTrue("no colour reported", colors.isNotEmpty())
        assertEquals(0.95, pickerOf(colors.last()).lightness, 0.02)
    }

    @Test
    fun customGradient_showsBothWellsWithHexLabelsAndTheActiveOneSelected() {
        setContent(QuoteBackground.CustomGradient(0xFF7A1F16.toInt(), 0xFFA24C16.toInt(), 135), GradientStop.To)

        rule.onNodeWithTag(QuoteCardTestTags.well(GradientStop.From)).assertTextContains("#7A1F16", substring = true)
        rule.onNodeWithTag(QuoteCardTestTags.well(GradientStop.To)).assertTextContains("#A24C16", substring = true)
        rule.onNodeWithTag(QuoteCardTestTags.well(GradientStop.To)).assertIsSelected()
    }

    @Test
    fun tappingAWell_focusesThatStop() {
        setContent(QuoteBackground.CustomGradient(0xFF7A1F16.toInt(), 0xFFA24C16.toInt(), 135))

        rule.onNodeWithTag(QuoteCardTestTags.well(GradientStop.To)).performClick()

        assertEquals(listOf(GradientStop.To), stops)
    }

    @Test
    fun directionButton_announcesItsAngleAndReportsATap() {
        setContent(QuoteBackground.CustomGradient(0xFF7A1F16.toInt(), 0xFFA24C16.toInt(), 135))

        rule.onNodeWithTag(QuoteCardTestTags.DIRECTION).assertContentDescriptionEquals("Direction, 135 degrees").performClick()

        assertEquals(1, rotations)
    }
}
