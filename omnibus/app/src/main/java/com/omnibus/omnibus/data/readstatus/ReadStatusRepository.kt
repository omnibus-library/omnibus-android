package com.omnibus.omnibus.data.readstatus

import kotlinx.coroutines.flow.Flow

/** A book's read status for the signed-in reader. */
enum class ReadStatus { Unread, Reading, Finished }

/**
 * A status with its times, in epoch milliseconds.
 *
 * @property updatedAtMillis When the status was last set, or null when the server has no record of it.
 * @property finishedAtMillis When the book was first finished. Null unless [status] is [ReadStatus.Finished] and the
 *   server reported the time. Kept when Finished is set again, cleared for Unread and Reading.
 */
data class ReadStatusRecord(val status: ReadStatus, val updatedAtMillis: Long?, val finishedAtMillis: Long?)

/** What the app knows about one book's read status. */
sealed interface ReadStatusState {
    /** No answer from the server yet. Never shown as Unread. */
    data object Unknown : ReadStatusState

    /** The server's record, or an optimistic one while a write is in flight. */
    data class Known(val record: ReadStatusRecord) : ReadStatusState

    /** The last read failed and no status was ever known. */
    data object Failed : ReadStatusState
}

/**
 * Reads and sets a book's read status. Every failure is a result, so nothing throws except cancellation.
 *
 * Each book has one shared state. Collecting it fetches the status once, and a write shows at once for every
 * collector. A failed write rolls the state back to the last server-confirmed answer.
 */
interface ReadStatusRepository {

    /**
     * The shared state for [bookUuid], which replays its current value. Collecting it when nothing is known starts a
     * fetch, so the first emission is [ReadStatusState.Unknown] and a blank uuid fails without a request.
     */
    fun getReadStatusFlow(bookUuid: String): Flow<ReadStatusState>

    /** Fetches the status again, unless a fetch for [bookUuid] is already in flight, which it joins. */
    suspend fun refresh(bookUuid: String)

    /**
     * Sets the status. The shared state moves at once, then the server's record replaces it on success, or the state
     * rolls back to the last confirmed answer on failure. Choosing the status that is already known makes no request.
     */
    suspend fun setStatus(bookUuid: String, status: ReadStatus): ReadStatusWriteResult

    /** One read of the status from the server, which also updates the shared state. */
    suspend fun fetchStatus(bookUuid: String): ReadStatusState
}

/** The outcome of [ReadStatusRepository.setStatus]. */
sealed interface ReadStatusWriteResult {
    /** The server accepted the write. [record] is the server's, and it replaces the optimistic value. */
    data class Saved(val record: ReadStatusRecord) : ReadStatusWriteResult

    /** The status was already current, so no request was made. */
    data object Unchanged : ReadStatusWriteResult

    /** The write failed and the shared state was rolled back. [error] says why. */
    data class Failed(val error: ReadStatusError) : ReadStatusWriteResult
}

/** Why a status write failed. */
sealed interface ReadStatusError {
    /** No server is configured, so no request was made. */
    data object NoServer : ReadStatusError

    /** The server could not be reached (an IOException). */
    data object Unreachable : ReadStatusError

    /** The server answered 401, so the session has expired. */
    data object SessionExpired : ReadStatusError

    /** The server answered 404, or the book uuid was blank and no request was made. */
    data object BookNotFound : ReadStatusError

    /** The server answered another non-2xx code. */
    data class Http(val code: Int) : ReadStatusError

    /** A 2xx body that does not decode, or any other unexpected failure. */
    data object UnexpectedResponse : ReadStatusError
}
