package com.omnibus.omnibus.feature.quotecard

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class QuoteStyleControlsTest {

    @get:Rule
    val rule = createComposeRule()

    private val typefaces = mutableListOf<QuoteTypeface>()
    private val sizes = mutableListOf<QuoteSize>()
    private val shapes = mutableListOf<QuoteShape>()
    private var bolds = 0
    private var italics = 0

    private fun setContent(state: QuoteCardUIState) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Column {
                    QuoteTypeSection(state, typefaces::add, sizes::add, { bolds++ }, { italics++ })
                    QuoteShapeSection(state.shape, shapes::add)
                }
            }
        }
    }

    @Test
    fun typeface_isARadioGroupWithTheChosenFaceSelectedAndReportsAChoice() {
        setContent(QuoteCardUIState(typeface = QuoteTypeface.Sans))

        rule.onNodeWithText("Sans").assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        rule.onNodeWithText("Mono").performClick()

        assertEquals(listOf(QuoteTypeface.Mono), typefaces)
    }

    @Test
    fun size_selectsTheCurrentStepAndReportsAChoice() {
        setContent(QuoteCardUIState(size = QuoteSize.M))

        rule.onNodeWithText("M").assertIsSelected()
        rule.onNodeWithText("L").performClick()

        assertEquals(listOf(QuoteSize.L), sizes)
    }

    @Test
    fun boldAndItalic_areIndependentCheckboxesThatReportATap() {
        setContent(QuoteCardUIState(bold = true, italic = false))

        rule.onNodeWithTag(QuoteCardTestTags.BOLD).assertIsOn()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        rule.onNodeWithTag(QuoteCardTestTags.ITALIC).performClick()

        assertEquals(0, bolds)
        assertEquals(1, italics)
    }

    @Test
    fun shape_showsEachNameOverItsRatioAndReportsAChoice() {
        setContent(QuoteCardUIState(shape = QuoteShape.Square))

        listOf("Square" to "1:1", "Post" to "4:5", "Portrait" to "3:4", "Story" to "9:16").forEach { (name, ratio) ->
            rule.onNodeWithText(name).assertExists()
            rule.onNodeWithText(ratio).assertExists()
        }
        rule.onNodeWithTag(QuoteCardTestTags.shape(QuoteShape.Square)).assertIsSelected()
        rule.onNodeWithTag(QuoteCardTestTags.shape(QuoteShape.Story)).performClick()

        assertEquals(listOf(QuoteShape.Story), shapes)
    }
}
