package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.feature.reader.ReaderBookState
import com.omnibus.omnibus.screenshot.Shot
import com.omnibus.omnibus.ui.theme.OmnibusTheme

/** The note confirmation on the app palette, with a book's accent. */
@PreviewTest
@PreviewLightDark
@Composable
fun ReaderHighlightDeleteDialogShown() {
    Shot {
        ReaderHighlightDeleteDialog(
            accent = readerSheetAccent(ReaderBookState.Ready(title = "The Quiet House"), OmnibusTheme.palette).tint,
            onConfirm = {},
            onCancel = {},
        )
    }
}
