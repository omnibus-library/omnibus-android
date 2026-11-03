package com.omnibus.omnibus.feature.bookdetail.slots

import com.omnibus.omnibus.feature.bookdetail.BookDetailSection

/** The Reviews tab, in display order. */
internal val reviewsSections: List<BookDetailSection> = listOfNotNull(
    // #108 Rate a Book
    com.omnibus.omnibus.feature.bookdetail.reviews.mine.MyReviewSection,
    // #107 Household Ratings and Reviews
    com.omnibus.omnibus.feature.bookdetail.reviews.HouseholdReviewsSection,
)
