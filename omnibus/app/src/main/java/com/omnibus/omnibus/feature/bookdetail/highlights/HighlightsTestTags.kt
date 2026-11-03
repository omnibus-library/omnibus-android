package com.omnibus.omnibus.feature.bookdetail.highlights

/** Test tags of the Highlights list. Rows merge their descendants, so child tags need unmerged lookups. */
internal object HighlightsTestTags {
    const val SECTION = "book-detail-highlights"
    const val HEADING = "book-detail-highlights-heading"
    const val COUNT = "book-detail-highlights-count"
    const val EMPTY = "book-detail-highlights-empty"
    const val FAILED = "book-detail-highlights-failed"
    const val RETRY = "book-detail-highlights-retry"
    const val MORE = "book-detail-highlights-more"
    const val ROW_PREFIX = "book-detail-highlights-row-"

    fun row(id: Long) = "$ROW_PREFIX$id"
    fun text(id: Long) = "book-detail-highlights-text-$id"
    fun noText(id: Long) = "book-detail-highlights-no-text-$id"
    fun note(id: Long) = "book-detail-highlights-note-$id"
    fun footerRow(id: Long) = "book-detail-highlights-footer-row-$id"
    fun footer(id: Long) = "book-detail-highlights-footer-$id"
    fun ribbon(id: Long) = "book-detail-highlights-ribbon-$id"
}
