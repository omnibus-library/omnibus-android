package com.omnibus.omnibus.feature.reader.ui

/** A segment of the contents sheet. #57 adds Highlights and Notes. */
enum class ReaderContentsTab { Contents, Bookmarks }

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
