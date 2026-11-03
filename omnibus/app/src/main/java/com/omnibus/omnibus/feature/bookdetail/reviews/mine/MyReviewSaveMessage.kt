package com.omnibus.omnibus.feature.bookdetail.reviews.mine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.BookDetailMessage

/**
 * Posts the snackbar for a failed rating or delete, once per failure. [onShown] clears the failure, so a later failure
 * posts again. Recomposing with the same failure does nothing.
 */
@Composable
internal fun MyReviewSaveMessage(
    failure: MyReviewSaveFailure?,
    onShowMessage: (BookDetailMessage) -> Unit,
    onShown: () -> Unit,
) {
    val rateOffline = stringResource(R.string.book_detail_rate_save_failed_offline)
    val rate = stringResource(R.string.book_detail_rate_save_failed)
    val deleteOffline = stringResource(R.string.book_detail_rate_delete_failed_offline)
    val delete = stringResource(R.string.book_detail_rate_delete_failed)
    LaunchedEffect(failure) {
        val text = when {
            failure == null -> return@LaunchedEffect
            failure.kind == MyReviewSaveFailure.Kind.Rate -> if (failure.offline) rateOffline else rate
            else -> if (failure.offline) deleteOffline else delete
        }
        onShowMessage(BookDetailMessage(text))
        onShown()
    }
}
