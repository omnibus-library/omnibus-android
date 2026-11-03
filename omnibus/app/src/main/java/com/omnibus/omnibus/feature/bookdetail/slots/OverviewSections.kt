package com.omnibus.omnibus.feature.bookdetail.slots

import com.omnibus.omnibus.feature.bookdetail.BookDetailSection

/** The Overview tab, in display order. */
internal val overviewSections: List<BookDetailSection> = listOfNotNull(
    // #94 Book Tabs and Description
    com.omnibus.omnibus.feature.bookdetail.overview.description.BookDescriptionSection,
    // #100 Genres and Tags
    com.omnibus.omnibus.feature.bookdetail.overview.chips.GenresTagsSection,
    // #102 Your Reading Card
    null,
    // #103 Household Activity
    com.omnibus.omnibus.feature.bookdetail.overview.activity.HouseholdActivitySection,
    // #104 Series Fan
    com.omnibus.omnibus.feature.bookdetail.overview.series.SeriesFanSection,
    // #105 More by This Author
    com.omnibus.omnibus.feature.bookdetail.overview.author.MoreByAuthorSection,
    // #106 Readers Also Read
    null,
)
