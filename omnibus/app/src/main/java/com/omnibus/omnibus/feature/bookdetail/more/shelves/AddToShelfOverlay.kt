package com.omnibus.omnibus.feature.bookdetail.more.shelves

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.BookDetailMessage
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.ui.theme.rememberReducedMotion
import kotlinx.coroutines.launch

/**
 * Hosts the Add to shelf sheet and routes its failures (#116). While the sheet is open, a failed change shows in the
 * sheet's own snackbar host; once it is closed, the message goes to the page. Each failure is reported once, through
 * [onFailureShown] with its event id.
 *
 * @param onFailureShown Called once per failure, so the ViewModel can clear it.
 */
@Composable
internal fun AddToShelfOverlayContent(
    state: AddToShelfUIState,
    context: BookDetailSectionContext,
    onToggle: (Long) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onFailureShown: (Long) -> Unit,
    reducedMotion: Boolean = rememberReducedMotion(),
) {
    val sheetMessages = remember { SnackbarHostState() }
    // Outlives the effect, so clearing the failure does not cancel the message.
    val scope = rememberCoroutineScope()
    val failure = state.failure
    val text = failure?.let {
        stringResource(
            if (it.wasAdding) R.string.book_detail_more_shelf_add_failed else R.string.book_detail_more_shelf_remove_failed,
            it.shelfName,
        )
    }
    // Keyed on the event id only, so a recomposition never repeats the message.
    LaunchedEffect(failure?.eventId) {
        if (failure == null || text == null) return@LaunchedEffect
        if (state.sheetOpen) {
            scope.launch { sheetMessages.showSnackbar(text) }
        } else {
            context.onShowMessage(BookDetailMessage(text))
        }
        onFailureShown(failure.eventId)
    }
    if (state.sheetOpen) {
        AddToShelfSheet(
            content = state.content,
            book = context.book,
            onToggle = onToggle,
            onRetry = onRetry,
            onClose = onClose,
            snackbarHostState = sheetMessages,
            reducedMotion = reducedMotion,
        )
    }
}
