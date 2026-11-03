package com.omnibus.omnibus.feature.bookdetail.overview.series

/** Test tags of the Series section. [card] is the node of one cover, unrotated, and merges its ribbon and text. */
internal object SeriesFanTestTags {
    const val SECTION = "series-fan-section"
    const val HEADING_ROW = "series-fan-heading-row"
    const val HEADING = "series-fan-heading"
    const val SERIES_PAGE_LINK = "series-fan-series-page"
    const val ROW = "series-fan-row"
    const val RIBBON = "series-fan-ribbon"

    /** The tag of the card for the book with [uuid]. */
    fun card(uuid: String) = "series-fan-card-$uuid"
}
