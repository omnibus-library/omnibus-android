package com.omnibus.omnibus.data.requests

/**
 * Book requests, one function per route. Every call is a command or a live read: none is queued offline. Every failure is a
 * result, so nothing throws except cancellation.
 */
interface RequestsRepository {

    /** Every reader's requests, newest first. */
    suspend fun list(): RequestsResult<List<RequestListing>>

    /** One book's requests, newest first. */
    suspend fun bookList(uuid: String): RequestsResult<List<RequestListing>>

    /** One request with every version and any cover offer. */
    suspend fun request(id: Long): RequestsResult<BookRequest>

    /** The readers who have the book on a wishlist; empty when no wishlist row matches. */
    suspend fun wanters(uuid: String): RequestsResult<List<Wanter>>

    /** Starts a request of [kind] on a library book. A 200 (already going) and a 201 (new) both succeed. */
    suspend fun start(uuid: String, kind: RequestKind): RequestsResult<BookRequest>

    /** Starts an ebook request on a book made from [book]'s record, sent back to the server untouched. */
    suspend fun startFromRecord(book: ProviderBook): RequestsResult<BookRequest>

    /** Picks a version. The body is exactly the version id, since the server denies unknown fields. */
    suspend fun choose(id: Long, resultId: Long): RequestsResult<BookRequest>

    /** Gives up the downloading version and goes back to choosing among the others. */
    suspend fun swap(id: Long): RequestsResult<BookRequest>

    suspend fun retry(id: Long): RequestsResult<BookRequest>

    suspend fun cancel(id: Long): RequestsResult<BookRequest>

    /** Takes a cancel back, until the server's `cancel_at`. */
    suspend fun undoCancel(id: Long): RequestsResult<BookRequest>

    /** Replaces the book's cover with the one the download brought, and drops the cached cover so it shows. */
    suspend fun takeCover(id: Long): RequestsResult<BookRequest>

    /** Keeps the book's current cover and drops the offer. */
    suspend fun keepCover(id: Long): RequestsResult<BookRequest>

    /** Library books matching [query], from the search palette. */
    suspend fun searchLibrary(query: String): RequestsResult<List<LibraryHit>>

    /** Provider records matching [query]. */
    suspend fun searchProviders(query: String): RequestsResult<List<ProviderBook>>

    /** Matches a provider record against the library. */
    suspend fun resolve(book: ProviderBook): RequestsResult<ScanMatch>
}

/** The outcome of one [RequestsRepository] call. */
sealed interface RequestsResult<out T> {
    data class Success<T>(val value: T) : RequestsResult<T>

    /** No server is saved, so no request was made. */
    data object NoServer : RequestsResult<Nothing>

    data class Failure(val error: RequestsError) : RequestsResult<Nothing>
}

/** Why a request call failed. The UI layer decides what to say for each. */
sealed interface RequestsError {
    /** The server could not be reached. */
    data object Unreachable : RequestsError

    /** The server answered 401, so the session has expired. */
    data object SessionExpired : RequestsError

    /**
     * Any other non-2xx answer.
     *
     * @property message The server's own text: the JSON `error`, `message` or `detail` string, else the trimmed
     * plain-text body, or null when it sent none.
     */
    data class Http(val code: Int, val message: String?) : RequestsError

    /** A 2xx body that does not decode, or any other unexpected failure. */
    data object UnexpectedResponse : RequestsError
}
