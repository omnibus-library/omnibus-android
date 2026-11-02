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
    val ink = if (theme.isLight) OmnibusPalette.Light.ink0 else OmnibusPalette.Atrium.ink0
    val plate = when (theme) {
        ReaderTheme.Light -> OmnibusPalette.Light.bg1
        ReaderTheme.Sepia -> Color(0xFFF6F0E2)
        ReaderTheme.Dark -> Color(0xFF2E2B27)
        ReaderTheme.Black -> Color(0xFF1A1816)
    }
    return ReaderChromeColors(ink, ink.copy(alpha = 0.5f), plate, ink.copy(alpha = 0.12f))
}
