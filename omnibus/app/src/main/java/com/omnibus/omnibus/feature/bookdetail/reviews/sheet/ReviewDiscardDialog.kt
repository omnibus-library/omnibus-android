package com.omnibus.omnibus.feature.bookdetail.reviews.sheet

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.theme.OmnibusTheme

/**
 * The confirmation for dropping a changed draft: "Discard your review?", with Discard and Keep editing. Dismissing it,
 * by the scrim or back, keeps editing; it never discards.
 *
 * @param onDiscard Called when the reader confirms the discard.
 * @param onKeepEditing Called when the reader keeps the draft, or dismisses the dialog.
 */
@Composable
internal fun ReviewDiscardDialog(onDiscard: () -> Unit, onKeepEditing: () -> Unit) {
    val palette = OmnibusTheme.palette
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text(stringResource(R.string.book_detail_review_sheet_discard_title)) },
        text = { Text(stringResource(R.string.book_detail_review_sheet_discard_message)) },
        confirmButton = {
            TextButton(
                onClick = onDiscard,
                modifier = Modifier.heightIn(min = 48.dp).testTag(ReviewSheetTestTags.DISCARD),
                colors = ButtonDefaults.textButtonColors(contentColor = palette.bad),
            ) {
                Text(stringResource(R.string.book_detail_review_sheet_discard_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onKeepEditing,
                modifier = Modifier.heightIn(min = 48.dp).testTag(ReviewSheetTestTags.KEEP_EDITING),
                colors = ButtonDefaults.textButtonColors(contentColor = palette.ink0),
            ) {
                Text(stringResource(R.string.book_detail_review_sheet_discard_keep))
            }
        },
        containerColor = palette.bg2,
        titleContentColor = palette.ink0,
        textContentColor = palette.ink1,
    )
}
