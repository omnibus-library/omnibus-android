package com.omnibus.omnibus.feature.bookdetail.tabs

import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.feature.bookdetail.BookDetailTab

/** The bookmark ribbon's width. */
internal val RibbonWidth = 20.dp

/** The bookmark ribbon's height, including its notch. */
internal val RibbonHeight = 38.dp

/** Where the ribbon's notch apex sits, as a fraction of its height (iOS `RibbonShape`). */
internal const val RIBBON_NOTCH_FRACTION = 38f / 46f

/** The length of the ribbon's swing after a selection change. */
internal const val RIBBON_SWING_MILLIS = 900

/**
 * The horizontal centre of the ribbon above tab [index] (fractional while it slides), in the strip's pixels. The four
 * cells share the width inside [insetPx] on each side.
 */
internal fun ribbonCenterXPx(
    index: Float,
    stripWidthPx: Float,
    insetPx: Float,
    tabCount: Int = BookDetailTab.entries.size,
): Float {
    val cell = (stripWidthPx - 2 * insetPx) / tabCount
    return insetPx + cell * (index + 0.5f)
}

/**
 * The swing's rotation keyframes, as (time in ms, degrees): it dips 10 degrees, rebounds 4 and settles. [direction]
 * is +1 when the selection moved right, -1 when it moved left, mirroring the swing.
 */
internal fun ribbonSwingKeyframes(direction: Int): List<Pair<Int, Float>> = listOf(
    0 to 0f,
    200 to -10f * direction,
    520 to 4f * direction,
    900 to 0f,
)
