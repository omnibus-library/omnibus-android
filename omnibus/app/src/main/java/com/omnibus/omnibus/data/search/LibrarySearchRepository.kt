package com.omnibus.omnibus.data.search

/**
 * Searches the library through `GET api/search/palette`, for the command palette, the scan picker and the Search tab.
 * Every failure is a result, so nothing throws except cancellation.
 */
interface LibrarySearchRepository {

    /** The library books matching [query], up to the server's first few hits, with the total number of matches. */
    suspend fun searchBooks(query: String): LibrarySearchResult

    /** Every section of the library matching [query], up to the server's default rows per section, with each section's total. */
    suspend fun searchLibrary(query: String): PaletteSearchResult
}
