package com.omnibus.omnibus.feature.bookdetail.reviews

/** Test tags for the household reviews on the stage and on the Reviews tab. */
object ReviewsTestTags {
    const val STAGE_RATING = "book_detail_reviews_stage"
    const val SECTION = "book_detail_reviews_section"
    const val HOUSEHOLD_TITLE = "book_detail_reviews_household_title"
    const val COUNT = "book_detail_reviews_count"
    const val AVERAGE = "book_detail_reviews_average"

    /** The tag of the row for the reader with [userId]. */
    fun entry(userId: Long) = "book_detail_reviews_entry_$userId"

    /** The tag of the star row for the reader with [userId]. */
    fun stars(userId: Long) = "book_detail_reviews_stars_$userId"

    /** The tag of the review text for the reader with [userId]. */
    fun body(userId: Long) = "book_detail_reviews_body_$userId"

    /** The tag of the Read more button for the reader with [userId]. */
    fun readMore(userId: Long) = "book_detail_reviews_read_more_$userId"

    /** The tag of the Show review button for the reader with [userId]. */
    fun showReview(userId: Long) = "book_detail_reviews_show_review_$userId"
}
