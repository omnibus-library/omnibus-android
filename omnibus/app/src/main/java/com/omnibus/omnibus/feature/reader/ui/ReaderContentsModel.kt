package com.omnibus.omnibus.feature.reader.ui

import com.omnibus.omnibus.data.bookmarks.Bookmark
import kotlin.math.roundToInt

/** A segment of the contents sheet. #57 adds Highlights and Notes. */
enum class ReaderContentsTab { Contents, Bookmarks }

/**
 * A bookmark row's label: its title, else its chapter title. Blank counts as absent. Null when neither is set, and the
 * sheet shows "Saved position" then.
 */
internal fun bookmarkLabel(bookmark: Bookmark): String? =
    bookmark.title?.trim()?.takeIf { it.isNotEmpty() } ?: bookmark.chapterTitle?.trim()?.takeIf { it.isNotEmpty() }

/** A bookmark's place in the book as a whole percent, 0 to 100, or null when the server could not place it. */
internal fun bookmarkPercent(bookmark: Bookmark): Int? =
    bookmark.fraction?.let { (it * 100).roundToInt().coerceIn(0, 100) }

/**
 * The index into [toc] of the reader's chapter. [RelocateData.chapter] is 1-based and 0 means no
 * entry matched, so this is `chapter - 1` when that is in range, and null otherwise.
 */
internal fun currentTocIndex(toc: List<TocEntry>, location: RelocateData?): Int? {
    val chapter = location?.chapter ?: return null
    return if (chapter in 1..toc.size) chapter - 1 else null
}

/** The page shown for an entry: only a positive page, never "0". Null when there is nothing to show. */
internal fun pageText(page: Int?): String? = page?.takeIf { it > 0 }?.toString()

/**
 * The header's position line, ported from iOS `positionLabel`. The UI maps each case to a string
 * resource.
 */
sealed interface ContentsPosition {

    /** The current page out of the book's total pages. */
    data class PageOf(val page: Int, val total: Int) : ContentsPosition

    /** Progress through the book, as a whole percent. */
    data class Percent(val pct: Int) : ContentsPosition

    companion object {

        /**
         * Page of total once the book has page numbers (`totalPages > 0` and `page >= 1`, as
         * [ReaderIndicators] does), otherwise the percent. Null without a location.
         */
        fun from(location: RelocateData?): ContentsPosition? = when {
            location == null -> null
            location.totalPages > 0 && location.page >= 1 -> PageOf(location.page, location.totalPages)
            else -> Percent(location.pct)
        }
    }
}
