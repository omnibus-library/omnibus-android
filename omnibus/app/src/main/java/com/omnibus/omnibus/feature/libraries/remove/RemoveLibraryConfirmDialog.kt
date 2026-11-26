package com.omnibus.omnibus.feature.libraries.remove

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle

/**
 * The dialog that removes a library. It is one dialog for every [RemoveLibraryState] that is open, so the failure
 * state changes its content in place.
 *
 * @param name The library's display name, shown in the title and body.
 * @param onCancel Called by Cancel, Back or a tap outside, unless a removal is running.
 * @param onRemove Called by Remove in the confirm state.
 * @param onRemoveAnyway Called by Remove anyway in the failure state.
 */
@Composable
internal fun RemoveLibraryDialog(
    state: RemoveLibraryState,
    name: String,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    onRemoveAnyway: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state == RemoveLibraryState.Hidden) return
    val failed = state == RemoveLibraryState.SyncFailed || state == RemoveLibraryState.RemovingAnyway
    val removing = state == RemoveLibraryState.Syncing || state == RemoveLibraryState.RemovingAnyway
    val confirmTitle = stringResource(R.string.libraries_remove_confirm_title, name)
    val confirmBody = stringResource(R.string.libraries_remove_confirm_body, name)
    val failedTitle = stringResource(R.string.libraries_remove_failed_title, name)
    val failedBody = stringResource(R.string.libraries_remove_failed_body, name)
    RemoveLibraryAlert(
        title = if (failed) failedTitle else confirmTitle,
        body = if (failed) failedBody else confirmBody,
        confirmLabel = stringResource(if (failed) R.string.libraries_remove_anyway else R.string.libraries_remove_confirm),
        onConfirm = if (failed) onRemoveAnyway else onRemove,
        onCancel = onCancel,
        isRemoving = removing,
        modifier = modifier,
    )
}

/** The confirm dialog on its own: "Remove ‹name›?" with Cancel and Remove. */
@Composable
internal fun RemoveLibraryConfirmDialog(
    name: String,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    isRemoving: Boolean = false,
) {
    RemoveLibraryAlert(
        title = stringResource(R.string.libraries_remove_confirm_title, name),
        body = stringResource(R.string.libraries_remove_confirm_body, name),
        confirmLabel = stringResource(R.string.libraries_remove_confirm),
        onConfirm = onRemove,
        onCancel = onCancel,
        isRemoving = isRemoving,
        modifier = modifier,
    )
}

/** The failure dialog on its own: "Couldn't sync ‹name›" with Cancel and Remove anyway. */
@Composable
internal fun RemoveLibrarySyncFailedDialog(
    name: String,
    onCancel: () -> Unit,
    onRemoveAnyway: () -> Unit,
    modifier: Modifier = Modifier,
    isRemoving: Boolean = false,
) {
    RemoveLibraryAlert(
        title = stringResource(R.string.libraries_remove_failed_title, name),
        body = stringResource(R.string.libraries_remove_failed_body, name),
        confirmLabel = stringResource(R.string.libraries_remove_anyway),
        onConfirm = onRemoveAnyway,
        onCancel = onCancel,
        isRemoving = isRemoving,
        modifier = modifier,
    )
}

/**
 * The shared alert. While [isRemoving], the confirm button shows Removing with a spinner, Cancel is disabled, and Back
 * and taps outside do nothing.
 */
@Composable
private fun RemoveLibraryAlert(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    isRemoving: Boolean,
    modifier: Modifier,
) {
    val palette = OmnibusTheme.palette
    val removingLabel = stringResource(R.string.libraries_remove_removing)
    val cancelLabel = stringResource(R.string.libraries_remove_cancel)
    AlertDialog(
        onDismissRequest = { if (!isRemoving) onCancel() },
        properties = DialogProperties(
            dismissOnBackPress = !isRemoving,
            dismissOnClickOutside = !isRemoving,
        ),
        modifier = modifier,
        title = {
            Text(
                title,
                style = displayTextStyle(24, palette.ink0),
                modifier = Modifier
                    .testTag(RemoveLibraryTestTags.TITLE)
                    .semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
            )
        },
        text = {
            Text(body, style = uiTextStyle(16, FontWeight.Normal, palette.ink1))
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isRemoving,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(RemoveLibraryTestTags.CONFIRM),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = palette.badText,
                    disabledContentColor = palette.badText,
                ),
            ) {
                if (isRemoving) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = palette.badText,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(removingLabel)
                    }
                } else {
                    Text(confirmLabel)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                enabled = !isRemoving,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(RemoveLibraryTestTags.CANCEL),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = palette.accentText,
                    disabledContentColor = palette.accentText.copy(alpha = 0.38f),
                ),
            ) {
                Text(cancelLabel)
            }
        },
        containerColor = palette.bg2,
        titleContentColor = palette.ink0,
        textContentColor = palette.ink1,
    )
}
