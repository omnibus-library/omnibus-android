package com.omnibus.omnibus.feature.bookdetail.journal

/** Test tags of the Journal list. Cards merge their descendants, so child tags need unmerged lookups. */
internal object JournalTestTags {
    const val SECTION = "book-detail-journal"
    const val HEADING = "book-detail-journal-heading"
    const val COUNT = "book-detail-journal-count"
    const val EMPTY = "book-detail-journal-empty"
    const val FAILED = "book-detail-journal-failed"
    const val RETRY = "book-detail-journal-retry"
    const val MORE = "book-detail-journal-more"
    const val CARD_PREFIX = "book-detail-journal-card-"

    fun card(id: Long) = "$CARD_PREFIX$id"
    fun byline(id: Long) = "book-detail-journal-byline-$id"
    fun preview(id: Long) = "book-detail-journal-preview-$id"
}
