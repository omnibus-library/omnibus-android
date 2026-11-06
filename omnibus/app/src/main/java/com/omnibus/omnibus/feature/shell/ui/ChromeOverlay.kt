package com.omnibus.omnibus.feature.shell.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.feature.shell.chrome.ChromeLayoutInput
import com.omnibus.omnibus.feature.shell.chrome.ChromeMode
import com.omnibus.omnibus.feature.shell.chrome.ChromeMotion
import com.omnibus.omnibus.feature.shell.chrome.ChromeTokens
import com.omnibus.omnibus.feature.shell.chrome.ProgressTiming
import com.omnibus.omnibus.feature.shell.chrome.blockHeight
import com.omnibus.omnibus.feature.shell.chrome.chromeLayout
import com.omnibus.omnibus.ui.theme.Motion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Test tags on the overlay's slots, whose bounds are the bar's, the mini player's and the FAB's. */
internal object ChromeTestTags {
    const val BAR_SLOT = "chrome-bar-slot"
    const val MINI_PLAYER_SLOT = "chrome-mini-player-slot"
    const val FAB_SLOT = "chrome-fab-slot"
}

private enum class ChromeSlot { MiniPlayer, Bar, Fab }

/** Progress of the breakpoint switch and the FAB's dip. Not saved: the layout is recomputed from the window (I-NAV-6). */
@Stable
private class ChromeMotionState(initialMode: ChromeMode) {
    val bar = Animatable(ChromeMotion.progressTarget(initialMode))
    val fab = Animatable(ChromeMotion.progressTarget(initialMode))
    val dip = Animatable(1f)
}

private suspend fun Animatable<Float, AnimationVector1D>.runTo(target: Float, timing: ProgressTiming) {
    if (timing.durationMillis == 0) {
        snapTo(target)
    } else {
        animateTo(target, tween(timing.durationMillis, timing.delayMillis, Motion.emphasized))
    }
}

/** Animates on mode changes only, so a resize inside one mode is tracked directly (P-MOT-6). */
@Composable
private fun rememberChromeMotion(mode: ChromeMode, reducedMotion: Boolean): ChromeMotionState {
    val state = remember { ChromeMotionState(mode) }
    LaunchedEffect(mode) {
        val target = ChromeMotion.progressTarget(mode)
        if (state.bar.value == target && state.fab.value == target) return@LaunchedEffect // first composition
        val interrupted = ChromeMotion.isInterrupted(state.bar.value, state.fab.value)
        coroutineScope {
            launch { state.bar.runTo(target, ChromeMotion.barTiming(mode, interrupted, reducedMotion)) }
            launch { state.fab.runTo(target, ChromeMotion.fabTiming(mode, interrupted, reducedMotion)) }
            val dipMillis = ChromeMotion.dipDurationMillis(mode, reducedMotion)
            if (dipMillis > 0) {
                launch {
                    // An interrupted dip carries on from where it is rather than jumping back to full size.
                    if (state.dip.value >= 1f) state.dip.snapTo(0f)
                    state.dip.animateTo(1f, tween(dipMillis, easing = Motion.emphasized))
                }
            }
        }
    }
    return state
}

/**
 * The shell's body: [content] fills the window and draws behind the floating chrome, which is overlaid at the bottom
 * (I-ARCH-1). Provides [LocalShellContentPadding] to [content].
 *
 * @param chromeVisible false hides the bar, the mini player and the FAB together (P-VIS-1, I-MOT-9).
 * @param fabVisible whether the selected root shows its FAB (P-FAB-3). Never changes the bar (P-LAY-5).
 * @param bar the tab bar; laid out at the bar's width.
 * @param fab the FAB; shown and hidden with [fabVisible] here, so it should not animate itself.
 * @param miniPlayer laid out at the bar's width, 8 dp above it (I-MINI-1); takes no height when empty.
 */
@Composable
internal fun ChromeScaffold(
    chromeVisible: Boolean,
    fabVisible: Boolean,
    bar: @Composable () -> Unit,
    fab: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    miniPlayer: @Composable () -> Unit = {},
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val direction = LocalLayoutDirection.current
        // Horizontal safe area: side navigation bars and cutouts (AND-LAY-1). The keyboard is never included (AND-LAY-2).
        val safe = WindowInsets.safeDrawing
        val leftInset = with(density) { safe.getLeft(this, direction).toDp() }
        val rightInset = with(density) { safe.getRight(this, direction).toDp() }
        val startInset = if (direction == LayoutDirection.Ltr) leftInset else rightInset
        val endInset = if (direction == LayoutDirection.Ltr) rightInset else leftInset
        val bottomInset = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }

        var barHeight by remember { mutableStateOf(ChromeTokens.navBarHeight.dp) }
        var miniPlayerHeight by remember { mutableStateOf(0.dp) }
        val input = ChromeLayoutInput(
            availableWidth = (maxWidth - startInset - endInset).value,
            bottomInset = bottomInset.value,
            barHeight = barHeight.value,
            miniPlayerHeight = miniPlayerHeight.value,
            fabVisible = fabVisible,
        )
        val target = chromeLayout(input)
        val motion = rememberChromeMotion(target.mode, reducedMotion)
        val hidden = animateFloatAsState(
            targetValue = if (chromeVisible) 0f else 1f,
            animationSpec = when {
                reducedMotion -> snap()
                chromeVisible -> Motion.settle()
                else -> Motion.snap()
            },
            label = "chromeHidden",
        )

        CompositionLocalProvider(LocalShellContentPadding provides PaddingValues(bottom = target.contentBottomPadding.dp)) {
            content()
        }
        ChromeOverlay(
            input = input,
            startInset = startInset,
            endInset = endInset,
            motion = motion,
            hidden = hidden,
            chromeVisible = chromeVisible,
            fabVisible = fabVisible,
            reducedMotion = reducedMotion,
            onBarHeight = { barHeight = it },
            onMiniPlayerHeight = { miniPlayerHeight = it },
            bar = bar,
            fab = fab,
            miniPlayer = miniPlayer,
        )
    }
}

