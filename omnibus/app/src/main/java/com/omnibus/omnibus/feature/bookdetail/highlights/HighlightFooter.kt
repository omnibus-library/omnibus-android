package com.omnibus.omnibus.feature.bookdetail.highlights

import com.omnibus.omnibus.data.highlights.Highlight
import com.omnibus.omnibus.feature.bookdetail.format.BookDetailFormat
import com.omnibus.omnibus.feature.bookdetail.progress.BookChapter
import com.omnibus.omnibus.feature.bookdetail.progress.chapterNumberAt
import java.util.Locale
import java.util.TimeZone

/**
 * The 1-based chapter number of an epub [highlight], or null when it can't be placed. Chapters that share the start of the
 * placed one give the lowest index, as the server's `chapter_title` does.
 */
internal fun highlightChapterNumber(highlight: Highlight, chapters: List<BookChapter>): Int? {
    if (highlight.format != "epub") return null
    return chapterNumberAt(highlight.fraction, chapters)
}

/**
 * The footer of a highlight: "Chapter N · Oct 24", either part alone, or null when there is neither.
 *
 * @property chapterLabel Formats the chapter number. The default is "Chapter N".
 * @property separator Joins the two parts.
 */
internal fun highlightFooter(
    chapterNumber: Int?,
    createdAtMillis: Long?,
    nowMillis: Long,
    locale: Locale,
    timeZone: TimeZone,
    chapterLabel: (Int) -> String = { "Chapter $it" },
    separator: String = " · ",
): String? = listOfNotNull(
    chapterNumber?.let(chapterLabel),
    createdAtMillis?.let { BookDetailFormat.shortDate(it, nowMillis, locale, timeZone) },
).takeIf { it.isNotEmpty() }?.joinToString(separator)
