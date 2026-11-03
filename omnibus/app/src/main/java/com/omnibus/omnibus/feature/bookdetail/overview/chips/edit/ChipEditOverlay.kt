package com.omnibus.omnibus.feature.bookdetail.overview.chips.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.metadata.MetadataListKind
import com.omnibus.omnibus.feature.bookdetail.BookDetailMessage
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext

/**
 * Hosts the chip editor sheet and routes its results. A saved list is adopted by the page once. A failed save shows
 * inside the open sheet, or on the page once the sheet is closed. Each event is acknowledged so it happens once.
 *
 * @param autoFocus Whether the sheet's field asks for focus when the sheet opens.
 */
@Composable
internal fun ChipEditOverlay(editor: ChipEditor, context: BookDetailSectionContext, autoFocus: Boolean = true) {
    val state = editor.state
    val openKind = state.openKind
    // Lives for one opening of the sheet: it resets when the sheet closes or reopens.
    var inlineError by remember(openKind) { mutableStateOf<String?>(null) }

    val update = state.bookUpdate
    // Keyed on the id only, so the merged book is forwarded once.
    LaunchedEffect(update?.eventId) {
        if (update == null) return@LaunchedEffect
        context.onBookChanged(update.book)
        inlineError = null
        editor.bookUpdateApplied(update.eventId)
    }

    val failure = state.failure
    val failureText = failure?.let {
        stringResource(
            if (it.kind == MetadataListKind.Genres) R.string.book_detail_chipedit_failed_genres
            else R.string.book_detail_chipedit_failed_tags,
        )
    }
    // Keyed on the event id only, so a recomposition never repeats the message.
    LaunchedEffect(failure?.eventId) {
        if (failure == null || failureText == null) return@LaunchedEffect
        if (openKind != null) inlineError = failureText
        else context.onShowMessage(BookDetailMessage(failureText))
        editor.failureShown(failure.eventId)
    }

    if (openKind != null) {
        ChipEditSheet(
            state = state,
            kind = openKind,
            bookTitle = context.book.displayTitle,
            inlineError = inlineError,
            onRemove = { editor.remove(openKind, it) },
            onAdd = { editor.add(openKind, it) },
            onEntryChange = editor::updateEntry,
            onClose = editor::close,
            autoFocus = autoFocus,
        )
    }
}