@Composable
private fun ChromeOverlay(
    input: ChromeLayoutInput,
    startInset: Dp,
    endInset: Dp,
    motion: ChromeMotionState,
    hidden: State<Float>,
    chromeVisible: Boolean,
    fabVisible: Boolean,
    reducedMotion: Boolean,
    onBarHeight: (Dp) -> Unit,
    onMiniPlayerHeight: (Dp) -> Unit,
    bar: @Composable () -> Unit,
    fab: @Composable () -> Unit,
    miniPlayer: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    // Slide distance for hiding: the whole stack clears the bottom edge (I-MOT-9).
    val slidePx = with(density) { (input.bottomInset + ChromeTokens.navBottomMargin + input.blockHeight()).dp.toPx() }
    Layout(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val h = hidden.value
                translationY = h * slidePx
                alpha = (1f - h).coerceIn(0f, 1f)
            }
            // Hidden chrome is out of the accessibility tree too.
            .then(if (chromeVisible) Modifier else Modifier.clearAndSetSemantics {}),
        content = {
            Box(
                Modifier
                    .layoutId(ChromeSlot.MiniPlayer)
                    .testTag(ChromeTestTags.MINI_PLAYER_SLOT)
                    .onSizeChanged { onMiniPlayerHeight(with(density) { it.height.toDp() }) },
                propagateMinConstraints = true,
            ) { miniPlayer() }
            Box(
                Modifier
                    .layoutId(ChromeSlot.Bar)
                    .testTag(ChromeTestTags.BAR_SLOT)
                    .onSizeChanged { onBarHeight(with(density) { it.height.toDp() }) },
                propagateMinConstraints = true,
            ) { bar() }
            Box(
                Modifier
                    .layoutId(ChromeSlot.Fab)
                    .testTag(ChromeTestTags.FAB_SLOT)
                    .graphicsLayer {
                        val s = ChromeMotion.dipScale(motion.dip.value)
                        scaleX = s
                        scaleY = s
                    },
            ) {
                AnimatedVisibility(
                    visible = fabVisible,
                    enter = if (reducedMotion) EnterTransition.None
                    else scaleIn(Motion.settle(), initialScale = 0.6f) + fadeIn(Motion.settle()),
                    exit = if (reducedMotion) ExitTransition.None
                    else scaleOut(Motion.snap(), targetScale = 0.6f) + fadeOut(Motion.snap()),
                ) { fab() }
            }
        },
    ) { measurables, constraints ->
        // Layout phase: progress is read here, so animation frames re-measure and re-place without recomposing (AND-MOT-1).
        val barProgress = motion.bar.value
        val fabProgress = motion.fab.value
        val widthDp = ChromeMotion.lerp(
            chromeLayout(input, ChromeMode.Narrow).barWidth,
            chromeLayout(input, ChromeMode.Wide).barWidth,
            barProgress,
        )
        val barWidthPx = widthDp.dp.roundToPx()
        val atBarWidth = Constraints(minWidth = barWidthPx, maxWidth = barWidthPx, minHeight = 0, maxHeight = constraints.maxHeight)
        val mini = measurables.first { it.layoutId == ChromeSlot.MiniPlayer }.measure(atBarWidth)
        val barPlaceable = measurables.first { it.layoutId == ChromeSlot.Bar }.measure(atBarWidth)
        val fabPlaceable = measurables.first { it.layoutId == ChromeSlot.Fab }.measure(Constraints())
        // Recompute both ends with the heights just measured, so the FAB never lags a height change (I-LAY-6).
        val measured = input.copy(
            barHeight = barPlaceable.height.toDp().value,
            miniPlayerHeight = mini.height.toDp().value,
        )
        val narrow = chromeLayout(measured, ChromeMode.Narrow)
        val wide = chromeLayout(measured, ChromeMode.Wide)
        val frame = ChromeMotion.frame(narrow, wide, barProgress, fabProgress)
        layout(constraints.maxWidth, constraints.maxHeight) {
            // placeRelative mirrors x in RTL, so start/end come out right without a second code path (P-LAY-6).
            val startX = (startInset + ChromeTokens.navSideMargin.dp).roundToPx()
            val barY = constraints.maxHeight - narrow.barBottom.dp.roundToPx() - barPlaceable.height
            barPlaceable.placeRelative(startX, barY)
            mini.placeRelative(startX, barY - ChromeTokens.miniPlayerGap.dp.roundToPx() - mini.height)
            val fabSizePx = ChromeTokens.fabSize.dp.roundToPx()
            val fabX = constraints.maxWidth - (endInset + narrow.fabEnd.dp).roundToPx() - fabSizePx
            val fabY = constraints.maxHeight - frame.fabBottom.dp.roundToPx() - fabSizePx
            fabPlaceable.placeRelative(fabX, fabY)
        }
    }
}
