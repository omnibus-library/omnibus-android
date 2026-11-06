package com.omnibus.omnibus.feature.quotecard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.data.quotecard.CoverPair
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class QuoteBackgroundSectionTest {

    @get:Rule
    val rule = createComposeRule()

    private val book = CoverIdentity(uuid = "b", title = "T", hasCover = false)
    private val pair = CoverPair(0xFF5D1F1C.toInt(), 0xFFC01A1A.toInt(), lightInk = true)
    private val kinds = mutableListOf<QuoteBackgroundKind>()
    private val chosen = mutableListOf<QuoteBackground>()
    private var customTaps = 0
    private var background by mutableStateOf<QuoteBackground>(QuoteBackground.Book)

    private fun setContent(coverPairs: List<CoverPair> = emptyList()) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                QuoteBackgroundSection(
                    background = background,
                    coverPairs = coverPairs,
                    book = book,
                    onKindChosen = { kinds += it },
                    onBackgroundChosen = { chosen += it; background = it },
                    activeStop = GradientStop.From,
                    onCustomChosen = { customTaps++ },
                    onColorChanged = {},
                    onStopFocused = {},
                    onDirectionRotated = {},
                )
            }
        }
    }

    @Test
    fun pill_namesTheCardsKindAndTheMenuChecksIt() {
        background = QuoteBackground.Preset(QuoteGradientPreset.Ember)
        setContent()

        rule.onNodeWithTag(QuoteCardTestTags.KIND_PILL).assertTextEquals("Gradient")
        rule.onNodeWithTag(QuoteCardTestTags.KIND_PILL).performClick()

        rule.onNodeWithTag(QuoteCardTestTags.kindItem(QuoteBackgroundKind.Gradient)).assertIsSelected()
        rule.onNodeWithTag(QuoteCardTestTags.kindItem(QuoteBackgroundKind.Solid)).assert(isNotSelected())
    }

    @Test
    fun choosingAKind_reportsItAndClosesTheMenu() {
        setContent()

        rule.onNodeWithTag(QuoteCardTestTags.KIND_PILL).performClick()
        rule.onNodeWithTag(QuoteCardTestTags.kindItem(QuoteBackgroundKind.Gradient)).performClick()

        assertEquals(listOf(QuoteBackgroundKind.Gradient), kinds)
        rule.onNodeWithTag(QuoteCardTestTags.KIND_MENU).assertDoesNotExist()
    }

    @Test
    fun swatches_areRadioButtonsAndTheCurrentOneIsSelected() {
        setContent()

        rule.onNodeWithContentDescription("Book").assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        rule.onNodeWithContentDescription("Moss").assert(isNotSelected()).performClick()

        assertEquals(listOf<QuoteBackground>(QuoteBackground.Wash(QuoteWash.Moss)), chosen)
        rule.onNodeWithContentDescription("Moss").assertIsSelected()
    }

    @Test
    fun gradientRow_showsCoverPairsAndTheDividerOnlyWhenThereAreCoverPairs() {
        background = QuoteBackground.Preset(QuoteGradientPreset.Ember)
        setContent(coverPairs = listOf(pair))

        rule.onNodeWithContentDescription("Cover gradient 1").assertExists()
        rule.onNodeWithTag(QuoteCardTestTags.DIVIDER).assertExists()
    }

    @Test
    fun gradientRow_hasNoDividerWithoutCoverPairs() {
        background = QuoteBackground.Preset(QuoteGradientPreset.Ember)
        setContent()

        rule.onNodeWithTag(QuoteCardTestTags.DIVIDER).assertDoesNotExist()
        rule.onNodeWithContentDescription("Ember").assertIsSelected()
    }

    @Test
    fun customSwatch_isNamedForItsRowAndReportsATap() {
        setContent()

        rule.onNodeWithContentDescription("Custom color").performClick()

        assertEquals(1, customTaps)
        rule.onNodeWithContentDescription("Custom gradient").assertDoesNotExist()
    }

    private fun isNotSelected() = SemanticsMatcher.expectValue(SemanticsProperties.Selected, false)
}
