package com.omnibus.omnibus.feature.bookdetail.more.bookmarks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext

/**
 * Hosts the Bookmarks sheet while it is open (#117). A tapped bookmark's destination goes through the page's
 * navigation, after the sheet has hidden.
 *
 * @param onRetry Reads the bookmarks again after a failure.
 * @param onClose Closes the sheet.
 */
@Composable
internal fun BookmarksOverlayContent(
    state: BookmarksUIState,
    context: BookDetailSectionContext,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    if (!state.sheetOpen) return
    val ribbon = remember(context.book) { bookmarkRibbonColor(context.book) }
    BookmarksSheet(
        content = state.content,
        nowMillis = state.nowMillis,
        ribbonColor = ribbon,
        onRetry = onRetry,
        onClose = onClose,
        onOpen = context.onNavigate,
    )
}
