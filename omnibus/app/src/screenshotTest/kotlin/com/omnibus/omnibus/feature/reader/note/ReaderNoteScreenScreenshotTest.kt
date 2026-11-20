package com.omnibus.omnibus.feature.reader.note

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.screenshot.Shot

private const val PASSAGE = "The house kept its own counsel, as houses in that street had learned to do."

/** The empty note screen over a highlighted passage, in light and dark. */
@PreviewTest
@PreviewLightDark
@Composable
fun ReaderNoteScreenEmpty() {
    Shot {
        ReaderNoteScreen(
            state = NoteComposerState(cfiRange = "epubcfi(/6/4!/4/2,/1:0,/1:20)", quote = PASSAGE),
            onTextChanged = {},
            onSave = {},
            onClose = {},
            onDiscardConfirmed = {},
            onDiscardCancelled = {},
            onDelete = {},
            onDeleteConfirmed = {},
            onDeleteCancelled = {},
        )
    }
}

/** Text near the cap, with the counter and the save error, in light and dark. */
@PreviewTest
@PreviewLightDark
@Composable
fun ReaderNoteScreenCounterAndError() {
    Shot {
        ReaderNoteScreen(
            state = NoteComposerState(
                cfiRange = "epubcfi(/6/4!/4/2,/1:0,/1:20)",
                quote = PASSAGE,
                text = "x".repeat(3_962),
                failed = true,
            ),
            onTextChanged = {},
            onSave = {},
            onClose = {},
            onDiscardConfirmed = {},
            onDiscardCancelled = {},
            onDelete = {},
            onDeleteConfirmed = {},
            onDeleteCancelled = {},
        )
    }
}

/** A wide window with text: the quote and the field sit in a centred column, in light and dark. */
@PreviewTest
@Preview(name = "Light", widthDp = 840, heightDp = 600)
@Preview(name = "Dark", widthDp = 840, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ReaderNoteScreenWide() {
    Shot {
        ReaderNoteScreen(
            state = NoteComposerState(
                cfiRange = "epubcfi(/6/4!/4/2,/1:0,/1:20)",
                quote = PASSAGE,
                text = "A note that runs on for a while, so the wide column has something to hold.",
            ),
            onTextChanged = {},
            onSave = {},
            onClose = {},
            onDiscardConfirmed = {},
            onDiscardCancelled = {},
            onDelete = {},
            onDeleteConfirmed = {},
            onDeleteCancelled = {},
        )
    }
}

/** Editing an existing note, unchanged so Save is off, with the trash button, in light and dark. */
@PreviewTest
@PreviewLightDark
@Composable
fun ReaderNoteScreenEdit() {
    val note = "Echoes the porch scene in chapter two. Compare with the letter she burns later, and check whether the " +
        "street is named again."
    Shot {
        ReaderNoteScreen(
            state = NoteComposerState(
                cfiRange = "epubcfi(/6/4!/4/2,/1:0,/1:20)",
                quote = PASSAGE,
                text = note,
                existing = note,
            ),
            onTextChanged = {},
            onSave = {},
            onClose = {},
            onDiscardConfirmed = {},
            onDiscardCancelled = {},
            onDelete = {},
            onDeleteConfirmed = {},
            onDeleteCancelled = {},
        )
    }
}
