package com.omnibus.omnibus.feature.bookdetail.slots

import com.omnibus.omnibus.feature.bookdetail.BookDetailSection

/** The Files tab, in display order. */
internal val filesSections: List<BookDetailSection> = listOfNotNull(
    // #113 Ebook and Audiobook Downloads
    null,
    // #115 Link Ebook and Audiobook Positions
    null,
    // #3078 Book Requests: the kinds the library lacks
    com.omnibus.omnibus.feature.requests.start.RequestFilesSection,
    // #114 Book Details
    com.omnibus.omnibus.feature.bookdetail.files.details.BookDetailsSection,
)
