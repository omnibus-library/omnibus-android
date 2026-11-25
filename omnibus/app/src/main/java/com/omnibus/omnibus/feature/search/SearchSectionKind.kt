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
    Authors(
        R.string.search_section_authors,
        R.string.search_see_all_authors_title,
        R.plurals.search_see_all_authors_link,
        R.plurals.search_see_all_authors_count,
    ),
    Series(
        R.string.search_section_series,
        R.string.search_see_all_series_title,
        R.plurals.search_see_all_series_link,
        R.plurals.search_see_all_series_count,
    ),
    Tags(
        R.string.search_section_tags,
        R.string.search_see_all_tags_title,
        R.plurals.search_see_all_tags_link,
        R.plurals.search_see_all_tags_count,
    ),
    Genres(
        R.string.search_section_genres,
        R.string.search_see_all_genres_title,
        R.plurals.search_see_all_genres_link,
        R.plurals.search_see_all_genres_count,
    ),
}
