package com.omnibus.omnibus.feature.bookdetail.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.omnibus.omnibus.feature.bookdetail.tabs.ANCHOR_WAIT_MILLIS
import com.omnibus.omnibus.feature.bookdetail.tabs.BookDetailTabState
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max

/**
 * The content offset carried across a layout change. [lastTwoPane] is the mode the page last settled in, or null
 * before its first layout. [contentOffsetPx] is the content's offset under the strip. Plain fields, not snapshot state,
 * so scrolling never recomposes.
 */
internal class BookDetailScrollHandoff(var lastTwoPane: Boolean? = null, var contentOffsetPx: Int = 0) {
    companion object {
        val Saver: Saver<BookDetailScrollHandoff, Any> = listSaver(
            save = {
                listOf(
                    when (it.lastTwoPane) {
                        null -> -1
                        false -> 0
                        true -> 1
                    },
                    it.contentOffsetPx,
                )
            },
            restore = {
                BookDetailScrollHandoff(
                    lastTwoPane = when (it[0]) {
                        -1 -> null
                        0 -> false
                        else -> true
                    },
                    contentOffsetPx = it[1],
                )
            },
        )
    }
}

/**
 * Carries the content offset across a change of layout, and keeps it recorded while the page is in one layout. After
 * a swap it waits, up to [ANCHOR_WAIT_MILLIS], for the new geometry and enough scroll range, then lands the offset
 * without animation. A same-layout recreation finds [BookDetailScrollHandoff.lastTwoPane] equal to [twoPane] and does
 * nothing.
 */
@Composable
internal fun BookDetailScrollHandoffEffect(
    handoff: BookDetailScrollHandoff,
    twoPane: Boolean,
    tabs: BookDetailTabState,
    pageScroll: ScrollState,
    panelScroll: ScrollState,
) {
    LaunchedEffect(twoPane, tabs) {
        val last = handoff.lastTwoPane
        if (last != null && last != twoPane) {
            val offset = handoff.contentOffsetPx
            // The target in the new layout, clamped to [limit]. Unclamped, it is the scroll the content needs.
            fun target(limit: Int): Int = if (twoPane) {
                offset.coerceIn(0, max(0, limit))
            } else {
                columnScrollForContentOffset(offset, tabs.stripTopPx, tabs.pinTopPx, limit)
            }
            val scroll = if (twoPane) panelScroll else pageScroll
            withTimeoutOrNull(ANCHOR_WAIT_MILLIS) {
                snapshotFlow { tabs.measuredTwoPane == twoPane && scroll.maxValue >= target(Int.MAX_VALUE) }.first { it }
            }
            if (tabs.measuredTwoPane == twoPane) scroll.scrollTo(target(scroll.maxValue))
        }
        handoff.lastTwoPane = twoPane
        snapshotFlow {
            when {
                tabs.measuredTwoPane != twoPane -> null
                twoPane -> panelScroll.value
                else -> columnContentOffsetPx(pageScroll.value, tabs.stripTopPx, tabs.pinTopPx)
            }
        }.filterNotNull().collect { handoff.contentOffsetPx = it }
    }
}
