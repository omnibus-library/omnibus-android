package com.omnibus.omnibus.feature.bookdetail.slots

import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.more.BookMoreButton

/** The trailing actions of the top bar. */
internal val topBarActions: List<BookDetailSection> = listOfNotNull(
    // #116 Add to Shelf
    BookMoreButton,
)
