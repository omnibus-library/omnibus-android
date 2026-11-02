package com.omnibus.omnibus.feature.library.ui

import androidx.compose.ui.graphics.Color
import com.omnibus.omnibus.ui.theme.Oklch

/** The colours of one Continue card, all derived from its book's tone. */
data class ContinueCardColors(
    /** The top-leading end of the base gradient. */
    val baseStart: Color,
    /** The bottom-trailing end of the base gradient. */
    val baseEnd: Color,
    /** The additive bloom behind the cover. */
    val bloom: Color,
    /** The eyebrow and the progress bar fill. */
    val rule: Color,
)

/** A theme is light when its bg0 has OKLCH lightness above 0.5 (iOS ContinueHero). */
fun isLightGround(bg0: Color): Boolean = Oklch.fromSrgb(bg0).l > 0.5

/**
 * The card colours for a book [tone] on a light or dark theme. Only the base's starting lightness
 * changes between themes. The rule colour is the same in both, as on iOS.
 */
fun continueCardColors(tone: Oklch, lightTheme: Boolean): ContinueCardColors {
    val l0 = if (lightTheme) 0.93 else 0.24
    return ContinueCardColors(
        baseStart = Oklch(l0, tone.c * 0.6, tone.h).toColor(),
        baseEnd = Oklch(l0 * 0.78, tone.c * 0.54, tone.h).toColor(),
        bloom = Oklch(0.62, tone.c * 0.9, tone.h, alpha = 0.40).toColor(),
        rule = Oklch(0.80, tone.c * 0.95, tone.h).toColor(),
    )
}
