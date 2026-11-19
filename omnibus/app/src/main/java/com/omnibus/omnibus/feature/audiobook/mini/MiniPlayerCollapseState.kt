package com.omnibus.omnibus.feature.audiobook.mini

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity

/** Whether the docked strip is collapsed, driven by the shell content's scrolling through [connection] (#286 R4). */
@Stable
internal class MiniPlayerCollapseState(private val thresholdPx: Float) {
    private var rule = MiniPlayerCollapse()

    /** Read by the strip. Only this is snapshot state, so a scroll frame that does not flip it recomposes nothing. */
    var collapsed by mutableStateOf(false)
        private set

    /** Observes only: it consumes nothing, so lists, top app bars and pull-to-refresh behave as before. */
    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            rule = rule.afterScroll(consumed.y, thresholdPx)
            collapsed = rule.collapsed
            return Offset.Zero
        }
    }

    /** Back to expanded, with no scroll carried over (#286 R7). */
    fun expand() {
        rule = MiniPlayerCollapse()
        collapsed = false
    }
}

/** A [MiniPlayerCollapseState] whose threshold is [MiniPlayerCollapseThreshold] in this density (#286 R4). */
@Composable
internal fun rememberMiniPlayerCollapseState(): MiniPlayerCollapseState {
    val thresholdPx = with(LocalDensity.current) { MiniPlayerCollapseThreshold.toPx() }
    return remember(thresholdPx) { MiniPlayerCollapseState(thresholdPx) }
}
