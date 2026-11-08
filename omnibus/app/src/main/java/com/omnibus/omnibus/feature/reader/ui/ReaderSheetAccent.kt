package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.ui.graphics.Color
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.feature.bookdetail.stage.BookAccent
import com.omnibus.omnibus.feature.bookdetail.stage.bookAccent
import com.omnibus.omnibus.feature.library.ui.isLightGround
import com.omnibus.omnibus.feature.reader.ReaderBookState
import com.omnibus.omnibus.ui.theme.OmnibusPalette

/**
 * The accent for the reader's sheets: the open [book]'s cover accent, for the ground of [palette], the app theme the
 * sheet is wrapped in and never the reading theme. A book that isn't ready has no cover, so the app accent stands in.
 */
internal fun readerSheetAccent(book: ReaderBookState, palette: OmnibusPalette): BookAccent = when (book) {
    is ReaderBookState.Ready -> bookAccent(book.cover.tone, lightGround = isLightGround(palette.bg0))
    else -> BookAccent(tint = palette.accent, onTint = palette.accentInk, tintSoft = palette.accent.copy(alpha = 0.22f))
}

/**
 * The colour of the bookmark on the page: the open [book]'s cover accent on the ground of [theme], the reading theme
 * the page is drawn in. A book that isn't ready has no cover, so the app accent stands in, as for the sheets.
 */
internal fun readerPageAccent(book: ReaderBookState, theme: ReaderTheme): Color = when (book) {
    is ReaderBookState.Ready -> bookAccent(book.cover.tone, lightGround = theme.isLight).tint
    else -> OmnibusPalette.Atrium.accent
}
