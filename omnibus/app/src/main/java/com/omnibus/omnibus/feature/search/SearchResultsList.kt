package com.omnibus.omnibus.feature.search

import androidx.compose.foundation.lazy.LazyListScope
import com.omnibus.omnibus.data.search.GenreMatch
import com.omnibus.omnibus.data.search.LibraryHit
import com.omnibus.omnibus.data.search.TagMatch
import com.omnibus.omnibus.feature.search.rows.LibraryHitRow
import com.omnibus.omnibus.feature.search.rows.SearchGenreRow
import com.omnibus.omnibus.feature.search.rows.SearchTagRow
import com.omnibus.omnibus.feature.search.seeall.SearchSeeAllRoute

/**
 * One lazy item per section, in order. Each kind renders its own rows; later tickets add one branch. Its See all link
 * pushes the answered [query] for the section.
 */
internal fun LazyListScope.searchResults(sections: List<SearchSection>, query: String, onOpen: (SearchOpen) -> Unit) {
    sections.forEach { section ->
        item(key = section.kind.name, contentType = section.kind) {
            SearchSectionBlock(
                kind = section.kind,
                total = section.matches.total,
                shown = section.matches.items.size,
                onSeeAll = { onOpen(SearchOpen.Route(SearchSeeAllRoute(section.kind, query))) },
            ) {
                when (section.kind) {
                    SearchSectionKind.Books -> section.matches.items.filterIsInstance<LibraryHit>().forEach { hit ->
                        LibraryHitRow(hit, query, onOpen)
                    }
                    SearchSectionKind.Tags -> section.matches.items.filterIsInstance<TagMatch>().forEach { tag ->
                        SearchTagRow(tag, query, onOpen)
                    }
                    SearchSectionKind.Genres -> section.matches.items.filterIsInstance<GenreMatch>().forEach { genre ->
                        SearchGenreRow(genre, query, onOpen)
                    }
                }
            }
        }
    }
}
