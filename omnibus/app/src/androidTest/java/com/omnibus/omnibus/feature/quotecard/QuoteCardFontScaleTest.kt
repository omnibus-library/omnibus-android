package com.omnibus.omnibus.feature.quotecard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.omnibus.omnibus.ui.theme.Oklch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private const val PASSAGE = "It was the best of times, it was the worst of times, it was the age of wisdom."

class QuoteCardFontScaleTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun passage_keepsItsLineHeightAtAFontScaleOfTwo() {
        var cardWidthPx = 0f
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2f)) {
                Box(Modifier.fillMaxWidth(0.9f).onSizeChanged { cardWidthPx = it.width.toFloat() }) {
                    QuoteCard(
                        passage = PASSAGE,
                        title = "A Tale of Two Cities",
                        author = "Charles Dickens",
                        fill = QuoteBackground.Book.fill(Oklch(0.5, 0.1, 30.0)),
                        typeface = QuoteTypeface.Serif,
                        size = QuoteSize.M,
                        bold = false,
                        italic = false,
                        shape = QuoteShape.Square,
                    )
                }
            }
        }

        val node = rule.onNodeWithText(PASSAGE).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action!!(results)
        // Under 90 characters the passage is set at 0.082 of the card width, with 1.55 em between lines, at any font scale.
        val expectedLineHeight = cardWidthPx * 0.082f * 1.55f
        // Each line's height is rounded up to a whole pixel.
        val lines = results.single().lineCount
        assertEquals(lines * expectedLineHeight, node.size.height.toFloat(), lines + 1f)
    }
}
