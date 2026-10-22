package com.omnibus.omnibus.ui.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

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
}
