package com.omnibus.omnibus.data.search

/** One section of a whole-library search: the first [items] the server sent and the [total] that match. */
data class SearchMatches<T>(val items: List<T>, val total: Int) {
    companion object {
        fun <T> empty(): SearchMatches<T> = SearchMatches(emptyList(), 0)
    }
}

/** Every section a whole-library search answered. Later tickets add one defaulted section each. */
data class LibrarySearchResults(
    val books: SearchMatches<LibraryHit>,
    val authors: SearchMatches<AuthorMatch> = SearchMatches.empty(),
    val series: SearchMatches<SeriesMatch> = SearchMatches.empty(),
    val tags: SearchMatches<TagMatch> = SearchMatches.empty(),
    val genres: SearchMatches<GenreMatch> = SearchMatches.empty(),
)

/** What a whole-library search answered. Every failure is a result, not an exception. */
sealed interface PaletteSearchResult {
    /** The server answered with a success status and a body that decoded. */
    data class Success(val results: LibrarySearchResults) : PaletteSearchResult

    /** No server is saved, so no request was sent. */
    data object NoServer : PaletteSearchResult

    /** The request failed for [error]. */
    data class Failure(val error: LibrarySearchError) : PaletteSearchResult
}
