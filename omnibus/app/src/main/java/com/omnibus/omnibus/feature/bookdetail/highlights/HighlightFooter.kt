package com.omnibus.omnibus.feature.bookdetail.highlights

import com.omnibus.omnibus.data.highlights.Highlight
import com.omnibus.omnibus.feature.bookdetail.format.BookDetailFormat
import com.omnibus.omnibus.feature.bookdetail.progress.BookChapter
import com.omnibus.omnibus.feature.bookdetail.progress.chapterAt
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/** Chapter starts closer than this are the same start (C8's tolerance). */
internal const val HIGHLIGHT_CHAPTER_EPSILON = 1e-4f

/**
 * The 1-based chapter number of an epub [highlight], or null when it can't be placed. Chapters that share the start of the
 * placed one give the lowest index, as the server's `chapter_title` does, because [chapterAt] alone returns the later one.
 */
internal fun highlightChapterNumber(highlight: Highlight, chapters: List<BookChapter>): Int? {
    if (highlight.format != "epub") return null
    val fraction = highlight.fraction?.takeIf { !it.isNaN() } ?: return null
    val hit = chapters.chapterAt(fraction) ?: return null
    return chapters
        .filter { abs(it.startFraction - hit.startFraction) <= HIGHLIGHT_CHAPTER_EPSILON }
        .minOf { it.index } + 1
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
