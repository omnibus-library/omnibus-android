package com.omnibus.omnibus.feature.bookdetail.tabs

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.feature.bookdetail.BookDetailAnchor
import com.omnibus.omnibus.feature.bookdetail.BookDetailTab
import com.omnibus.omnibus.feature.bookdetail.BookDetailTabController
import com.omnibus.omnibus.ui.theme.Motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max
import kotlin.math.roundToInt

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

/** Moves the page's scroll value. Suspends so an animated scroll can be awaited and cancelled. */
fun interface PageScroller {
    suspend fun scrollTo(valuePx: Int)
}

/**
 * The page's tab controller. It owns the selected tab, the entry direction of the last change, and the anchor
 * registry the panels report into. Positions are in pixels: the host writes the strip's and the pin's geometry, and
 * [anchorTopInHost] is a test seam for an anchor's position (null uses the registry).
 */
@Stable
internal class BookDetailTabState(
    private val selectedState: MutableState<BookDetailTab>,
    private val scope: CoroutineScope,
    private val scroller: PageScroller,
    private val maxScroll: () -> Int,
    anchorTopInHost: ((BookDetailAnchor) -> Int?)? = null,
) : BookDetailTabController, BookDetailAnchorRegistry {

    override val selected: BookDetailTab get() = selectedState.value

    /** +1 when the last change moved right, -1 when it moved left, 0 when there was no entrance (open or restore). */
    var entryDirection by mutableIntStateOf(0)
        private set

    /** The tab whose panel is at rest, or null while its entrance runs. A cancelled entrance never sets it. */
    var settledTab: BookDetailTab? by mutableStateOf(selectedState.value)

    /** The strip's top in the page's content, or -1 until the host is placed. */
    var stripTopPx by mutableIntStateOf(-1)
    var pinTopPx by mutableIntStateOf(0)
    var stripHeightPx by mutableIntStateOf(0)
    var clearancePx by mutableIntStateOf(0)

    /** The host's coordinates, from which anchor positions are measured. Not state: only read when scrolling. */
    var hostCoordinates: LayoutCoordinates? = null

    private val anchors = mutableStateMapOf<BookDetailAnchor, LayoutCoordinates>()
    private val locate: (BookDetailAnchor) -> Int? = anchorTopInHost ?: { registryTop(it) }
    private var pending: Job? = null

    override fun select(tab: BookDetailTab) {
        pending?.cancel()
        change(tab)
    }

    override fun jumpTo(tab: BookDetailTab) {
        pending?.cancel()
        change(tab)
        pending = scope.launch { scrollStripToPin() }
    }

    override fun scrollTo(anchor: BookDetailAnchor) {
        pending?.cancel()
        val tab = anchor.tab
        change(tab)
        pending = scope.launch {
            val top = withTimeoutOrNull(ANCHOR_WAIT_MILLIS) {
                snapshotFlow { if (settledTab == tab) locate(anchor) else null }.filterNotNull().first()
            }
            if (top == null) {
                scrollStripToPin()
            } else {
                scroller.scrollTo(anchorScrollTarget(stripTopPx + top, pinTopPx, stripHeightPx, clearancePx, maxScroll()))
            }
        }
    }

    override fun register(anchor: BookDetailAnchor, coordinates: LayoutCoordinates) {
        anchors[anchor] = coordinates
    }

    override fun unregister(anchor: BookDetailAnchor, coordinates: LayoutCoordinates) {
        if (anchors[anchor] === coordinates) anchors.remove(anchor)
    }

    /** The coordinates currently registered for [anchor], or null. */
    fun registeredCoordinates(anchor: BookDetailAnchor): LayoutCoordinates? = anchors[anchor]

    private fun change(tab: BookDetailTab) {
        val direction = tabEntryDirection(selected, tab) ?: return
        entryDirection = direction
        settledTab = null
        selectedState.value = tab
    }

    private suspend fun scrollStripToPin() {
        if (stripTopPx >= 0) scroller.scrollTo(jumpScrollTarget(stripTopPx, pinTopPx, maxScroll()))
    }

    private fun registryTop(anchor: BookDetailAnchor): Int? {
        val host = hostCoordinates?.takeIf { it.isAttached } ?: return null
        val target = anchors[anchor]?.takeIf { it.isAttached } ?: return null
        return host.localPositionOf(target, Offset.Zero).y.roundToInt()
    }
}

/**
 * The page's real tab controller, kept for the life of the screen's nav entry. The selected tab is saved, so it
 * survives configuration change and returning from the reader; [reducedMotion] makes scrolls instant.
 */
@Composable
internal fun rememberBookDetailTabController(scrollState: ScrollState, reducedMotion: Boolean): BookDetailTabState {
    val selected = rememberSaveable { mutableStateOf(BookDetailTab.Overview) }
    val scope = rememberCoroutineScope()
    return remember(selected, scope, scrollState, reducedMotion) {
        BookDetailTabState(
            selectedState = selected,
            scope = scope,
            scroller = PageScroller { value ->
                if (reducedMotion) scrollState.scrollTo(value) else scrollState.animateScrollTo(value, Motion.settle())
            },
            maxScroll = { scrollState.maxValue },
        )
    }
}

/**
 * The minimum height of the tab host. It is the viewport minus the top bar and the action area, plus a little slack,
 * so the page can always scroll the strip up to its pin point whichever panel is shown.
 */
internal fun tabHostMinHeight(viewport: Dp, pinTop: Dp, actionArea: Dp): Dp =
    (viewport - pinTop - actionArea + TabHostSlack).coerceAtLeast(0.dp)
