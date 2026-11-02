package com.omnibus.omnibus.feature.bookdetail.more.shelves

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.feature.bookdetail.more.BookMoreMenuEntry
import com.omnibus.omnibus.feature.bookdetail.more.BookMoreMenuRow
import com.omnibus.omnibus.feature.bookdetail.more.LocalMoreMenuActivated

/** The More menu's add-to-shelf entry (#116): its row, and the sheet it opens. */
internal object ShelfMenuEntry : BookMoreMenuEntry {

    @Composable
    override fun Rows(context: BookDetailSectionContext, dismissMenu: () -> Unit) {
        val viewModel = hiltViewModel<AddToShelfViewModel, AddToShelfViewModel.Factory>(
            creationCallback = { it.create(context.book.uuid) },
        )
        val state by viewModel.uiState.collectAsState()
        BookMoreMenuRow(
            label = stringResource(R.string.book_detail_more_add_to_shelf),
            icon = R.drawable.ic_book_detail_more_shelf,
            enabled = state.isOnline,
            subLabel = if (state.isOnline) null else stringResource(R.string.book_detail_more_needs_connection),
            onClick = {
                dismissMenu()
                viewModel.openSheet()
            },
        )
    }

    @Composable
    override fun Overlay(context: BookDetailSectionContext) {
        if (!LocalMoreMenuActivated.current) return
    }
}
