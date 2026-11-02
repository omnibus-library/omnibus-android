package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.ui.graphics.Color
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.ui.theme.OmnibusPalette

/** The colours of the reader's chrome: its ink, the labels' ink, and the plate and border behind controls. */
data class ReaderChromeColors(
    val ink: Color,
    val labelInk: Color,
    val plate: Color,
    val border: Color,
)

/**
 * The chrome colours for [theme]. They come from the reading theme and never from `MaterialTheme`,
 * so the screen's theme wrapper and app-theme sheets can't leak into the chrome (AC4). The three
 * literal plates are reader-only tones from the design's chrome table.
 */
fun readerChromeColors(theme: ReaderTheme): ReaderChromeColors {
    val ink = readerSwatchInk(theme)
    val plate = when (theme) {
        ReaderTheme.Light -> OmnibusPalette.Light.bg1
        ReaderTheme.Sepia -> Color(0xFFF6F0E2)
        ReaderTheme.Dark -> Color(0xFF2E2B27)
        ReaderTheme.Black -> Color(0xFF1A1816)
    }
    return ReaderChromeColors(ink, ink.copy(alpha = 0.5f), plate, ink.copy(alpha = 0.12f))
}

/**
 * The ink for text and lines drawn on [theme]'s page: the chrome ink, and the ink of the settings
 * sheet's page swatches. Light pages take the light palette's ink, dark ones the Atrium ink.
 */
fun readerSwatchInk(theme: ReaderTheme): Color =
    if (theme.isLight) OmnibusPalette.Light.ink0 else OmnibusPalette.Atrium.ink0

/**
 * The colour of the sync offer's "Go" action on [theme]'s plate. The accent is only about 1.8:1 on
 * the light plates, so they use the chrome ink, and the dark plates use the accent (6.9:1 and 8.6:1).
 */
fun readerSyncOfferActionColor(theme: ReaderTheme): Color =
    if (theme.isLight) readerChromeColors(theme).ink else OmnibusPalette.Atrium.accent
