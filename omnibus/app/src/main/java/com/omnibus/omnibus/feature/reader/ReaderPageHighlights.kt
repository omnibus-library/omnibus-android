package com.omnibus.omnibus.feature.reader

import com.omnibus.omnibus.data.highlights.Highlight
import com.omnibus.omnibus.data.highlights.HighlightColor
import com.omnibus.omnibus.data.progress.PositionSync

/** A highlight as the page draws it: its EPUB CFI range, its colour, and whether it carries a note. */
data class PageHighlight(val cfiRange: String, val color: HighlightColor, val hasNote: Boolean)

/**
 * The highlights the page draws: the session's rows first, then the saved rows located by an EPUB CFI, one per range.
 * A saved row with no location, or a PDF or Kobo one, is never drawn (R14).
 */
fun pageHighlightsOf(saved: List<Highlight>, session: List<PageHighlight>): List<PageHighlight> =
    (session + saved.mapNotNull { row ->
        val cfi = row.location?.trim()?.takeIf { PositionSync.isEpubCfi(it) } ?: return@mapNotNull null
        PageHighlight(cfi, row.color, hasNote = !row.note.isNullOrBlank())
    }).distinctBy { it.cfiRange }
