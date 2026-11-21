package com.omnibus.omnibus.data.search

/** A library book a title search found. */
data class LibraryHit(
    val uuid: String,
    val title: String,
    val authorDisplay: String,
    val year: String?,
    val formats: List<String>,
    val coverUrl: String?,
    val accent: String?,
)

/**
 * The books a library search matched. [books] is the first few hits the server sent, and [total] counts every match,
 * so a caller can say how many were left out.
 */
data class LibraryBookMatches(val books: List<LibraryHit>, val total: Int)

/** What a library search answered. Every failure is a result, not an exception. */
sealed interface LibrarySearchResult {
    /** The server answered with a success status and a body that decoded. */
    data class Success(val matches: LibraryBookMatches) : LibrarySearchResult

    /** No server is saved, so no request was sent. */
    data object NoServer : LibrarySearchResult

    /** The request failed for [error]. */
    data class Failure(val error: LibrarySearchError) : LibrarySearchResult
}

/** Why a library search failed. */
sealed interface LibrarySearchError {
    /** The server could not be reached. */
    data object Unreachable : LibrarySearchError

    /** The server answered 401, so the session has expired. */
    data object SessionExpired : LibrarySearchError

    /** The server answered with a non-success [code]. */
    data class Http(val code: Int) : LibrarySearchError

    /** The response body could not be read, or the request failed for another reason. */
    data object UnexpectedResponse : LibrarySearchError
}
