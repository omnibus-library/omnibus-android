package com.omnibus.omnibus.feature.bookdetail.slots

import com.omnibus.omnibus.feature.bookdetail.BookDetailSection

/** Above the title, under the cover. */
internal val stageAboveTitle: List<BookDetailSection> = listOfNotNull(
    // #119 Wishlist Books
    null,
)

/** The start of the status row under the byline. */
internal val stageStatusStart: List<BookDetailSection> = listOfNotNull(
    // #95 Set Reading Status
    null,
)

/** The end of the status row under the byline. */
internal val stageStatusEnd: List<BookDetailSection> = listOfNotNull(
    // #107 Household Ratings and Reviews
    null,
)

/** Under the byline, the full width of the stage. */
internal val stageProgress: List<BookDetailSection> = listOfNotNull(
    // #96 See Reading Progress
    null,
    // #97 Finish Card and Celebration
    null,
)

/** Over the cover, not tilted with it. */
internal val stageCoverOverlay: List<BookDetailSection> = listOfNotNull(
    // #97 Finish Card and Celebration
    null,
)
