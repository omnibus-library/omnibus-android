package com.omnibus.omnibus.feature.audiobook.player

/**
 * When a downward drag on the player's stage dismisses it, ported from iOS `PlayerDismissDrag`. Offsets are in dp, and
 * an upward drag never moves the surface.
 */
internal object PlayerDismissDrag {

    /** The distance that dismisses the player on release. */
    const val DISMISS_DISTANCE_DP = 120f

    /** The predicted end of a flick that dismisses the player. */
    const val FLICK_PROJECTION_DP = 320f

    /** How far ahead the flick's velocity is projected. */
    const val PROJECTION_SECONDS = 0.2f

    /** The surface offset for a drag of [dragDp] in total. Upward drags report 0. */
    fun offset(dragDp: Float): Float = maxOf(0f, dragDp)

    /** Whether releasing at [offsetDp] with [velocityDpPerSecond] dismisses the player. */
    fun shouldDismiss(offsetDp: Float, velocityDpPerSecond: Float): Boolean =
        offsetDp > 0f &&
            (offsetDp >= DISMISS_DISTANCE_DP ||
                offsetDp + maxOf(0f, velocityDpPerSecond) * PROJECTION_SECONDS >= FLICK_PROJECTION_DP)

    /** How far the drag has come, from 0 to 1, for the surface's fade. */
    fun progress(offsetDp: Float): Float = (offsetDp / DISMISS_DISTANCE_DP).coerceIn(0f, 1f)
}
