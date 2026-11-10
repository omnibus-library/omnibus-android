package com.omnibus.omnibus.feature.library

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** The unfold timeline's durations, windows and offsets, in milliseconds, fractions of `t` and design dp. */
internal object SeriesUnfoldTiming {
    const val UNFOLD_MILLIS = 800
    const val FOLD_MILLIS = 600
    const val GROW_END = 0.7f
    const val CONTAINER_FADE_END = 0.2f
    const val SUMMARY_START = 0.3f
    const val SUMMARY_LENGTH = 0.4f
    const val STACK_CAPTION_END = 0.25f
    const val MEMBER_CAPTION_START = 0.65f
    const val MEMBER_CAPTION_LENGTH = 0.35f
    const val COVER_STAGGER = 0.08f
    const val STAGGERED_COVERS = 3
    const val STACK_STEP_DP = 6f
    const val SUMMARY_RISE_DP = 6f
    const val VEIL_ALPHA = 0.45f
}

/** The page curve of `Motion.page`, applied to each element's own window. */
internal val UnfoldEasing = CubicBezierEasing(0.2f, 0.6f, 0.3f, 1f)

/** [UnfoldEasing] of [x], clamped to 0..1 and exactly 0 and 1 at the ends. */
internal fun unfoldEase(x: Float): Float = when {
    x <= 0f -> 0f
    x >= 1f -> 1f
    else -> UnfoldEasing.transform(x)
}

/** Where each element of an unfolding series is at one time `t`. */
internal data class UnfoldFrame(
    val containerProgress: Float,
    val containerAlpha: Float,
    val reflowProgress: Float,
    val summaryAlpha: Float,
    val summaryRiseDp: Float,
    val stackCaptionAlpha: Float,
    val memberCaptionAlpha: Float,
) {
    companion object {
        /** The frame of a series at rest, unfolded. */
        val REST: UnfoldFrame = unfoldFrame(1f)
    }
}

/** The frame at time [t] in 0..1, where 0 is the folded stack and 1 the unfolded series. Out-of-range values clamp. */
internal fun unfoldFrame(t: Float): UnfoldFrame {
    val growth = unfoldEase(t / SeriesUnfoldTiming.GROW_END)
    val summary = unfoldEase((t - SeriesUnfoldTiming.SUMMARY_START) / SeriesUnfoldTiming.SUMMARY_LENGTH)
    return UnfoldFrame(
        containerProgress = growth,
        containerAlpha = (t / SeriesUnfoldTiming.CONTAINER_FADE_END).coerceIn(0f, 1f),
        reflowProgress = growth,
        summaryAlpha = summary,
        summaryRiseDp = SeriesUnfoldTiming.SUMMARY_RISE_DP * (1f - summary),
        stackCaptionAlpha = (1f - t / SeriesUnfoldTiming.STACK_CAPTION_END).coerceIn(0f, 1f),
        memberCaptionAlpha = unfoldEase(
            (t - SeriesUnfoldTiming.MEMBER_CAPTION_START) / SeriesUnfoldTiming.MEMBER_CAPTION_LENGTH,
        ),
    )
}

/** The progress of member cover [index] at time [t]. Each cover starts 8% of `t` after the previous one, up to cover 3. */
internal fun coverProgress(t: Float, index: Int): Float {
    val start = SeriesUnfoldTiming.COVER_STAGGER * min(index, SeriesUnfoldTiming.STAGGERED_COVERS)
    return unfoldEase((t - start) / SeriesUnfoldTiming.GROW_END)
}

/**
 * The transform of a member's cover drawn in its own slot: scaled about its bottom-left corner, then translated.
 *
 * @property widthFraction The cover's width as a fraction of its own slot's width.
 * @property dxDp The horizontal translation from the slot, in dp.
 * @property dyDp The vertical translation from the slot, in dp.
 * @property veilAlpha The alpha of the background veil drawn over the cover.
 * @property alpha The cover's own alpha.
 */
internal data class CoverPose(
    val widthFraction: Float,
    val dxDp: Float,
    val dyDp: Float,
    val veilAlpha: Float,
    val alpha: Float,
)

/**
 * The pose of member cover [index] at [progress]. At 0 it sits in the folded stack's pose over the anchor cell, and at
 * 1 in its own slot. [slotDxDp] and [slotDyDp] are the anchor cell's cover box minus this member's, in dp. Covers past
 * the third share the third's stack pose and fade in (Q1).
 */
internal fun coverPose(index: Int, progress: Float, cellWidthDp: Float, slotDxDp: Float, slotDyDp: Float): CoverPose {
    val p = progress.coerceIn(0f, 1f)
    val q = 1f - p
    val k = min(index, 2)
    val stack = (cellWidthDp - 2 * SeriesUnfoldTiming.STACK_STEP_DP) / cellWidthDp
    return CoverPose(
        widthFraction = stack + (1f - stack) * p,
        dxDp = (slotDxDp + SeriesUnfoldTiming.STACK_STEP_DP * k) * q,
        dyDp = (slotDyDp - SeriesUnfoldTiming.STACK_STEP_DP * k) * q,
        veilAlpha = if (k == 0) 0f else SeriesUnfoldTiming.VEIL_ALPHA * q,
        alpha = if (index >= SeriesUnfoldTiming.STAGGERED_COVERS) p else 1f,
    )
}

/** An axis-aligned rectangle in px. */
internal data class OutlineRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** The smallest rectangle containing these points. The list must not be empty. */
internal fun List<OutlinePoint>.bounds(): OutlineRect = OutlineRect(
    left = minOf { it.x },
    top = minOf { it.y },
    right = maxOf { it.x },
    bottom = maxOf { it.y },
)

/**
 * The points at [progress], each moved on a straight line from its position clamped into [rect] to its own position.
 * The vertex count is kept, and the bounds grow from [rect] to the full outline as [progress] rises.
 */
internal fun collapseToward(points: List<OutlinePoint>, rect: OutlineRect, progress: Float): List<OutlinePoint> {
    val p = progress.coerceIn(0f, 1f)
    return points.map { point ->
        val cx = point.x.coerceIn(rect.left, rect.right)
        val cy = point.y.coerceIn(rect.top, rect.bottom)
        OutlinePoint(cx + (point.x - cx) * p, cy + (point.y - cy) * p)
    }
}

/** The outline of a one-cell run at [startColumn], the folded stack's container, in the first row of [grid]. */
internal fun stackRect(startColumn: Int, columns: Int, grid: OutlineGrid): OutlineRect {
    val firstRow = grid.copy(rowTops = grid.rowTops.take(1), rowBottoms = grid.rowBottoms.take(1))
    return outlineStrokes(seriesOutline(startColumn, 1, columns), firstRow).flatMap { it.points }.bounds()
}

/** The time in milliseconds to move from [from] to [to] at the full speed of that direction. */
internal fun remainingMillis(from: Float, to: Float): Int =
    (abs(to - from) * if (to > from) SeriesUnfoldTiming.UNFOLD_MILLIS else SeriesUnfoldTiming.FOLD_MILLIS).roundToInt()
