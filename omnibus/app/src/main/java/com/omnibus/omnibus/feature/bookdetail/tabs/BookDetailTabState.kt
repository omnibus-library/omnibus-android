package com.omnibus.omnibus.feature.bookdetail.tabs

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.feature.bookdetail.BookDetailAnchor
import com.omnibus.omnibus.feature.bookdetail.BookDetailTab
import kotlin.math.max

/** The height of the tab strip, below the stage. */
internal val TabStripHeight = 50.dp

/** The strip's horizontal inset from the screen edges; the four cells share the width inside it. */
internal val TabStripInset = 16.dp

/** The gap between the pinned strip and an anchored element that a scroll brings into view (iOS `jumpClearance`). */
internal val AnchorClearance = 16.dp

/** Extra height the tab host keeps so the page can always scroll the strip to its pin point (iOS uses 4 pt). */
internal val TabHostSlack = 4.dp

/** How long a scroll to an anchor waits for the anchor to lay out before it falls back to the tab. */
internal const val ANCHOR_WAIT_MILLIS = 1_000L

/**
 * The entry direction for moving from [from] to [to]: +1 when the new tab is to the right (its panel enters from the
 * right), -1 when it is to the left, and null when nothing changes.
 */
internal fun tabEntryDirection(from: BookDetailTab, to: BookDetailTab): Int? = when {
    from == to -> null
    to.ordinal > from.ordinal -> 1
    else -> -1
}

/** The tab an anchor's element lives in. Exhaustive, so a new anchor cannot ship without a tab. */
internal val BookDetailAnchor.tab: BookDetailTab
    get() = when (this) {
        BookDetailAnchor.Series -> BookDetailTab.Overview
    }

/**
 * How far the content is pushed down so the strip stays at [pinTop] once the page has scrolled past it. Zero until
 * the strip reaches the pin point, then it tracks the scroll one to one.
 */
internal fun stripPinOffsetPx(scroll: Int, stripTop: Int, pinTop: Int): Int = max(0, scroll + pinTop - stripTop)

/** The scroll value that puts the strip at [pinTop], clamped to the scroll range. */
internal fun jumpScrollTarget(stripTop: Int, pinTop: Int, maxScroll: Int): Int =
    (stripTop - pinTop).coerceIn(0, max(0, maxScroll))

/**
 * The scroll value that puts an anchored element [clearance] below the pinned strip, clamped to the scroll range.
 * [anchorContentY] is the element's top in the content's coordinates, not on screen.
 */
internal fun anchorScrollTarget(
    anchorContentY: Int,
    pinTop: Int,
    stripHeight: Int,
    clearance: Int,
    maxScroll: Int,
): Int = (anchorContentY - pinTop - stripHeight - clearance).coerceIn(0, max(0, maxScroll))

/**
 * The minimum height of the tab host. It is the viewport minus the top bar and the action area, plus a little slack,
 * so the page can always scroll the strip up to its pin point whichever panel is shown.
 */
internal fun tabHostMinHeight(viewport: Dp, pinTop: Dp, actionArea: Dp): Dp =
    (viewport - pinTop - actionArea + TabHostSlack).coerceAtLeast(0.dp)
