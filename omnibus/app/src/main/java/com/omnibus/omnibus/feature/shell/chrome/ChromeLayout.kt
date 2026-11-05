package com.omnibus.omnibus.feature.shell.chrome

/**
 * Layout tokens for the floating chrome, in dp (tech spec §1.1). Plain floats keep [chromeLayout] free of UI types.
 */
object ChromeTokens {
    /** Spacing.md. */
    const val navSideMargin = 12f

    /** Spacing.sm. Above the system navigation inset. */
    const val navBottomMargin = 8f

    /** Minimum; the measured height wins (I-LAY-6). */
    const val navBarHeight = 64f
    const val navBarMaxWidth = 360f
    const val navBarHorizontalPadding = 6f

    /** Same as Radius.chrome. */
    const val navCornerRadius = 16f
    const val fabSize = 56f
    const val fabGap = 12f

    /** Spacing.sm. Between the nav bar and the mini player bar above it. */
    const val miniPlayerGap = 8f
    const val contentBreathingRoom = 16f

    /** The narrowest width at which the bar at full size, the FAB and the margins fit side by side (P-LAY-3). */
    const val breakpoint = 2 * navSideMargin + navBarMaxWidth + fabGap + fabSize
}

/** Narrow: the bar fills the width and the FAB floats above its end. Wide: the bar is capped at the start, the FAB at the end. */
enum class ChromeMode { Narrow, Wide }

/**
 * @property availableWidth Window width minus the horizontal safe-area insets.
 * @property bottomInset The system navigation inset. Never the keyboard (P-VIS-5).
 * @property barHeight The measured bar height, at least [ChromeTokens.navBarHeight].
 * @property miniPlayerHeight 0 when no book is loaded.
 * @property fabVisible Whether the selected root shows its FAB (P-FAB-3).
 */
data class ChromeLayoutInput(
    val availableWidth: Float,
    val bottomInset: Float,
    val barHeight: Float = ChromeTokens.navBarHeight,
    val miniPlayerHeight: Float = 0f,
    val fabVisible: Boolean = false,
)

/** The nav bar plus the mini player bar above it, when one shows. */
fun ChromeLayoutInput.blockHeight(): Float =
    barHeight + (if (miniPlayerHeight > 0f) ChromeTokens.miniPlayerGap + miniPlayerHeight else 0f)

/**
 * Where the chrome goes, in dp. Bottoms are measured up from the window's bottom edge; [fabEnd] in from the safe area's
 * end edge.
 *
 * @property contentBottomPadding What scrolling content pads at its bottom to clear the chrome (I-LAY-7).
 */
data class ChromeLayout(
    val mode: ChromeMode,
    val barWidth: Float,
    val barBottom: Float,
    val fabEnd: Float,
    val fabBottom: Float,
    val contentBottomPadding: Float,
)

/** The mode [availableWidth] calls for. Ties are wide (I-LAY-1). */
fun chromeModeFor(availableWidth: Float): ChromeMode =
    if (availableWidth >= ChromeTokens.breakpoint) ChromeMode.Wide else ChromeMode.Narrow

/** The layout for [input] in the mode its width calls for (tech spec §3). */
fun chromeLayout(input: ChromeLayoutInput): ChromeLayout = chromeLayout(input, chromeModeFor(input.availableWidth))

/** The layout for [input] as if it were in [mode]: the two ends the breakpoint motion interpolates between (I-MOT-1). */
fun chromeLayout(input: ChromeLayoutInput, mode: ChromeMode): ChromeLayout {
    val t = ChromeTokens
    val wide = mode == ChromeMode.Wide
    val barBottom = input.bottomInset + t.navBottomMargin
    val blockHeight = input.blockHeight()
    val barWidth = if (wide) t.navBarMaxWidth else (input.availableWidth - 2 * t.navSideMargin).coerceAtLeast(0f)
    val fabBottom = if (wide) barBottom + (input.barHeight - t.fabSize) / 2 else barBottom + blockHeight + t.fabGap
    val fabReserve = if (!wide && input.fabVisible) t.fabSize + t.fabGap else 0f
    return ChromeLayout(
        mode = mode,
        barWidth = barWidth,
        barBottom = barBottom,
        fabEnd = t.navSideMargin,
        fabBottom = fabBottom,
        contentBottomPadding = barBottom + blockHeight + t.contentBreathingRoom + fabReserve,
    )
}
