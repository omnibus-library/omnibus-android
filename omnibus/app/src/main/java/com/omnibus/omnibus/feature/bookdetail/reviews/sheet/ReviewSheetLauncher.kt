package com.omnibus.omnibus.feature.bookdetail.reviews.sheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

/**
 * The book's one review sheet ViewModel. The launcher and the sheet's overlay share it through the key, in the same nav
 * entry, so a launch and the sheet it opens see one draft.
 */
@Composable
internal fun reviewSheetViewModel(bookUuid: String): ReviewSheetViewModel =
    hiltViewModel<ReviewSheetViewModel, ReviewSheetViewModel.Factory>(
        key = "review-sheet-$bookUuid",
        creationCallback = { it.create(bookUuid) },
    )

/**
 * Opens the review sheet for [bookUuid] (C15). The sheet lives in the page's screen overlays, so it opens from any tab,
 * and only once the reader's review has loaded; before that the call does nothing. Only books with something to read
 * mount the overlay. Needs a Hilt host.
 */
@Composable
fun rememberReviewSheetLauncher(bookUuid: String): () -> Unit {
    val viewModel = reviewSheetViewModel(bookUuid)
    return remember(viewModel) { { viewModel.open() } }
}
