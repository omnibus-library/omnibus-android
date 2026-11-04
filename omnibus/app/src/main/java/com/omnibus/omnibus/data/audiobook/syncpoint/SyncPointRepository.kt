package com.omnibus.omnibus.data.audiobook.syncpoint

/** Declares sync points between a book's audio and its ebook (I-BMK-3). Never throws except cancellation. */
interface SyncPointRepository {

    /**
     * Tells the server that [audioSeconds] (book time) of [bookFileId] matches the reader's place. Sent once, never queued.
     */
    suspend fun declareAudioSyncPoint(bookUuid: String, bookFileId: Long, audioSeconds: Double): SyncPointResult
}

/** The outcome of declaring a sync point. */
sealed interface SyncPointResult {
    /** The server accepted the sync point (2xx). */
    data object Declared : SyncPointResult

    /** The server did not accept it. [error] says why. */
    data class Failed(val error: SyncPointError) : SyncPointResult
}

/** Why a sync point was not declared. The UI only tells [SyncPointResult.Declared] from a failure. */
sealed interface SyncPointError {
    /** No server is configured, so no request was made. */
    data object NoServer : SyncPointError

    /** The server could not be reached (an IOException). */
    data object Unreachable : SyncPointError

    /** The server answered 401, so the session has expired. */
    data object SessionExpired : SyncPointError

    /** The server answered 404, or the book uuid was blank and no request was made. */
    data object BookNotFound : SyncPointError

    /** The server answered 409: the alignment needs confirming, the audio set is stale, or there is nothing to pair with. */
    data object Conflict : SyncPointError

    /** The server answered 400 or 422, so it rejected the body. */
    data object Rejected : SyncPointError

    /** The server answered another non-2xx code. */
    data class Http(val code: Int) : SyncPointError

    /** Any other failure, such as a body the app did not expect. */
    data object UnexpectedResponse : SyncPointError
}
