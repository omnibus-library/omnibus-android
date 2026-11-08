package com.omnibus.omnibus.feature.bookdetail.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.feature.bookdetail.BookDetailTab
import com.omnibus.omnibus.screenshot.Shot

/** The strip well below the pin point: the 28 dp ribbon hangs under Overview and nothing glows. */
@PreviewTest
@PreviewLightDark
@Composable
fun BookTabStripResting() {
    StripShot(stripTopPx = 2_000, scroll = 0)
}

/** The strip scrolled one pixel past the pin: the ribbon is retracted and Overview glows over the bar-coloured ground. */
@PreviewTest
@PreviewLightDark
@Composable
fun BookTabStripPinned() {
    StripShot(stripTopPx = 0, scroll = 1)
}

/**
 * The strip in two-pane mode: the scroll that would pin it in column mode leaves it unpinned, with its ribbon out and a
 * hairline inset under the cells.
 */
@PreviewTest
@PreviewLightDark
@Composable
fun BookTabStripUnpinned() {
    StripShot(stripTopPx = 0, scroll = 1, pins = false)
}

@Composable
private fun StripShot(stripTopPx: Int, scroll: Int, pins: Boolean = true) {
    Shot {
        val scrollState = rememberScrollState(scroll)
        val scope = rememberCoroutineScope()
        val state = remember {
            BookDetailTabState(
                selectedState = mutableStateOf(BookDetailTab.Overview),
                scope = scope,
                scroller = {},
                maxScroll = { 0 },
            )
        }
        state.stripTopPx = stripTopPx
        state.pinTopPx = 0
        Column {
            BookTabStrip(state, scrollState, ribbonColor = Color(0xFFB5452B), reducedMotion = true, pins = pins)
            // Room for the resting ribbon, which hangs below the strip.
            Spacer(Modifier.height(48.dp))
        }
    }
}
