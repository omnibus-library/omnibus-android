package com.omnibus.omnibus.feature.audiobook.player

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.theme.OmnibusTheme

/**
 * The "Can't play offline" dialog (R3, P-SCR-2), shown instead of the player when the book can't stream. The title is
 * named in the message when it is known. Styled like the library's Continue dialog.
 *
 * @param title The book's title, or blank when it isn't known.
 * @param onDismiss Called by OK, back and an outside tap.
 */
@Composable
internal fun PlayerOfflineDialog(title: String, onDismiss: () -> Unit) {
    val palette = OmnibusTheme.palette
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = palette.ink0),
            ) {
                Text(stringResource(R.string.audiobook_player_ok))
            }
        },
        title = {
            Text(stringResource(R.string.audiobook_player_offline_title))
        },
        text = {
            Text(
                if (title.isBlank()) {
                    stringResource(R.string.audiobook_player_offline_message_untitled)
                } else {
                    stringResource(R.string.audiobook_player_offline_message, title)
                },
            )
        },
        containerColor = palette.bg2,
        titleContentColor = palette.ink0,
        textContentColor = palette.ink1,
    )
}
