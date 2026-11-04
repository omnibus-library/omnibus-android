package com.omnibus.omnibus.feature.audiobook.player

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.omnibus.omnibus.ui.theme.Motion

/**
 * The dismiss drag's offset in dp, shared by the stage (which takes the drag) and the surface (which moves).
 */
@Stable
internal class PlayerDismissState(val density: Float) {
    var offsetDp by mutableFloatStateOf(0f)

    val progress: Float get() = PlayerDismissDrag.progress(offsetDp)

    val draggableState = DraggableState { deltaPx ->
        offsetDp = PlayerDismissDrag.offset(offsetDp + deltaPx / density)
    }
}

/** The [PlayerDismissState] for one player, rebuilt only when the screen density changes. */
@Composable
internal fun rememberPlayerDismissState(): PlayerDismissState {
    val density = LocalDensity.current.density
    return remember(density) { PlayerDismissState(density) }
}

/**
 * The downward drag that dismisses the player (R17). Not composable, so it can be applied to the stage alone; it is
 * rebuilt on recomposition, so [onDismiss] stays current. Release dismisses past the threshold or on a fast flick, and
 * otherwise springs back (snaps with reduced motion).
 */
internal fun Modifier.playerDismissDrag(
    state: PlayerDismissState,
    reducedMotion: Boolean,
    onDismiss: () -> Unit,
): Modifier = draggable(
    state = state.draggableState,
    orientation = Orientation.Vertical,
    onDragStopped = { velocityPx ->
        if (PlayerDismissDrag.shouldDismiss(state.offsetDp, velocityPx / state.density)) {
            onDismiss()                            // the surface stays put; the nav pop carries it away
        } else if (reducedMotion) {
            state.offsetDp = 0f
        } else {
            animate(state.offsetDp, 0f, animationSpec = Motion.glide()) { value, _ -> state.offsetDp = value }
        }
    },
)

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
