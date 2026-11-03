package com.omnibus.omnibus.data.deletion

/**
 * A file of a book that can be deleted from disk.
 *
 * @property label The reader's label for the file, or null when it has none.
 * @property sizeBytes The file's size on disk, or 0 when the server did not report it.
 */
data class DeletableFile(
    val id: Long,
    val format: String,
    val filename: String,
    val label: String?,
    val sizeBytes: Long,
)

/**
 * A physical copy of a book. Deleting it only un-records the copy, so nothing is removed from disk.
 *
 * @property isbn The copy's ISBN, or null when it has none.
 * @property note The note the reader wrote on the copy, or null when there is none.
 */
data class DeletableCopy(val id: Long, val isbn: String?, val note: String?)

/** What deleting the whole book would also remove from the library. Each count is zero when nothing of that kind exists. */
data class DeletionImpact(
    val highlights: Long = 0,
    val journalEntries: Long = 0,
    val bookmarks: Long = 0,
    val readingSessions: Long = 0,
    val listeningSessions: Long = 0,
    val reviews: Long = 0,
    val shelves: Long = 0,
)

/**
 * What a book has that can be deleted: its files, its physical copies, and what would go with the whole book.
 *
 * @property itemCount The number of files and copies together.
 */
data class DeletionManifest(
    val files: List<DeletableFile>,
    val copies: List<DeletableCopy>,
    val impact: DeletionImpact,
) {
    val itemCount: Int get() = files.size + copies.size
}

/** The outcome of [DeletionRepository.fetchManifest]. */
sealed interface DeletionManifestResult {
    data class Success(val manifest: DeletionManifest) : DeletionManifestResult

    /** The read failed. [error] says why. */
    data class Failed(val error: DeletionError) : DeletionManifestResult
}

/** The outcome of [DeletionRepository.deleteItems]. */
sealed interface DeleteItemsResult {

    /**
     * The server deleted the picked items.
     *
     * @property bookDeleted True when the server also deleted the book, because nothing of it is left.
     */
    data class Deleted(
        val deletedFileIds: List<Long>,
        val deletedCopyIds: List<Long>,
        val bookDeleted: Boolean,
    ) : DeleteItemsResult

    /** The delete failed and nothing was reported as deleted. [error] says why. */
    data class Failed(val error: DeletionError) : DeleteItemsResult
}

/** Why a deletion request failed. */
sealed interface DeletionError {
    /** No server is configured, so no request was made. */
    data object NoServer : DeletionError

    /** The server could not be reached (an IOException). */
    data object Unreachable : DeletionError

    /** The server answered 401, so the session has expired. */
    data object SessionExpired : DeletionError

    /** The server answered 403, so the user is not an admin. */
    data object Forbidden : DeletionError

    /** The server answered 404, or the book uuid was blank and no request was made. */
    data object BookNotFound : DeletionError

    /** The server answered 422, so an id is not one of this book's. */
    data object ItemNotFound : DeletionError

    /** The server answered another non-2xx code. */
    data class Http(val code: Int) : DeletionError

    /** A 2xx body that does not decode, or any other unexpected failure. */
    data object UnexpectedResponse : DeletionError
}

/**
 * Reads what a book can delete and deletes picked files and physical copies. Every failure is a result, so nothing
 * throws except cancellation.
 */
interface DeletionRepository {

    /** The book's files, physical copies and impact. Nothing is cached, so each call is a fresh read. */
    suspend fun fetchManifest(bookUuid: String): DeletionManifestResult

    /**
     * Deletes the picked files and copies. Both sets may be empty, which deletes a book that has neither. The request
     * is not cancelled when the caller is.
     */
    suspend fun deleteItems(bookUuid: String, fileIds: Set<Long>, copyIds: Set<Long>): DeleteItemsResult
}
