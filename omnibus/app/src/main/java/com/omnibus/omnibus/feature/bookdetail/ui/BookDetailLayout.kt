package com.omnibus.omnibus.feature.bookdetail.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

/** The widths of the two panes in two-pane mode. */
internal data class PaneSplit(val leading: Dp, val trailing: Dp)

/** The y positions of a fade: [clear] is where the content is gone, [opaque] where it is whole. */
internal data class FadeBand(val clear: Dp, val opaque: Dp)

/** The narrowest window that gets two panes, when it is also wider than it is tall. */
internal val TwoPaneMinWidth = 560.dp

/** The leading pane's share of the width, as an integer percent so half cases round exactly. */
internal const val STAGE_PANE_PERCENT = 42

/** The leading pane narrower than this starts its stage below the bar row (iOS `stageUnderBarWidth`). */
internal val StageUnderBarWidth = 300.dp

/** The action row's height at the foot of the leading pane (iOS `actionBar`). */
internal val StageActionRowHeight = 62.dp

/** The stage cover's height at scale 1: 168 dp wide at 2:3. */
internal val StageCoverHeight = 252.dp

/** The share of the free pane height the cover may take (iOS `stageCoverShare`). */
internal const val STAGE_COVER_SHARE = 0.44f

/** The stage's top padding below the status bar, when the cover rests in the bar row. */
internal val StageRestTop = 10.dp

/** The length of a fade, at the top or bottom of a pane. */
internal val EdgeFadeLength = 24.dp

/** How far a fade's clear edge reaches past the content it hides (iOS `barEdgeFade` reach). */
internal val EdgeFadeReach = 4.dp

/** How far the stage pane extends past its leading edge, so the cover's halo is not cut by the pane edge. */
internal val StageHaloOverhang = 64.dp

/**
 * The split of [width] between two panes, or null when the window stays one column. Two panes need a window wider
 * than it is tall and at least [TwoPaneMinWidth] wide.
 */
internal fun bookDetailPaneSplit(width: Dp, height: Dp): PaneSplit? =
    if (width > height && width >= TwoPaneMinWidth) splitAtShare(width) else null

/** The 42% split of [width], without the window guard, so tests can force two panes at any size. */
internal fun splitAtShare(width: Dp): PaneSplit {
    val leading = (width.value * STAGE_PANE_PERCENT / 100f).roundToInt().dp
    return PaneSplit(leading, width - leading)
}

/** Whether the stage rests in the bar row, given the leading pane's [leading] width. */
internal fun stageUnderBar(leading: Dp): Boolean = leading >= StageUnderBarWidth

/**
 * The stage cover's scale for a pane [paneHeight] tall. It shrinks to give back the height the action row and, when
 * the stage is not under the bar, the bar row take, and stops at half size.
 */
internal fun stageCoverScale(paneHeight: Dp, underBar: Boolean): Float {
    val free = paneHeight - StageActionRowHeight - (if (underBar) 0.dp else TopBarRowHeight)
    return (free.value * STAGE_COVER_SHARE / StageCoverHeight.value).coerceIn(0.5f, 1f)
}

/** The top fade of the stage pane: under the bar it sits on the stage's rest line, otherwise under the bar row. */
internal fun stageTopFadeBand(statusTop: Dp, underBar: Boolean): FadeBand =
    if (underBar) {
        FadeBand(statusTop + StageRestTop - EdgeFadeLength, statusTop + StageRestTop)
    } else {
        (statusTop + TopBarRowHeight - EdgeFadeReach).let { FadeBand(it, it + EdgeFadeLength) }
    }

/**
 * The top of the stage's content in a pane [paneHeight] tall, for content [content] tall. The content is centred
 * between the bar row and the action row when it fits there clear of the edge fades. Otherwise it rests at the
 * stage's usual top and the pane scrolls.
 */
internal fun stageContentTop(statusTop: Dp, underBar: Boolean, paneHeight: Dp, actionArea: Dp, content: Dp): Dp {
    val bandTop = statusTop + TopBarRowHeight
    val spare = paneHeight - actionArea - bandTop - content
    return if (spare >= (EdgeFadeLength - EdgeFadeReach) * 2) {
        bandTop + spare / 2
    } else {
        statusTop + StageRestTop + (if (underBar) 0.dp else TopBarRowHeight)
    }
}

/** The bottom fade of the stage pane, ending just above the action row. */
internal fun stageBottomFadeBand(paneHeight: Dp, actionArea: Dp): FadeBand =
    (paneHeight - actionArea + EdgeFadeReach).let { FadeBand(clear = it, opaque = it - EdgeFadeLength) }

/**
 * The content's offset under the strip, in px, for a column page at [scroll]. Zero until the strip reaches the pin
 * point, then it tracks the scroll. [stripTop] is negative until the host is placed.
 */
internal fun columnContentOffsetPx(scroll: Int, stripTop: Int, pinTop: Int): Int =
    if (stripTop < 0) 0 else max(0, scroll - (stripTop - pinTop))

/**
 * The column page's scroll value that shows the content at [offset] under the strip, clamped to [maxScroll]. An
 * offset of zero is the top of the page.
 */
internal fun columnScrollForContentOffset(offset: Int, stripTop: Int, pinTop: Int, maxScroll: Int): Int =
    if (offset <= 0) 0 else (stripTop - pinTop + offset).coerceIn(0, max(0, maxScroll))
