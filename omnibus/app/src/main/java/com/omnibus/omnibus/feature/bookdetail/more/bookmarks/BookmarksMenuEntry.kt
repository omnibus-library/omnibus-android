package com.omnibus.omnibus.feature.bookdetail.more.bookmarks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.feature.bookdetail.more.BookMoreMenuEntry
import com.omnibus.omnibus.feature.bookdetail.more.BookMoreMenuRow
import com.omnibus.omnibus.feature.bookdetail.more.LocalMoreMenuActivated

/**
 * The More menu's bookmarks entry (#117): its row, and the sheet it opens. The row joins the Add to shelf group above it,
 * so the two share one section.
 */
internal object BookmarksMenuEntry : BookMoreMenuEntry {

    override val joinsPreviousGroup: Boolean get() = true

    @Composable
    override fun Rows(context: BookDetailSectionContext, dismissMenu: () -> Unit) {
        if (!showsBookmarksRow(context.book)) return
        val viewModel = hiltViewModel<BookmarksViewModel, BookmarksViewModel.Factory>(
            creationCallback = { it.create(context.book.uuid) },
        )
        BookmarksMenuRows(context.book) {
            dismissMenu()
            viewModel.openSheet()
        }
    }

    @Composable
    override fun Overlay(context: BookDetailSectionContext) {
        if (!LocalMoreMenuActivated.current) return
        val viewModel = hiltViewModel<BookmarksViewModel, BookmarksViewModel.Factory>(
            creationCallback = { it.create(context.book.uuid) },
        )
        val state by viewModel.uiState.collectAsState()
        BookmarksOverlayContent(
            state = state,
            context = context,
            onRetry = viewModel::retry,
            onClose = viewModel::closeSheet,
        )
    }
}

/** The Bookmarks row for [book], shown only for a book with files. It emits nothing otherwise. */
@Composable
internal fun BookmarksMenuRows(book: Book, onOpen: () -> Unit) {
    if (!showsBookmarksRow(book)) return
    BookmarksMenuRow(onOpen)
}

/** The Bookmarks row. It is always enabled: an offline open shows the sheet's error state. */
@Composable
internal fun BookmarksMenuRow(onClick: () -> Unit) {
    BookMoreMenuRow(
        label = stringResource(R.string.book_detail_bookmarks_title),
        icon = R.drawable.ic_reader_bookmark,
        enabled = true,
        subLabel = null,
        onClick = onClick,
        modifier = Modifier.testTag(BookmarksTestTags.MENU_ROW),
    )
}
