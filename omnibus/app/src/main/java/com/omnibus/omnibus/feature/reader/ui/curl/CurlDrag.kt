package com.omnibus.omnibus.feature.reader.ui.curl

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** How a released turn ends: the page completes the turn, or settles back. */
internal enum class CurlDecision { Commit, Cancel }

/**
 * Maps a drag in direction [dir] (1 forward, -1 back) over [turning], from a touch-down at ([downX], [downY]), to fold
 * points. The lifted corner C follows the touch's row, and the fold tilts with the finger.
 */
internal class CurlDrag(val dir: Int, val turning: CurlRect, private val downX: Float, private val downY: Float) {
    /** The lifted corner C, on the turning page's right edge at the touch's row. */
    val corner = Vec(turning.right, downY.coerceIn(turning.top, turning.bottom))

    /** The fold past the left edge, where nothing of the turning page is kept. */
    val turned = Vec(2 * turning.left - corner.x - 1f, corner.y)

    /**
     * The fold for the finger at ([x], [y]). Vertical travel is damped, so a straight swipe folds near-vertically, and
     * the fold's tilt is capped at [MAX_FOLD_SLOPE] so a steep pull never flips the page up or down.
     */
    fun fold(x: Float, y: Float): Vec {
        val dx = x - downX
        val dy = y - downY
        val foldX = if (dir > 0) corner.x + min(dx, 0f) else min(turned.x + 2 * max(dx, 0f), corner.x)
        val tilt = (if (dir > 0) dy * TILT_DAMPING else 2 * dy * TILT_DAMPING)
        val limit = MAX_FOLD_SLOPE * (corner.x - foldX)
        return Vec(foldX, corner.y + tilt.coerceIn(-limit, limit))
    }

    /** How far the turn has gone, 0 to 1, from the touch-down toward the turn direction. */
    fun progress(x: Float): Float = ((if (dir > 0) downX - x else x - downX) / turning.width).coerceIn(0f, 1f)

    /** The fold a release with [decision] settles to: [turned] when the page completes the turn, else [corner]. */
    fun target(decision: CurlDecision): Vec = if ((dir > 0) == (decision == CurlDecision.Commit)) turned else corner

    companion object {
        /** How far the fold's tilt follows vertical finger travel. */
        const val TILT_DAMPING = 0.35f

        /** The most the fold may offset vertically from the corner, per pixel of horizontal distance from it. */
        const val MAX_FOLD_SLOPE = 0.6f

        /** A release faster than this, in dp per second, is a flick. */
        const val FLICK_DP_PER_S = 1_000f

        /**
         * A flick decides by its sign alone: toward the turn completes it. Otherwise a release past halfway commits.
         * [velocityDp] is in dp per second, negative leftwards.
         */
        fun release(dir: Int, progress: Float, velocityDp: Float): CurlDecision = when {
            abs(velocityDp) >= FLICK_DP_PER_S ->
                if (velocityDp.sign.toInt() == -dir) CurlDecision.Commit else CurlDecision.Cancel
            progress >= 0.5f -> CurlDecision.Commit
            else -> CurlDecision.Cancel
        }

        /**
         * The fold of a gutter tap at time [t] (0 to 1) over [turning], ported from `curl.html`'s tap loop. A back turn
         * runs the forward path in reverse.
         */
        fun tapFold(dir: Int, turning: CurlRect, t: Float): Vec {
            val c = Vec(turning.right, turning.top + 0.7f * turning.height)
            val turnedX = 2 * turning.left - c.x - 1f
            val eased = easeInOutCubic(t.coerceIn(0f, 1f))
            val p = if (dir < 0) 1 - eased else eased
            val h = turning.height
            return Vec(c.x - p * (c.x - turnedX), c.y - sin(PI.toFloat() * p) * 0.08f * h)
        }

        /** The cubic ease in and out used by the tap curl. */
        fun easeInOutCubic(x: Float): Float = if (x < 0.5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    }
}
