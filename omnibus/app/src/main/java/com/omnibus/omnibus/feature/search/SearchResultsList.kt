package com.omnibus.omnibus.feature.search

import androidx.compose.foundation.lazy.LazyListScope
import com.omnibus.omnibus.data.search.LibraryHit
import com.omnibus.omnibus.feature.search.rows.LibraryHitRow

/** One lazy item per section, in order. Each kind renders its own rows; later tickets add one branch. */
internal fun LazyListScope.searchResults(sections: List<SearchSection>, query: String, onOpen: (SearchOpen) -> Unit) {
    sections.forEach { section ->
        item(key = section.kind.name, contentType = section.kind) {
            SearchSectionBlock(section.kind) {
                when (section.kind) {
                    SearchSectionKind.Books -> section.matches.items.filterIsInstance<LibraryHit>().forEach { hit ->
                        LibraryHitRow(hit, query, onOpen)
                    }
                }
            }
        }
    }
}
