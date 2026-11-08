package com.omnibus.omnibus.feature.quotecard

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.quotecard.CoverPair
import com.omnibus.omnibus.screenshot.Shot
import com.omnibus.omnibus.ui.components.CoverIdentity

/** Light and dark, tall enough for the whole sheet. */
@Preview(name = "Light", heightDp = 1600)
@Preview(name = "Dark", heightDp = 1600, uiMode = Configuration.UI_MODE_NIGHT_YES)
private annotation class SheetLightDark

private const val PASSAGE =
    "It is a truth universally acknowledged, that a single man in possession of a good fortune, " +
        "must be in want of a wife."

/** No art is requested, so the thumbnail is the generated plate. */
private val Book = CoverIdentity(
    uuid = "shot",
    title = "Pride and Prejudice",
    creator = "Jane Austen",
    accent = "oklch(0.50 0.12 30)",
    hasCover = false,
)

private val CoverPairs = listOf(
    CoverPair(0xFF5D1F1C.toInt(), 0xFFC01A1A.toInt(), lightInk = true),
    CoverPair(0xFFEFE4E6.toInt(), 0xFFD5C2C9.toInt(), lightInk = false),
)

@Composable
private fun SheetShot(state: QuoteCardUIState) {
    Shot {
        QuoteCardSheetContent(
            state = state,
            book = Book,
            onKindChosen = {},
            onBackgroundChosen = {},
            onCustomChosen = {},
            onColorChanged = {},
            onStopFocused = {},
            onDirectionRotated = {},
            onTypefaceChosen = {},
            onSizeChosen = {},
            onBoldToggled = {},
            onItalicToggled = {},
            onShapeChosen = {},
            onShare = {},
            onCopy = {},
            onDone = {},
        )
    }
}

/** The defaults: the book's colour, Serif, M, upright, Square. */
@PreviewTest
@SheetLightDark
@Composable
fun QuoteCardSheetSolid() {
    SheetShot(QuoteCardUIState(passage = PASSAGE))
}

/** The gradient row with two cover pairs ahead of the presets, Dusk chosen, Post, and Sans, Italic and L selected. */
@PreviewTest
@SheetLightDark
@Composable
fun QuoteCardSheetGradient() {
    SheetShot(
        QuoteCardUIState(
            passage = PASSAGE,
            background = QuoteBackground.Preset(QuoteGradientPreset.Dusk),
            typeface = QuoteTypeface.Sans,
            size = QuoteSize.L,
            italic = true,
            shape = QuoteShape.Post,
            coverPairs = CoverPairs,
        ),
    )
}

/** The two-stop editor open on a custom gradient: From active, 135 degrees. */
@PreviewTest
@SheetLightDark
@Composable
fun QuoteCardSheetCustomGradient() {
    SheetShot(
        QuoteCardUIState(
            passage = PASSAGE,
            background = QuoteBackground.CustomGradient(0xFF7A1F16.toInt(), 0xFFA24C16.toInt(), 135),
            coverPairs = CoverPairs,
        ),
    )
}
