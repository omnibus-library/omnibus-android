package com.omnibus.omnibus.feature.bookdetail.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.BookDetailMessage

/**
 * Posts the snackbar for a failed save, once per failure. [onShown] clears the failure, so a later failure posts
 * again. Recomposing with the same failure does nothing.
 */
@Composable
internal fun ReadStatusSaveMessage(
    failure: ReadStatusSaveFailure?,
    onShowMessage: (BookDetailMessage) -> Unit,
    onShown: () -> Unit,
) {
    val offline = stringResource(R.string.book_detail_status_save_failed_offline)
    val other = stringResource(R.string.book_detail_status_save_failed)
    LaunchedEffect(failure) {
        val text = when (failure) {
            null -> return@LaunchedEffect
            ReadStatusSaveFailure.Offline -> offline
            ReadStatusSaveFailure.Other -> other
        }
        onShowMessage(BookDetailMessage(text))
        onShown()
    }
}
