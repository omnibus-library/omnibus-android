package com.omnibus.omnibus.feature.audiobook.mini

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailRoute
import kotlin.math.abs

/** Whether the shell hides the mini player for the selected tab's top entry [top] (P-MINI-1): only on Book Detail. */
internal fun shellHidesMiniPlayer(top: NavKey?): Boolean = top is BookDetailRoute

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
