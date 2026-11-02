package com.omnibus.omnibus.feature.bookdetail.tabs

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
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
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import kotlin.math.roundToInt

/**
 * The tab strip and its panel, mounted as one child of the page's scrolling column below the stage. The strip pins
 * under the top bar as the page scrolls. The host is at least [minHeight] tall, so a tab change never moves the
 * scroll value while the strip is at or past its pin point.
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
) {
    val density = LocalDensity.current
    SideEffect {
        with(density) {
            state.pinTopPx = pinTop.roundToPx()
            state.stripHeightPx = TabStripHeight.roundToPx()
            state.clearancePx = AnchorClearance.roundToPx()
        }
    }
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .onPlaced {
                // The host is a child of the scrolled column, so this position does not change with scroll.
                state.hostCoordinates = it
                state.stripTopPx = it.positionInParent().y.roundToInt()
            }
            .testTag(BookDetailTestTags.TAB_AREA),
    ) {
        BookTabStrip(
            state = state,
            scrollState = scrollState,
            ribbonColor = context.accent.tint,
            reducedMotion = reducedMotion,
            modifier = Modifier.zIndex(1f),
        )
        BookTabPanel(
            state = state,
            context = context,
            slots = slots,
            minHeight = (minHeight - TabStripHeight).coerceAtLeast(0.dp),
            reducedMotion = reducedMotion,
        )
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
