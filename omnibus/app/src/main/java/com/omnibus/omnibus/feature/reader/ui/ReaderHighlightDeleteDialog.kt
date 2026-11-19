package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.theme.OmnibusTheme

/** Test tags on the note dialog's buttons. */
internal object ReaderHighlightDeleteDialogTags {
    const val CONFIRM = "reader_highlight_delete_confirm"
    const val CANCEL = "reader_highlight_delete_cancel"
}

/**
 * The confirmation for deleting a highlight with a note: a trash icon, "Delete this highlight?", Cancel and Delete, on the
 * app palette with [accent] for the icon and both buttons. Dismissing it, by the scrim or back, cancels.
 *
 * @param onConfirm Called when the reader confirms the delete.
 * @param onCancel Called when the reader cancels, or dismisses the dialog.
 */
@Composable
internal fun ReaderHighlightDeleteDialog(accent: Color, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val palette = OmnibusTheme.palette
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Icon(painter = painterResource(R.drawable.ic_reader_delete), contentDescription = null) },
        title = { Text(stringResource(R.string.reader_highlight_delete_title)) },
        text = { Text(stringResource(R.string.reader_highlight_delete_message)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = 48.dp).testTag(ReaderHighlightDeleteDialogTags.CONFIRM),
                colors = ButtonDefaults.textButtonColors(contentColor = accent),
            ) {
                Text(stringResource(R.string.reader_highlight_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.heightIn(min = 48.dp).testTag(ReaderHighlightDeleteDialogTags.CANCEL),
                colors = ButtonDefaults.textButtonColors(contentColor = accent),
            ) {
                Text(stringResource(R.string.reader_highlight_delete_cancel))
            }
        },
        containerColor = palette.bg2,
        iconContentColor = accent,
        titleContentColor = palette.ink0,
        textContentColor = palette.ink1,
    )
}
