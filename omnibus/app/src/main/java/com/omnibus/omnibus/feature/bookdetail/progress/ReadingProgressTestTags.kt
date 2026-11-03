package com.omnibus.omnibus.feature.bookdetail.progress

/** Test tags of the reading progress section. */
internal object ReadingProgressTestTags {
    const val BAR = "book-detail-progress-bar"
    const val TRACK = "book-detail-progress-track"
    const val FLAG = "book-detail-progress-flag"
    const val STEM = "book-detail-progress-stem"
    const val LINE = "book-detail-progress-line"
    const val IN_PROGRESS = "book-detail-progress-in-progress"
    const val OFFLINE_NOTE = "book-detail-progress-offline"

    /** The segment at [index] in the bar, from the left. */
    fun segment(index: Int) = "book-detail-progress-segment-$index"
}
