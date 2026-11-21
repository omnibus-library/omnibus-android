package com.omnibus.omnibus.data.search

/** Searches the library's books for the command palette. Every failure is a result, so nothing throws except cancellation. */
interface LibrarySearchRepository {

    /** The library books matching [query], up to the server's first few hits, with the total number of matches. */
    suspend fun searchBooks(query: String): LibrarySearchResult
}
