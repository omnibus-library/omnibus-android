package com.omnibus.omnibus.feature.bookdetail.wishlist

import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.BookDetailMessage
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.feature.bookdetail.actionbar.hasReadableCopy
import com.omnibus.omnibus.feature.bookdetail.more.BookMoreMenuRow
import com.omnibus.omnibus.ui.theme.OmnibusTheme

/**
 * Shows a toast with the application context, so it outlives the page that asked for it. Used when a removal closes
 * the page, since the page's snackbar host goes with it.
 */
@Composable
internal fun rememberPlatformToast(): (String) -> Unit {
    val app = LocalContext.current.applicationContext
    return remember(app) { { text -> Toast.makeText(app, text, Toast.LENGTH_SHORT).show() } }
}

/**
 * The "Remove from wishlist" row in the More menu. It draws nothing when the row is [RemoveRow.Hidden]. Offline, the row
 * is disabled with the needs-connection sub-line.
 */
@Composable
internal fun WishlistRemoveRow(row: RemoveRow, onClick: () -> Unit) {
    if (row == RemoveRow.Hidden) return
    BookMoreMenuRow(
        label = stringResource(R.string.book_detail_wishlist_remove),
        icon = R.drawable.ic_book_detail_wishlist_remove,
        enabled = row == RemoveRow.Enabled,
        subLabel = if (row == RemoveRow.DisabledOffline) {
            stringResource(R.string.book_detail_more_needs_connection)
        } else {
            null
        },
        onClick = onClick,
        modifier = Modifier.testTag(WishlistTestTags.MENU_ROW),
    )
}

/**
 * Draws the removal confirmation and reports each removal outcome once (#119). A removal that leaves the page shows a
 * toast and closes it. Otherwise the removal shows in the page snackbar and reloads the page. A failure shows in the
 * page snackbar.
 *
 * @param onOutcomeShown Called once per outcome, so the ViewModel can clear it.
 * @param showToast Shows the message when the page is about to close. Tests pass a recording lambda.
 */
@Composable
internal fun WishlistRemovalOverlayContent(
    state: WishlistRemovalUIState,
    context: BookDetailSectionContext,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onOutcomeShown: (Long) -> Unit,
    showToast: (String) -> Unit = rememberPlatformToast(),
) {
    val palette = OmnibusTheme.palette
    if (state.confirmOpen) {
        val warning = removalWarning(hasReadableCopy(context.book))
        AlertDialog(
            onDismissRequest = onDismiss,
            modifier = Modifier.testTag(WishlistTestTags.DIALOG),
            title = { Text(stringResource(R.string.book_detail_wishlist_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        if (warning == RemovalWarning.KeepsBook) {
                            R.string.book_detail_wishlist_confirm_keeps_book
                        } else {
                            R.string.book_detail_wishlist_confirm_only_place
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirm,
                    modifier = Modifier.testTag(WishlistTestTags.CONFIRM),
                    colors = ButtonDefaults.textButtonColors(contentColor = palette.bad),
                ) {
                    Text(stringResource(R.string.book_detail_wishlist_confirm_remove))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag(WishlistTestTags.CANCEL),
                    colors = ButtonDefaults.textButtonColors(contentColor = palette.ink0),
                ) {
                    Text(stringResource(R.string.book_detail_wishlist_confirm_cancel))
                }
            },
            containerColor = palette.bg2,
            titleContentColor = palette.ink0,
            textContentColor = palette.ink1,
        )
    }

    val removedText = stringResource(R.string.book_detail_wishlist_removed)
    val failedText = stringResource(R.string.book_detail_wishlist_remove_failed)
    val outcome = state.outcome
    // Keyed on the event id only, so a recomposition never repeats the message.
    LaunchedEffect(outcome?.eventId) {
        if (outcome == null) return@LaunchedEffect
        when (outcome) {
            is RemovalOutcome.Removed -> {
                val book = context.book
                if (leavesAfterRemoval(outcome.bookDeleted, hasReadableCopy(book), book.hasPhysical)) {
                    showToast(removedText) // the page's snackbar host dies with the page
                    context.onClose()
                } else {
                    context.onShowMessage(BookDetailMessage(removedText))
                    context.onReloadBook()
                }
            }
            is RemovalOutcome.Failed -> context.onShowMessage(BookDetailMessage(failedText))
        }
        onOutcomeShown(outcome.eventId)
    }
}
