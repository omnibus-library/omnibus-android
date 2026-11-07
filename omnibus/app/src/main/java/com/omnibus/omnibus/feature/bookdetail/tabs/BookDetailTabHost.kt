package com.omnibus.omnibus.feature.bookdetail.tabs

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import com.omnibus.omnibus.feature.bookdetail.stage.rememberBookAccent
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailTestTags
import com.omnibus.omnibus.feature.bookdetail.ui.EdgeFadeLength
import com.omnibus.omnibus.feature.bookdetail.ui.FadeBand
import com.omnibus.omnibus.feature.bookdetail.ui.edgeFade
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import kotlin.math.roundToInt

/**
 * The tab strip and its panel. In column mode it is one child of the page's scrolling column below the stage: the
 * strip pins under the top bar as the page scrolls, and the host is at least [minHeight] tall, so a tab change never
 * moves the scroll value while the strip is at or past its pin point. In [twoPane] mode it fills the trailing pane:
 * the strip stays fixed under the bar and the panel scrolls in the viewport below it, with [panelScrollState].
 *
 * @param pinTop The bottom of the top bar, measured from the top of the screen.
 * @param minHeight The host's minimum height, see [tabHostMinHeight].
 */
@Composable
internal fun BookDetailTabHost(
    state: BookDetailTabState,
    context: BookDetailSectionContext,
    slots: BookDetailSlots,
    scrollState: ScrollState,
    pinTop: Dp,
    minHeight: Dp,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    twoPane: Boolean = false,
    panelScrollState: ScrollState = scrollState,
) {
    val density = LocalDensity.current
    // Built here, unconditionally, and chosen below, so the mode never changes the composition's groups.
    val panelScroll = Modifier.verticalScroll(panelScrollState)
    SideEffect {
        state.twoPane = twoPane
        with(density) {
            state.clearancePx = AnchorClearance.roundToPx()
            if (twoPane) {
                state.pinTopPx = 0
                state.stripHeightPx = 0
                state.stripTopPx = 0
            } else {
                state.pinTopPx = pinTop.roundToPx()
                state.stripHeightPx = TabStripHeight.roundToPx()
            }
        }
    }
    Column(
        modifier
            .then(
                if (twoPane) {
                    Modifier.fillMaxSize().padding(top = pinTop)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = minHeight)
                        .onPlaced {
                            // The host is a child of the scrolled column, so this position does not change with scroll.
                            state.hostCoordinates = it
                            state.stripTopPx = it.positionInParent().y.roundToInt()
                            state.measuredTwoPane = false
                        }
                },
            )
            .testTag(BookDetailTestTags.TAB_AREA),
    ) {
        BookTabStrip(
            state = state,
            scrollState = scrollState,
            ribbonColor = context.accent.tint,
            reducedMotion = reducedMotion,
            pins = !twoPane,
            modifier = Modifier.zIndex(1f),
        )
        // The viewport wraps the panel in both modes, so the panel's key path never changes.
        val viewport = if (twoPane) {
            Modifier.weight(1f).edgeFade(top = FadeBand(0.dp, EdgeFadeLength), bottom = null).then(panelScroll)
        } else {
            Modifier
        }
        Box(viewport.testTag(BookDetailTabsTestTags.PANEL_VIEWPORT)) {
            Box(
                Modifier.onPlaced {
                    if (twoPane) {
                        state.hostCoordinates = it
                        state.measuredTwoPane = true
                    }
                },
            ) {
                BookTabPanel(
                    state = state,
                    context = context,
                    slots = slots,
                    minHeight = if (twoPane) 0.dp else (minHeight - TabStripHeight).coerceAtLeast(0.dp),
                    reducedMotion = reducedMotion,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun BookDetailTabHostPreview() {
    val book = Book(
        id = 1,
        uniqueIdentifier = "uuid-1",
        filename = "the-great-gatsby.epub",
        title = "The Great Gatsby",
        creators = listOf(Creator("F. Scott Fitzgerald", "author", null, 7L)),
        coverUrl = null,
        accent = null,
        formats = listOf("epub"),
        series = null,
        seriesIndex = null,
        hasPhysical = false,
        addedAt = null,
        lastInteractedAt = null,
        bookFiles = emptyList(),
    )
    OmnibusTheme {
        val scrollState = rememberScrollState()
        val tabs = rememberBookDetailTabController(scrollState, reducedMotion = true)
        val context = BookDetailSectionContext(
            book = book,
            accent = rememberBookAccent(book),
            reloadKey = 0,
            tabs = tabs,
            onNavigate = {},
            onShowMessage = {},
            onBookChanged = {},
            onReloadBook = {},
            onClose = {},
        )
        BookDetailTabHost(
            state = tabs,
            context = context,
            slots = BookDetailSlots(),
            scrollState = scrollState,
            pinTop = 0.dp,
            minHeight = 0.dp,
            reducedMotion = true,
        )
    }
}
