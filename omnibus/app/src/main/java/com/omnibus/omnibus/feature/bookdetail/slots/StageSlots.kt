package com.omnibus.omnibus.feature.bookdetail.slots

import com.omnibus.omnibus.feature.bookdetail.BookDetailSection

/** Above the title, under the cover. */
internal val stageAboveTitle: List<BookDetailSection> = listOfNotNull(
    // #119 Wishlist Books
    com.omnibus.omnibus.feature.bookdetail.wishlist.WishlistStageSection,
)

/** The start of the status row under the byline. */
internal val stageStatusStart: List<BookDetailSection> = listOfNotNull(
    // #95 Set Reading Status
    com.omnibus.omnibus.feature.bookdetail.status.ReadStatusSection,
)

/** The end of the status row under the byline. */
internal val stageStatusEnd: List<BookDetailSection> = listOfNotNull(
    // #107 Household Ratings and Reviews
    com.omnibus.omnibus.feature.bookdetail.reviews.StageRatingSection,
)

/** Under the byline, the full width of the stage. */
internal val stageProgress: List<BookDetailSection> = listOfNotNull(
    // #96 See Reading Progress
    com.omnibus.omnibus.feature.bookdetail.progress.ReadingProgressSection,
    // #97 Finish Card and Celebration
    com.omnibus.omnibus.feature.bookdetail.finish.FinishCardSection,
)

/** Over the cover, not tilted with it. */
internal val stageCoverOverlay: List<BookDetailSection> = listOfNotNull(
    // #97 Finish Card and Celebration
    com.omnibus.omnibus.feature.bookdetail.finish.FinishCelebrationSection,
)
