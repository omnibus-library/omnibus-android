package com.omnibus.omnibus.feature.bookdetail.more.merge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.feature.bookdetail.more.BookMoreMenuEntry
import com.omnibus.omnibus.feature.bookdetail.more.LocalMoreMenuActivated

/** The More menu's "Merge with…" entry (#120): its admin-only row, and the merge sheet it opens. */
internal object MergeMenuEntry : BookMoreMenuEntry {

    @Composable
    override fun Rows(context: BookDetailSectionContext, dismissMenu: () -> Unit) {
        val viewModel = hiltViewModel<MergeViewModel, MergeViewModel.Factory>(
            creationCallback = { it.create(context.book.uuid) },
        )
        val state by viewModel.uiState.collectAsState()
        MergeMenuRow(mergeRow(state.isAdmin, context.book.bookFiles.isNotEmpty(), state.isOnline)) {
            dismissMenu()
            viewModel.openSheet()
        }
    }

    @Composable
    override fun Overlay(context: BookDetailSectionContext) {
        if (!LocalMoreMenuActivated.current) return
        val viewModel = hiltViewModel<MergeViewModel, MergeViewModel.Factory>(
            creationCallback = { it.create(context.book.uuid) },
        )
        val state by viewModel.uiState.collectAsState()
        val actions = remember(viewModel) {
            MergeSheetActions(
                onQueryChange = viewModel::onQueryChange,
                onRetrySearch = viewModel::retrySearch,
                onChoose = viewModel::choose,
                onBack = viewModel::back,
                onConfirm = viewModel::confirmMerge,
                onClose = viewModel::closeSheet,
            )
        }
        MergeOverlayContent(state, context, actions, onUndo = viewModel::undo, onOutcomeShown = viewModel::outcomeShown)
    }
}
