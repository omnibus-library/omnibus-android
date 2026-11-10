package com.omnibus.omnibus.feature.audiobook.mini

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.navigation3.runtime.NavKey
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailRoute

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
