package com.omnibus.omnibus.feature.bookdetail.finish

import androidx.compose.ui.graphics.Color
import com.omnibus.omnibus.ui.theme.Oklch

/** The cream pieces of the burst. */
internal val BurstCream = Color(0xFFF3EBDD)

/** The gold pieces of the burst. */
internal val BurstGold = Color(0xFFC9A15B)

/** The card's two gradient stops for a book of [tone], top-start then bottom-end (iOS `Palette.finishGround`). */
internal fun finishGround(tone: Oklch, lightGround: Boolean): List<Color> = if (lightGround) {
    listOf(Oklch(0.62, tone.c, tone.h, alpha = 0.16).toColor(), Oklch(0.62, tone.c, tone.h, alpha = 0.28).toColor())
} else {
    listOf(Oklch(0.62, tone.c, tone.h, alpha = 0.30).toColor(), Oklch(0.30, tone.c, tone.h, alpha = 0.55).toColor())
}
