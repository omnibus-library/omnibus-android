package com.omnibus.omnibus.feature.search

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.omnibus.omnibus.R
import kotlinx.serialization.Serializable

/**
 * A section of the results, in display order (spec: Books, Authors, Series, Tags, Genres). Later kinds are added at
 * their spec position, each with its own label, See all title and plurals (C6).
 */
@Serializable
enum class SearchSectionKind(
    @param:StringRes val label: Int,
    @param:StringRes val seeAllTitle: Int,
    @param:PluralsRes val seeAllLink: Int,
    @param:PluralsRes val seeAllCount: Int,
) {
    Books(
        R.string.search_section_books,
        R.string.search_see_all_books_title,
        R.plurals.search_see_all_books_link,
        R.plurals.search_see_all_books_count,
    ),
}
