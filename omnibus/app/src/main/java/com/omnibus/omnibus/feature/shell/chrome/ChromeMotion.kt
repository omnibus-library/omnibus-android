package com.omnibus.omnibus.feature.shell.chrome

import com.omnibus.omnibus.ui.theme.Motion

/** A progress animation's length and start delay, in ms. Zero duration means snap. */
data class ProgressTiming(val durationMillis: Int, val delayMillis: Int)

/** The interpolated values the overlay places from. */
data class ChromeFrame(val barWidth: Float, val fabBottom: Float)

/** Timing and interpolation for the breakpoint switch (tech spec §4.1). Plain code: no UI framework types (I-ARCH-3). */
object ChromeMotion {
    fun progressTarget(mode: ChromeMode): Float = if (mode == ChromeMode.Wide) 1f else 0f

    /** True while a switch is under way: either progress is between its ends, or they disagree (I-MOT-5). */
    fun isInterrupted(barProgress: Float, fabProgress: Float): Boolean =
        !(barProgress == fabProgress && (barProgress == 0f || barProgress == 1f))

    /** The bar's timing for a switch to [target] (I-MOT-2). An interruption has no delay (I-MOT-5). */
    fun barTiming(target: ChromeMode, interrupted: Boolean, reducedMotion: Boolean): ProgressTiming = when {
        reducedMotion -> ProgressTiming(0, 0)
        interrupted || target == ChromeMode.Wide -> ProgressTiming(Motion.barResizeDuration, 0)
        else -> ProgressTiming(Motion.barResizeDuration, Motion.toNarrowBarDelay)
    }

    /** The FAB's timing for a switch to [target] (I-MOT-2). An interruption has no delay (I-MOT-5). */
    fun fabTiming(target: ChromeMode, interrupted: Boolean, reducedMotion: Boolean): ProgressTiming = when {
        reducedMotion -> ProgressTiming(0, 0)
        interrupted || target == ChromeMode.Narrow -> ProgressTiming(Motion.fabMoveDuration, 0)
        else -> ProgressTiming(Motion.fabMoveDuration, Motion.toWideFabDelay)
    }

    /** 0 when the dip is skipped (I-MOT-6). */
    fun dipDurationMillis(target: ChromeMode, reducedMotion: Boolean): Int = when {
        reducedMotion -> 0
        target == ChromeMode.Wide -> Motion.toWideFabDelay + Motion.fabMoveDuration
        else -> Motion.toNarrowDipDuration
    }

    /** The FAB's scale at dip progress [progress]: 1 → [Motion.fabDipScale] at [Motion.fabDipAt] → 1, linear between (I-MOT-4). */
    fun dipScale(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return if (p <= Motion.fabDipAt) {
            lerp(1f, Motion.fabDipScale, p / Motion.fabDipAt)
        } else {
            lerp(Motion.fabDipScale, 1f, (p - Motion.fabDipAt) / (1f - Motion.fabDipAt))
        }
    }

    fun lerp(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction

    /** Interpolates between the narrow and wide layouts of the *current* input (I-MOT-1). */
    fun frame(narrow: ChromeLayout, wide: ChromeLayout, barProgress: Float, fabProgress: Float): ChromeFrame =
        ChromeFrame(
            barWidth = lerp(narrow.barWidth, wide.barWidth, barProgress),
            fabBottom = lerp(narrow.fabBottom, wide.fabBottom, fabProgress),
        )
}
