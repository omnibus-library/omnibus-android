package com.omnibus.omnibus.feature.bookdetail.wishlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.feature.bookdetail.more.BookMoreMenuEntry
import com.omnibus.omnibus.feature.bookdetail.more.LocalMoreMenuActivated

/**
 * The More menu's "Remove from wishlist" entry (#119): its row, and the confirmation it opens. The row is drawn only
 * while the book is on the wishlist.
 */
internal object WishlistMenuEntry : BookMoreMenuEntry {

    @Composable
    override fun Rows(context: BookDetailSectionContext, dismissMenu: () -> Unit) {
        val viewModel = hiltViewModel<WishlistRemovalViewModel, WishlistRemovalViewModel.Factory>(
            creationCallback = { it.create(context.book.uuid) },
        )
        val state by viewModel.uiState.collectAsState()
        WishlistRemoveRow(removeRow(state.state, state.isOnline, state.removing)) {
            dismissMenu()
            viewModel.openConfirm()
        }
    }

    @Composable
    override fun Overlay(context: BookDetailSectionContext) {
        if (!LocalMoreMenuActivated.current) return
        val viewModel = hiltViewModel<WishlistRemovalViewModel, WishlistRemovalViewModel.Factory>(
            creationCallback = { it.create(context.book.uuid) },
        )
        val state by viewModel.uiState.collectAsState()
        LaunchedEffect(context.reloadKey) { viewModel.pageReloaded(context.reloadKey) }
        WishlistRemovalOverlayContent(
            state = state,
            context = context,
            onConfirm = viewModel::confirmRemoval,
            onDismiss = viewModel::dismissConfirm,
            onOutcomeShown = viewModel::outcomeShown,
        )
    }
}
