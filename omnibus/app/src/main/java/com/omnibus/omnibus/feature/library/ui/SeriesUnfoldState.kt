package com.omnibus.omnibus.feature.library.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import com.omnibus.omnibus.feature.library.SeriesUnfoldTiming
import com.omnibus.omnibus.feature.library.UnfoldEasing
import com.omnibus.omnibus.feature.library.UnfoldFrame
import com.omnibus.omnibus.feature.library.remainingMillis
import com.omnibus.omnibus.feature.library.unfoldFrame
import com.omnibus.omnibus.ui.theme.Motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * How far each series has got in its unfold or fold, as a linear `t` from 0 (folded) to 1 (unfolded). Only the series
 * that are animating are held. The ViewModel's unfolded set stays the truth for what each series should be; this
 * knows only where the animation of a change has reached.
 *
 * A change is seen in the composition that shows it: [settled] still holds the set from the last [sync], so the first
 * frame of a change already reads `t = 0` (unfold) or `t = 1` (fold) and gets the placement animation. [sync] then
 * starts the animations from a `LaunchedEffect`, in [scope] rather than the effect's own scope, so a later change
 * does not cancel a running one.
 */
@Stable
internal class SeriesUnfoldState(initial: Set<Long>, private val scope: CoroutineScope) {

    /** Set from composition on every pass. Not snapshot state. */
    var reducedMotion: Boolean = false

    /** The unfolded set at the last [sync]. A difference from the current set is a change this composition shows. */
    private var settled by mutableStateOf(initial)
    private val progress = mutableStateMapOf<Long, Animatable<Float, AnimationVector1D>>()
    private val jobs = HashMap<Long, Job>()

    /** Starts or retargets the animation of every series whose state changed since the last call. */
    fun sync(unfolded: Set<Long>, groupBySeries: Boolean) {
        if (!groupBySeries) {
            // Grouping off: there is no fold into a flat grid, so everything snaps (R15).
            jobs.values.forEach { it.cancel() }
            jobs.clear()
            progress.clear()
            settled = unfolded
            return
        }
        for (id in (unfolded - settled) + (settled - unfolded)) {
            val target = if (id in unfolded) 1f else 0f
            jobs.remove(id)?.cancel()
            // A fold under reduced motion has no animation: the layout switches at once (Q2).
            if (reducedMotion && target == 0f) {
                progress.remove(id)
                continue
            }
            // A retarget keeps the current value, so the change continues from there (R9).
            val anim = progress.getOrPut(id) { Animatable(1f - target) }
            val spec: AnimationSpec<Float> = if (reducedMotion) {
                Motion.crossfade()
            } else {
                tween(remainingMillis(anim.value, target), easing = LinearEasing)
            }
            jobs[id] = scope.launch {
                anim.animateTo(target, spec)
                // Not reached when a retarget cancels this job, because animateTo throws.
                progress.remove(id)
                jobs.remove(id)
            }
        }
        settled = unfolded
    }

    /**
     * The unfold `t` of series [id] now, or null at rest. It reads the animated value, so call it from a draw or layout
     * lambda only.
     */
    fun t(id: Long, unfolded: Set<Long>): Float? {
        progress[id]?.let { return it.value }
        val now = id in unfolded
        val was = id in settled
        return when {
            now && !was -> 0f
            !now && was -> if (reducedMotion) null else 1f
            else -> null
        }
    }

    /** The frame of series [id] now, or null at rest. Like [t], for draw lambdas only. */
    fun frame(id: Long, unfolded: Set<Long>): UnfoldFrame? {
        val t = t(id, unfolded) ?: return null
        // Under reduced motion there is no growth: the container only fades in with the cross-fade.
        if (reducedMotion) return if (id in unfolded) UnfoldFrame.REST.copy(containerAlpha = t) else null
        return unfoldFrame(t)
    }

    /** The series whose folded stack is still animating, whose containers draw over the stack. Composition only. */
    fun folding(unfolded: Set<Long>): Set<Long> {
        if (reducedMotion) return emptySet()
        return (progress.keys - unfolded) + (settled - unfolded)
    }

    /**
     * The placement animation of the grid's items in this composition, or null when no series is changing, so sorting,
     * filtering and paging place their items at once (R11). Composition only.
     */
    fun placementSpec(unfolded: Set<Long>, groupBySeries: Boolean): FiniteAnimationSpec<IntOffset>? {
        if (reducedMotion || !groupBySeries) return null
        return when {
            (unfolded - settled).isNotEmpty() || progress.values.any { it.targetValue == 1f } -> UnfoldPlacement
            (settled - unfolded).isNotEmpty() || progress.values.any { it.targetValue == 0f } -> FoldPlacement
            else -> null
        }
    }
}

/** The reflow of the cells after an unfolding series: the container's 70% of the 800 ms unfold, on the page curve. */
private val UnfoldPlacement = tween<IntOffset>(
    durationMillis = (SeriesUnfoldTiming.GROW_END * SeriesUnfoldTiming.UNFOLD_MILLIS).toInt(),
    easing = UnfoldEasing,
)

/**
 * The reflow of the cells after a folding series: the unfold's reflow played backwards. It waits out the fold's first
 * 30% and then runs the reversed curve over the rest.
 */
private val FoldPlacement = tween<IntOffset>(
    durationMillis = (SeriesUnfoldTiming.GROW_END * SeriesUnfoldTiming.FOLD_MILLIS).toInt(),
    delayMillis = ((1f - SeriesUnfoldTiming.GROW_END) * SeriesUnfoldTiming.FOLD_MILLIS).toInt(),
    easing = Easing { 1f - UnfoldEasing.transform(1f - it) },
)

/**
 * The [SeriesUnfoldState] of the grid. It starts settled on [unfolded], so a series that is already open when the grid
 * composes is shown at rest and does not replay (R15).
 */
@Composable
internal fun rememberSeriesUnfoldState(unfolded: Set<Long>, reducedMotion: Boolean): SeriesUnfoldState {
    val scope = rememberCoroutineScope()
    return remember { SeriesUnfoldState(unfolded, scope) }.also { it.reducedMotion = reducedMotion }
}
