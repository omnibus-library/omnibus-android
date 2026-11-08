package com.omnibus.omnibus.feature.bookdetail.ui

import androidx.compose.ui.semantics.SemanticsPropertyKey

/** Test tags for the Book Detail page, so UI tests can find its parts. */
internal object BookDetailTestTags {
    const val STAGE = "book-detail-stage"
    const val STAGE_PANE = "book-detail-stage-pane"
    const val WASH = "book-detail-wash"
    const val COVER = "book-detail-cover"
    const val TITLE = "book-detail-title"
    const val AUTHOR = "book-detail-author"
    const val YEAR = "book-detail-year"
    const val SERIES = "book-detail-series"
    const val STATUS_ROW = "book-detail-status-row"
    const val TOP_BAR = "book-detail-top-bar"
    const val BAR_TITLE = "book-detail-bar-title"
    const val SCROLL = "book-detail-scroll"
    const val TAB_AREA = "book-detail-tab-area"
    const val ACTION_AREA = "book-detail-action-area"
    const val READ_PILL = "book-detail-read-pill"
}

/** Whether the top bar is filled. A custom key, so accessibility services never see it. */
internal val BookDetailTopBarFilledKey = SemanticsPropertyKey<Boolean>("BookDetailTopBarFilled")
