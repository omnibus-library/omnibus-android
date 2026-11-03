package com.omnibus.omnibus.feature.bookdetail.actionbar

import androidx.compose.ui.unit.dp

/** Where the solid fill ends and where its soft edge has faded out, as fractions of the pill's width. */
internal data class FillStops(val solidEnd: Float, val fadeEnd: Float)

/** The soft edge's half width on each side of the fill line, as iOS `BarCTAStyle`. */
internal val ActionBarSoftEdge = 36.dp

/**
 * The gradient stops for [fill] on a pill [widthPx] wide, with a soft edge [softPx] on each side of the fill line. Null
 * draws no fill. A NaN, zero or negative [fill] draws none; a [fill] of 1 or more covers the pill.
 */
internal fun fillStops(fill: Float, widthPx: Float, softPx: Float): FillStops? {
    if (fill.isNaN() || fill <= 0f) return null
    if (fill >= 1f) return FillStops(1f, 1f)
    val soft = if (widthPx > 0f && softPx > 0f && widthPx.isFinite() && softPx.isFinite()) softPx / widthPx else 0f
    return FillStops((fill - soft).coerceIn(0f, 1f), (fill + soft).coerceIn(0f, 1f))
}
