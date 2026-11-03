package com.omnibus.omnibus.feature.bookdetail.reviews.sheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.feature.bookdetail.reviews.showsReviews

/**
 * The review sheet (#109), mounted over the whole page. It renders nothing, and creates no ViewModel, for a book with
 * nothing to read.
 */
internal object ReviewSheetSection : BookDetailSection {

    @Composable
    override fun Content(context: BookDetailSectionContext, modifier: Modifier) {
        if (!showsReviews(context.book)) return
        val viewModel = reviewSheetViewModel(context.book.uuid)
        val state by viewModel.uiState.collectAsState()
        if (!state.open) return
        val actions = remember(viewModel) {
            ReviewSheetActions(
                onCancel = viewModel::requestClose,
                onSave = viewModel::save,
                onRatingChange = viewModel::setRating,
                onTextChange = viewModel::setText,
                onNotesChange = viewModel::setNotes,
                onSpoilerChange = viewModel::setSpoiler,
                onExpandNotes = viewModel::expandNotes,
                onOpenPicker = viewModel::openPicker,
                onDiscard = viewModel::discard,
                onKeepEditing = viewModel::keepEditing,
            )
        }
        ReviewSheet(
            state = state,
            book = context.book,
            actions = actions,
            onRequestClose = viewModel::requestClose,
            onClosed = viewModel::closed,
            onPick = viewModel::pickHighlight,
            onRetryHighlights = viewModel::retryHighlights,
            onClosePicker = viewModel::closePicker,
        )
    }
}
