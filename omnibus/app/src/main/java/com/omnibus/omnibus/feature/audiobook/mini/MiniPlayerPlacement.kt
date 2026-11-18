package com.omnibus.omnibus.feature.audiobook.mini

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.navigation3.runtime.NavKey
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailRoute
import kotlin.math.abs

/** Whether the shell hides the mini player for the selected tab's top entry [top] (P-MINI-1): only on Book Detail. */
internal fun shellHidesMiniPlayer(top: NavKey?): Boolean = top is BookDetailRoute

/** Where the shell floats the mini player card (#271): side margin, widest card, gap below it. */
internal data class MiniPlayerFloatPlacement(val sideMargin: Dp, val maxWidth: Dp, val bottomGap: Dp) {
    /** The card's width in [available] width: the width less a margin each side, capped at [maxWidth], never negative. */
    fun cardWidth(available: Dp): Dp = min(maxWidth, available - sideMargin * 2).coerceAtLeast(0.dp)

    companion object {
        /** Below 600dp: inset 12dp each side, 8dp above the tab bar, no cap. */
        val Bar = MiniPlayerFloatPlacement(sideMargin = 12.dp, maxWidth = Dp.Infinity, bottomGap = 8.dp)

        /** From 600dp: centred beside the rail, at most 560dp wide, 16dp above the navigation bar. */
        val Rail = MiniPlayerFloatPlacement(sideMargin = 16.dp, maxWidth = 560.dp, bottomGap = 16.dp)
    }
}

/** How far a list must scroll one way before the docked strip changes state (#286 R4). */
internal val MiniPlayerCollapseThreshold: Dp = 16.dp

/**
 * The docked strip's collapse state while lists scroll (#286 R4). [travel] is the distance scrolled, in px, in the
 * direction that would flip [collapsed]: negative toward collapsing, positive toward expanding, zero when none.
 */
internal data class MiniPlayerCollapse(val collapsed: Boolean = false, val travel: Float = 0f) {
    /**
     * The state after a list consumed [consumedDy] px (Compose's sign: negative while content moves up). Only what a
     * list consumed counts, so overscroll and pull-to-refresh never flip it.
     */
    fun afterScroll(consumedDy: Float, thresholdPx: Float): MiniPlayerCollapse {
        if (consumedDy == 0f || consumedDy.isNaN()) return this
        val towardCollapse = consumedDy < 0f
        if (towardCollapse == collapsed) return copy(travel = 0f)
        val sameWay = travel != 0f && (travel < 0f) == towardCollapse
        val total = if (sameWay) travel + consumedDy else consumedDy
        return if (abs(total) >= thresholdPx) MiniPlayerCollapse(collapsed = towardCollapse) else copy(travel = total)
    }
}

/** The rail block's two layouts (#286 R8, R11). */
internal enum class RailMiniPlayerVariant { Full, Compact }

/** [RailMiniPlayerVariant.Full] when the full block fits in the [space] the rail's tabs leave, else compact. */
internal fun railMiniPlayerVariant(space: Dp, fullBlockHeight: Dp): RailMiniPlayerVariant =
    if (fullBlockHeight <= space) RailMiniPlayerVariant.Full else RailMiniPlayerVariant.Compact
