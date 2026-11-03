package com.omnibus.omnibus.feature.bookdetail.slots

import com.omnibus.omnibus.feature.bookdetail.BookDetailSection

/** The Highlights tab, in display order. */
internal val highlightsSections: List<BookDetailSection> = listOfNotNull(
    // #110 Highlights List
    com.omnibus.omnibus.feature.bookdetail.highlights.HighlightsSection,
    // #112 Journal List
    com.omnibus.omnibus.feature.bookdetail.journal.JournalSection,
)
