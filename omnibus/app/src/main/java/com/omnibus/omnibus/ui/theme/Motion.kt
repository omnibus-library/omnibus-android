package com.omnibus.omnibus.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Ease
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * Named springs and tweens shared with the iOS client's `Motion`. Stiffness is derived from the iOS spring
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

    /** The bookmark ribbon unfurling; y above 1 overshoots about 8 dp, then settles. */
    fun <T> unfurl(): TweenSpec<T> =
        tween(durationMillis = 640, easing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1.14f))

    /** The bookmark ribbon furling straight up, with no overshoot. */
    fun <T> furl(): TweenSpec<T> =
        tween(durationMillis = 340, easing = CubicBezierEasing(0.55f, 0f, 0.85f, 0.4f))

    /** Something arriving with a new page, already at rest. */
    fun <T> pageFade(): TweenSpec<T> = tween(durationMillis = 140, easing = Ease)

    /** Every change under reduced motion: a plain cross-fade in place. */
    fun <T> crossfade(): TweenSpec<T> = tween(durationMillis = 120, easing = Ease)
}

/** True when the system "Remove animations" setting is on (animator duration scale 0). */
fun isReducedMotion(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f

/**
 * Whether the system asks for no animation. Read once when the caller enters composition, so a
 * change made while reading applies the next time the reader opens. Always false in previews.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    if (LocalInspectionMode.current) return false
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        isReducedMotion(Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
    }
}
