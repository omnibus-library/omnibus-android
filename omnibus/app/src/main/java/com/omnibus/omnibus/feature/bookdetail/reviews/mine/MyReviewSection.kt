package com.omnibus.omnibus.feature.bookdetail.reviews.mine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.feature.bookdetail.reviews.sheet.rememberReviewSheetLauncher
import com.omnibus.omnibus.feature.bookdetail.reviews.showsReviews

/**
 * The reader's own review, at the top of the Reviews tab. It renders nothing for a book with nothing to read, before it
 * creates its ViewModel, so such a book needs no Hilt. Its pencil and the prompt open the review sheet (#109).
 */
internal object MyReviewSection : BookDetailSection {

    @Composable
    override fun Content(context: BookDetailSectionContext, modifier: Modifier) {
        if (!showsReviews(context.book)) return
        val viewModel = hiltViewModel<MyReviewViewModel, MyReviewViewModel.Factory>(
            key = "my-review-${context.book.uuid}",
            creationCallback = { it.create(context.book.uuid) },
        )
        LaunchedEffect(context.reloadKey) { viewModel.reload(context.reloadKey) }
        val state by viewModel.uiState.collectAsState()
        MyReviewSaveMessage(state.saveFailure, context.onShowMessage, viewModel::saveFailureShown)
        MyReviewCard(
            model = state.card,
            tint = context.accent.tint,
            onRate = viewModel::rate,
            onDelete = viewModel::delete,
            onCompose = rememberReviewSheetLauncher(context.book.uuid),
            modifier = modifier,
        )
    }
}
