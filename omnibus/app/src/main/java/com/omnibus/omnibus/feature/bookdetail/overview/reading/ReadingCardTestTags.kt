package com.omnibus.omnibus.feature.bookdetail.overview.reading

/** Test tags for the Your reading card. */
object ReadingCardTestTags {
    const val CARD = "book_detail_reading_card"
    const val TITLE = "book_detail_reading_title"
    const val LINE = "book_detail_reading_line"

    /** The tag of the row of [kind]. */
    fun row(kind: ReadingRowKind) = "book_detail_reading_row_${kind.name}"
}
