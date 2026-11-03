package com.omnibus.omnibus.feature.bookdetail.reviews.sheet

import com.omnibus.omnibus.data.highlights.Highlight
import com.omnibus.omnibus.feature.bookdetail.highlights.HighlightRowModel
import com.omnibus.omnibus.feature.bookdetail.highlights.highlightChapterNumber
import com.omnibus.omnibus.feature.bookdetail.progress.BookChapter

/**
 * [quote] as a Markdown block quote, a blank line, then [body]. Line endings become `\n`, the quote is trimmed, and each
 * line loses its trailing spaces. A blank [body] gives the quote and a blank line only.
 */
fun quoteIntoReview(quote: String, body: String?): String {
    val lines = quote.replace("\r\n", "\n").replace('\r', '\n').trim().lines().map { it.trimEnd() }
    val block = lines.joinToString("\n") { if (it.isEmpty()) ">" else "> $it" }
    return if (body.isNullOrBlank()) "$block\n\n" else "$block\n\n$body"
}

/** The picker's rows: the highlights that have text, in their order, with chapter numbers from [chapters]. */
internal fun highlightPickerRows(highlights: List<Highlight>, chapters: List<BookChapter>): List<HighlightRowModel> =
    highlights.filter { !it.text.isNullOrBlank() }.map {
        HighlightRowModel(
            id = it.id,
            text = it.text!!.trim(),
            note = it.note?.takeIf { note -> note.isNotBlank() },
            color = it.color,
            chapterNumber = highlightChapterNumber(it, chapters),
            createdAtMillis = it.createdAtMillis,
        )
    }
