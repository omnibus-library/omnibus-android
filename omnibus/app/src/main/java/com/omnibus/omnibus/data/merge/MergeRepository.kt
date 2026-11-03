package com.omnibus.omnibus.data.merge

import com.omnibus.omnibus.data.library.Book

/**
 * Candidate search, merge and undo for the Merge with… sheet (#120). Each call is one request, answered with a result
 * rather than an exception.
 */
interface MergeRepository {

    /** Library books matching [query]. A blank query answers an empty list without a request. */
    suspend fun searchCandidates(query: String): MergeCandidatesResult

    /** Merges the book [sourceUuid] into [targetUuid], which keeps its metadata and loses nothing it had. */
    suspend fun mergeBooks(sourceUuid: String, targetUuid: String): MergeBooksResult

    /** Reverses the merge [mergeLogId], restoring its source book. */
    suspend fun undoMerge(mergeLogId: Long): UndoMergeResult
}

/**
 * A completed merge.
 *
 * @property mergeLogId The server's handle for undoing the merge.
 * @property targetUuid The book that kept the files, tags and progress.
 */
data class MergeReceipt(val mergeLogId: Long, val targetUuid: String)

/** The outcome of [MergeRepository.searchCandidates]. */
sealed interface MergeCandidatesResult {

    data class Success(val books: List<Book>) : MergeCandidatesResult

    /** No server is saved, so no request was made. */
    data object NoServer : MergeCandidatesResult

    /** [error] is why the search failed. */
    data class Failure(val error: MergeError) : MergeCandidatesResult
}

/** The outcome of [MergeRepository.mergeBooks]. */
sealed interface MergeBooksResult {

    data class Success(val receipt: MergeReceipt) : MergeBooksResult

    /** No server is saved, so no request was made. */
    data object NoServer : MergeBooksResult

    /** [error] is why the merge failed. */
    data class Failure(val error: MergeError) : MergeBooksResult
}

/** The outcome of [MergeRepository.undoMerge]. */
sealed interface UndoMergeResult {

    /** [restoredUuid] is the source book, which is back in the library. */
    data class Success(val restoredUuid: String) : UndoMergeResult

    /** No server is saved, so no request was made. */
    data object NoServer : UndoMergeResult

    /** [error] is why the undo failed. */
    data class Failure(val error: MergeError) : UndoMergeResult
}

/** Why a merge request failed. The UI layer decides what text to show for each. */
sealed interface MergeError {

    /** The server could not be reached. */
    data object Unreachable : MergeError

    /** The response could not be decoded, or the request failed for any other reason. */
    data object UnexpectedResponse : MergeError

    /** The server answered 401, so the user must sign in again. */
    data object SessionExpired : MergeError

    /** The server answered 403: the user may not merge books. */
    data object Forbidden : MergeError

    /** The server answered 404, or a uuid or id was blank, so nothing was sent. */
    data object NotFound : MergeError

    /**
     * The server answered 409: the merge is already undone, or a later change stops the undo.
     *
     * @property serverMessage The server's own text, as in [Http].
     */
    data class Conflict(val serverMessage: String?) : MergeError

    /**
     * The server answered with another non-2xx status.
     *
     * @property code The HTTP status code.
     * @property serverMessage The server's own text: the JSON `error`, `message` or `detail` string, else the trimmed
     * plain-text body capped at 300 characters, else null.
     */
    data class Http(val code: Int, val serverMessage: String?) : MergeError
}
