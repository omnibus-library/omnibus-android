package com.omnibus.omnibus.data.scan

/** A library book an ISBN matched. [isbn] is the book's own ISBN, which can differ from the one in hand. */
data class ScanBook(
    val uuid: String,
    val title: String,
    val authors: List<String>,
    val coverUrl: String?,
    val hasPhysical: Boolean,
    val hasFiles: Boolean,
    val isbn: String?,
)

/** What an ISBN matched, as `POST api/scan/resolve` answers. */
sealed interface ScanOutcome {
    /** The reader already has a physical copy of [book]. */
    data class AlreadyOwned(val book: ScanBook) : ScanOutcome

    /** [book] is on the reader's wishlist. */
    data class OnWishlist(val book: ScanBook) : ScanOutcome

    /** [book] is in the library as a digital copy only, so a physical copy can be checked in. */
    data class InLibraryUnowned(val book: ScanBook) : ScanOutcome

    /** Any answer this version can't act on yet: not in the library, a close match, unresolved, or an unknown kind. */
    data object Unplaced : ScanOutcome
}

/** The result of one scan request. Only cancellation escapes as an exception. */
sealed interface ScanResult<out T> {
    /** The server answered with a success status and a body that decoded. */
    data class Success<T>(val value: T) : ScanResult<T>

    /** No server is saved, so no request was sent. */
    data object NoServer : ScanResult<Nothing>

    /** The request failed for [error]. */
    data class Failure(val error: ScanError) : ScanResult<Nothing>
}

/** Why a scan request failed. Strings are chosen in the UI, not here. */
sealed interface ScanError {
    /** The server could not be reached. */
    data object Unreachable : ScanError

    /** The server answered 503 because the book providers are down. */
    data object ProvidersUnreachable : ScanError

    /** The server answered 400 and rejected the request. */
    data object Rejected : ScanError

    /** The server answered 401, so the session has expired. */
    data object SessionExpired : ScanError

    /** The server answered 404. */
    data object NotFound : ScanError

    /** The server answered with another non-success [code]. */
    data class Http(val code: Int) : ScanError

    /** The response body could not be read, or the request failed for another reason. */
    data object UnexpectedResponse : ScanError
}
