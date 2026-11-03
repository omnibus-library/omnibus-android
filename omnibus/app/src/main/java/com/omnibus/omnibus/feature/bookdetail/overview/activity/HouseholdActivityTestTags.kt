package com.omnibus.omnibus.feature.bookdetail.overview.activity

/** Test tags for the Activity section. */
object HouseholdActivityTestTags {
    const val SECTION = "book_detail_activity"
    const val TITLE = "book_detail_activity_title"

    /** The tag of the row for the reader with [userId]. */
    fun row(userId: Long) = "book_detail_activity_row_$userId"
}
