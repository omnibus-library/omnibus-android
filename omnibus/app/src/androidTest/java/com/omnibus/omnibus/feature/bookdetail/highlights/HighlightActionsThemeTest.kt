package com.omnibus.omnibus.feature.bookdetail.highlights

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.feature.bookdetail.menuPanelColor
import com.omnibus.omnibus.ui.theme.OmnibusPalette
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.contrastRatio
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** WCAG AA contrast for body text. */
private const val READABLE_CONTRAST = 4.5

class HighlightActionsThemeTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun issue327_lightTheme_labelsReadOnTheMenuPanel() {
        rule.setContent {
            OmnibusTheme(darkTheme = false) {
                HighlightRowActions(testRow(1, text = "A passage"), onQuoteCard = {}, onShare = {})
            }
        }
        rule.onNodeWithContentDescription("Highlight actions").performClick()

        val panel = rule.onNodeWithText("Share").menuPanelColor()
        val contrast = contrastRatio(panel, OmnibusPalette.Light.ink0)

        assertTrue("contrast was $contrast", contrast >= READABLE_CONTRAST)
    }
}
