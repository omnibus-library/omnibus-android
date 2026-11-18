package com.omnibus.omnibus.ui.theme

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.highlights.HighlightColor

private val TINTS: Map<HighlightColor, Color> = mapOf(
    HighlightColor.Amber to Oklch(0.78, 0.14, 75.0).toColor(),
    HighlightColor.Green to Oklch(0.78, 0.14, 150.0).toColor(),
    HighlightColor.Blue to Oklch(0.74, 0.12, 245.0).toColor(),
    HighlightColor.Rose to Oklch(0.74, 0.14, 10.0).toColor(),
    HighlightColor.Violet to Oklch(0.74, 0.12, 300.0).toColor(),
)

/** The ribbon fill for this colour. It is the same in light and dark themes. */
internal fun HighlightColor.tint(): Color = TINTS.getValue(this)

/** The spoken name of this colour, such as "Amber highlight". */
internal val HighlightColor.labelRes: Int
    @StringRes get() = when (this) {
        HighlightColor.Amber -> R.string.book_detail_highlights_color_amber
        HighlightColor.Green -> R.string.book_detail_highlights_color_green
        HighlightColor.Blue -> R.string.book_detail_highlights_color_blue
        HighlightColor.Rose -> R.string.book_detail_highlights_color_rose
        HighlightColor.Violet -> R.string.book_detail_highlights_color_violet
    }
