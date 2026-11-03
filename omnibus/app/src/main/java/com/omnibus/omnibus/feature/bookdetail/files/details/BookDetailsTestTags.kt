package com.omnibus.omnibus.feature.bookdetail.files.details

/** Test tags for the Details list in the Files tab. */
internal object BookDetailsTestTags {
    const val SECTION = "book-detail-details"
    const val TITLE = "book-detail-details-title"

    fun row(row: DetailRow) = "book-detail-details-row-${row.name}"
    fun value(row: DetailRow) = "book-detail-details-value-${row.name}"
}
