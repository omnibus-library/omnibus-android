package com.omnibus.omnibus.feature.bookdetail.more.delete

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.BookDetailMessage
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.feature.bookdetail.wishlist.rememberPlatformToast

/**
 * Hosts the Delete sheet and reports each finished delete once. A whole-book delete shows a toast and closes the page,
 * since the page's snackbar goes with it. A partial delete shows a message and reloads the page.
 *
 * @param onOutcomeShown Called once per outcome, so the ViewModel can clear it.
 * @param showToast Shows the toast when the page is about to close. Tests pass a recording lambda.
 */
@Composable
internal fun DeleteOverlayContent(
    state: DeleteBookUIState,
    context: BookDetailSectionContext,
    callbacks: DeleteSheetCallbacks,
    onOutcomeShown: (Long) -> Unit,
    showToast: (String) -> Unit = rememberPlatformToast(),
) {
    if (state.sheet != DeleteSheet.Closed) {
        DeleteBookSheet(state, context.book, context.accent.tint, callbacks)
    }
    val bookDeletedText = stringResource(R.string.book_detail_delete_done_book)
    val outcome = state.outcome
    val doneText = outcome?.let { doneToastText(doneToast(it.deletedFiles, it.deletedCopies)) }
    // Keyed on the event id only, so a recomposition never repeats the message.
    LaunchedEffect(outcome?.eventId) {
        if (outcome == null) return@LaunchedEffect
        if (outcome.bookDeleted) {
            showToast(bookDeletedText) // the page's snackbar host dies with the page
            context.onClose()
        } else {
            context.onShowMessage(BookDetailMessage(doneText.orEmpty()))
            context.onReloadBook()
        }
        onOutcomeShown(outcome.eventId)
    }
}
