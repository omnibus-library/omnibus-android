package com.omnibus.omnibus.feature.bookdetail.overview.series

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** The fan's dimensions and timings, in design dp and milliseconds (the Atrium Book Details boards S1 to S3). */
internal object SeriesFanLayout {
    const val CARD_WIDTH_DP = 104f
    const val COVER_HEIGHT_DP = 152f
    const val SPREAD_STEP_DP = 120f
    /** The widest gathered step, the design's (358 - 104) / 4 for its five-book fan, so neighbours always overlap. */
    const val GATHERED_MAX_STEP_DP = 63.5f
    const val GATHERED_HEIGHT_DP = 176f
    const val SPREAD_HEIGHT_DP = 236f
    const val RAISE_DP = 10f
    const val MAX_TURN_DEG = 5f
    const val TOTAL_TURN_DEG = 20f
    const val EDGE_DP = 16f
    const val ANIMATION_MILLIS = 620
    const val TEXT_FADE_MILLIS = 260
}

/** The fan's easing curve, the design's `cubic-bezier(.16,1,.3,1)`. */
internal val FanEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/**
 * Where one card sits.
 *
 * @property xDp The card's left edge, from the row's start.
 * @property yDp The card's vertical offset: -10 for this book when gathered, else 0.
 * @property rotationDeg The turn about the card's bottom centre.
 * @property z The stacking order. It is the same in both states.
 */
internal data class FanPose(val xDp: Float, val yDp: Float, val rotationDeg: Float, val z: Int)

/**
 * The pose of card [index] of [count]. Gathered, the cards share [widthDp], at most
 * [SeriesFanLayout.GATHERED_MAX_STEP_DP] apart from the row's start, and fan out by up to 20 degrees in total.
 * Spread, they sit 120 dp apart with no turn (iOS `SeriesFanLayout.pose`).
 *
 * @param current The index of this book, or null when it is not in the series; no card is raised then,
 * and z is computed as if this book were 0.
 */
internal fun fanPose(index: Int, count: Int, current: Int?, widthDp: Float, spread: Boolean): FanPose {
    val z = count - abs(index - (current ?: 0))
    if (spread) return FanPose(index * SeriesFanLayout.SPREAD_STEP_DP, 0f, 0f, z)
    val step = if (count > 1) {
        min(SeriesFanLayout.GATHERED_MAX_STEP_DP, (widthDp - SeriesFanLayout.CARD_WIDTH_DP) / (count - 1)).coerceAtLeast(0f)
    } else {
        0f
    }
    val turn = min(SeriesFanLayout.MAX_TURN_DEG, SeriesFanLayout.TOTAL_TURN_DEG / max(1, count - 1))
    return FanPose(
        xDp = index * step,
        yDp = if (index == current) -SeriesFanLayout.RAISE_DP else 0f,
        rotationDeg = (index - (count - 1) / 2f) * turn,
        z = z,
    )
}

/** Linear interpolation between two poses at fraction [t]. The z order is taken from [from]. */
internal fun lerpPose(from: FanPose, to: FanPose, t: Float): FanPose = FanPose(
    from.xDp + (to.xDp - from.xDp) * t,
    from.yDp + (to.yDp - from.yDp) * t,
    from.rotationDeg + (to.rotationDeg - from.rotationDeg) * t,
    from.z,
)

/** The width of the spread row for [count] books, without the edge padding. */
internal fun spreadWidthDp(count: Int): Float = count * SeriesFanLayout.SPREAD_STEP_DP - 16f

/**
 * The horizontal scroll that centres this book in the spread row, clamped to the scroll range.
 *
 * @param viewportDp The bled row's width (the section width plus 2 x [SeriesFanLayout.EDGE_DP]).
 */
internal fun spreadScrollTargetDp(current: Int?, count: Int, viewportDp: Float): Float {
    if (current == null) return 0f
    val content = spreadWidthDp(count) + 2 * SeriesFanLayout.EDGE_DP
    val centre = SeriesFanLayout.EDGE_DP + current * SeriesFanLayout.SPREAD_STEP_DP + SeriesFanLayout.CARD_WIDTH_DP / 2
    return (centre - viewportDp / 2).coerceIn(0f, max(0f, content - viewportDp))
}
