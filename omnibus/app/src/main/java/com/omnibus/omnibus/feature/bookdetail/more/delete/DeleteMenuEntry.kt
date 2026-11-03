package com.omnibus.omnibus.feature.bookdetail.more.delete

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.feature.bookdetail.more.BookMoreMenuEntry
import com.omnibus.omnibus.feature.bookdetail.more.LocalMoreMenuActivated

/** The More menu's "Delete files…" or "Delete record…" entry (#121): its row, and the sheet it opens. */
internal object DeleteMenuEntry : BookMoreMenuEntry {

    @Composable
    override fun Rows(context: BookDetailSectionContext, dismissMenu: () -> Unit) {
        val viewModel = hiltViewModel<DeleteBookViewModel, DeleteBookViewModel.Factory>(
            creationCallback = { it.create(context.book.uuid) },
        )
        val state by viewModel.uiState.collectAsState()
        val bookHasFiles = hasFiles(context.book)
        DeleteMenuRow(
            row = deleteRow(state.isAdmin, state.isOnline, bookHasFiles),
            onClick = {
                dismissMenu()
                viewModel.openSheet(bookHasFiles)
            },
        )
    }

    @Composable
    override fun Overlay(context: BookDetailSectionContext) {
        if (!LocalMoreMenuActivated.current) return
        val viewModel = hiltViewModel<DeleteBookViewModel, DeleteBookViewModel.Factory>(
            creationCallback = { it.create(context.book.uuid) },
        )
        val state by viewModel.uiState.collectAsState()
        val callbacks = remember(viewModel) {
            DeleteSheetCallbacks(
                onToggleFile = viewModel::toggleFile,
                onToggleCopy = viewModel::toggleCopy,
                onToggleAll = viewModel::toggleAll,
                onContinue = viewModel::continueToConfirm,
                onBack = viewModel::back,
                onConfirm = viewModel::confirmDelete,
                onClose = viewModel::closeSheet,
                onRetry = viewModel::retryLoad,
            )
        }
        DeleteOverlayContent(
            state = state,
            context = context,
            callbacks = callbacks,
            onOutcomeShown = viewModel::outcomeShown,
        )
    }
}
