package com.omnibus.omnibus.feature.reader.ui

/**
 * A label the reader's chrome can show. The UI maps each one to a string resource, so the rules
 * here stay testable without Android.
 */
sealed interface ReaderLabel {

    /** The book's title. */
    data class Title(val text: String) : ReaderLabel

    /** How many pages are left in the current chapter. */
    data class PagesLeftInChapter(val count: Int) : ReaderLabel

    /** The current chapter out of the total. */
    data class ChapterOf(val chapter: Int, val total: Int) : ReaderLabel

    /** The bare page number. */
    data class Page(val page: Int) : ReaderLabel

    /** The current page out of the total. */
    data class PageOf(val page: Int, val total: Int) : ReaderLabel

    /** Progress through the book, as a whole percent. */
    data class Percent(val pct: Int) : ReaderLabel
}

/**
 * The top and bottom labels for the reader's chrome, ported from iOS `ReaderIndicators.swift`.
 * Page numbers also need `page >= 1`, so the reader never shows page 0.
 *
 * With the chrome hidden, the top label is the title and the bottom one is the page. With it up,
 * the top label counts the pages left in the chapter and the bottom one reads "page of total".
 * Both are null while there is no location.
 */
data class ReaderIndicators(
    val location: RelocateData?,
    val title: String,
    val chromeVisible: Boolean,
) {
    /** The top label, or null when there is nothing to show. */
    val top: ReaderLabel? get() {
        val loc = location ?: return null
        if (!chromeVisible) return if (title.isBlank()) null else ReaderLabel.Title(title)
        return when {
            loc.chapterPagesLeft > 0 -> ReaderLabel.PagesLeftInChapter(loc.chapterPagesLeft)
            loc.chapter > 0 && loc.totalChapters > 0 -> ReaderLabel.ChapterOf(loc.chapter, loc.totalChapters)
            else -> null
        }
    }

    /** The bottom label. It falls back to progress when there are no page numbers. */
    val bottom: ReaderLabel? get() {
        val loc = location ?: return null
        val hasPageNumbers = loc.totalPages > 0 && loc.page >= 1
        return when {
            hasPageNumbers && chromeVisible -> ReaderLabel.PageOf(loc.page, loc.totalPages)
            hasPageNumbers -> ReaderLabel.Page(loc.page)
            loc.pct > 0 -> ReaderLabel.Percent(loc.pct)
            else -> null
        }
    }
}
