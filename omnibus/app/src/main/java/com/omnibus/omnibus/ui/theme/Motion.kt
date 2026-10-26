package com.omnibus.omnibus.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Named springs shared with the iOS client's `Motion`. Stiffness is derived from the iOS spring
 * response as `(2π / response)²`.
 */
object Motion {
    /** Presses, taps, and anything the finger is still touching. */
    fun <T> lift(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = 0.74f, stiffness = 504f, visibilityThreshold = visibilityThreshold)

    /** Something arriving and coming to rest, such as entrances and insertions. */
    fun <T> settle(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = 0.82f, stiffness = 204f, visibilityThreshold = visibilityThreshold)

    /** Fast, damped response for small state changes. Response 0.22. */
    fun <T> snap(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = 0.80f, stiffness = 816f, visibilityThreshold = visibilityThreshold)

    /** Slow, relaxed movement with a soft settle. Response 0.50. */
    fun <T> glide(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = 0.88f, stiffness = 158f, visibilityThreshold = visibilityThreshold)

    /** Page turns and large transitions; deliberately not a spring so it never overshoots. */
    fun <T> page(): TweenSpec<T> =
        tween(durationMillis = 400, easing = CubicBezierEasing(0.2f, 0.6f, 0.3f, 1f))
}
