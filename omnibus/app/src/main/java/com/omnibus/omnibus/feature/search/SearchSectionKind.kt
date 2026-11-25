package com.omnibus.omnibus.feature.search

import androidx.annotation.StringRes
import com.omnibus.omnibus.R

/**
 * A section of the results, in display order (spec: Books, Authors, Series, Tags, Genres). Later kinds are added at
 * their spec position.
 */
enum class SearchSectionKind(@param:StringRes val label: Int) {
    Books(R.string.search_section_books),
}
