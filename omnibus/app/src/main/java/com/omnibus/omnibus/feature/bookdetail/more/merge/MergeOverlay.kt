package com.omnibus.omnibus.feature.bookdetail.more.merge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.BookDetailMessage
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.feature.bookdetail.more.BookMoreMenuRow

/**
 * The "Merge with…" row in the More menu. It draws nothing when the row is [MergeRow.Hidden]. Offline, the row is disabled
 * with the needs-connection sub-line.
 */
@Composable
internal fun MergeMenuRow(row: MergeRow, onClick: () -> Unit) {
    if (row == MergeRow.Hidden) return
    BookMoreMenuRow(
        label = stringResource(R.string.book_detail_merge_menu_row),
        icon = R.drawable.ic_book_detail_merge,
        enabled = row == MergeRow.Enabled,
        subLabel = if (row == MergeRow.DisabledOffline) {
            stringResource(R.string.book_detail_more_needs_connection)
        } else {
            null
        },
        onClick = onClick,
        modifier = Modifier.testTag(MergeTestTags.MENU_ROW),
    )
}

/**
 * Hosts the Merge with… sheet and reports each merge or undo outcome once (#120). A merge posts a held "Books merged"
 * message with Undo and reloads the page. An undo posts its result, and a failed undo offers a retry only while the handle
 * is still usable.
 *
 * @param onUndo Undoes the merge with that handle.
 * @param onOutcomeShown Called once per outcome, so the ViewModel can clear it.
 */
@Composable
internal fun MergeOverlayContent(
    state: MergeUIState,
    context: BookDetailSectionContext,
    actions: MergeSheetActions,
    onUndo: (Long) -> Unit,
    onOutcomeShown: (Long) -> Unit,
) {
    val done = stringResource(R.string.book_detail_merge_done)
    val undoLabel = stringResource(R.string.book_detail_merge_undo)
    val undone = stringResource(R.string.book_detail_merge_undone)
    val undoFailed = stringResource(R.string.book_detail_merge_undo_failed)
    val retry = stringResource(R.string.book_detail_merge_retry)
    val expired = stringResource(R.string.book_detail_merge_undo_expired)
    val outcome = state.outcome
    // Keyed on the event id only, so a recomposition or reload never repeats the message.
    LaunchedEffect(outcome?.eventId) {
        if (outcome == null) return@LaunchedEffect
        when (outcome) {
            is MergeOutcome.Merged -> {
                val id = outcome.mergeLogId
                context.onShowMessage(BookDetailMessage(done, undoLabel, { onUndo(id) }, holdUntilDismissed = true))
                context.onReloadBook()
            }
            is MergeOutcome.Undone -> {
                context.onShowMessage(BookDetailMessage(undone))
                context.onReloadBook()
            }
            is MergeOutcome.UndoFailed -> {
                val id = outcome.mergeLogId
                context.onShowMessage(
                    if (outcome.retryable) {
                        BookDetailMessage(undoFailed, retry, { onUndo(id) }, holdUntilDismissed = true)
                    } else {
                        BookDetailMessage(expired)
                    },
                )
            }
        }
        onOutcomeShown(outcome.eventId)
    }
    if (state.sheetOpen) {
        MergeSheet(state = state, target = context.book, actions = actions)
    }
}
