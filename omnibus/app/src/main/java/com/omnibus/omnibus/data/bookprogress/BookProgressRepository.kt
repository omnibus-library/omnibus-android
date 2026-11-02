package com.omnibus.omnibus.data.bookprogress

import com.omnibus.omnibus.data.library.ProgressRecord
import com.omnibus.omnibus.feature.bookdetail.progress.BookChapter
import com.omnibus.omnibus.feature.bookdetail.progress.ReadingSession
import kotlinx.coroutines.flow.Flow

/**
 * The reading progress of one book: where the reader is, the chapters that split the bar, and the reading sessions
 * that give a pace. Every failure is a result, so nothing throws except cancellation.
 */
interface BookProgressRepository {

    /**
     * The book's progress records. Emits the device's own position first, then the server's merged with it. Collecting
     * it again makes a new read. The flow completes after the server answers or fails.
     */
    fun getBookProgressFlow(bookUuid: String): Flow<BookProgressResult>

    /** The book's chapters for the bar, read in one request per source. */
    suspend fun getChapters(bookUuid: String): BookChaptersResult

    /** The book's reading sessions, newest first, across every page the server has up to the page limit. */
    suspend fun getSessions(bookUuid: String): ReadingSessionsResult
}

/** Why a progress, chapters or sessions read failed. */
sealed interface BookProgressError {
    /** No server is saved, so no request was made. */
    data object NoServer : BookProgressError

    /** The server could not be reached (an IOException). */
    data object Unreachable : BookProgressError

    /** The server answered 401, so the session has expired. */
    data object SessionExpired : BookProgressError

    /** The server answered 404, or the book uuid was blank and no request was made. */
    data object NotFound : BookProgressError

    /** The server answered another non-2xx code. */
    data class Http(val code: Int) : BookProgressError

    /** A 2xx body that does not decode, or any other unexpected failure. */
    data object UnexpectedResponse : BookProgressError
}

/** The outcome of one [BookProgressRepository.getBookProgressFlow] emission. */
sealed interface BookProgressResult {

    /**
     * One record per format, at most one Reading and one Listening, with the device position merged in.
     * [fromServer] is false for the first, device-only emission, and true once the server answered.
     */
    data class Success(val records: List<ProgressRecord>, val fromServer: Boolean) : BookProgressResult

    /**
     * The server read failed. [records] is still the best known: the device position merged with the last server answer
     * this process saw for the book, or only the device position when there was none.
     */
    data class Failed(val error: BookProgressError, val records: List<ProgressRecord>) : BookProgressResult
}

/** The outcome of [BookProgressRepository.getChapters]. */
sealed interface BookChaptersResult {

    /**
     * [ebookChapters] come from the book's alignment, empty when the book has no measured EPUB. [audioChapters] come
     * from the manifest of [audioFileId], empty when there is no audio or none is known. [audioTotalSeconds] is that
     * file's length, or null when unknown.
     */
    data class Success(
        val ebookChapters: List<BookChapter>,
        val audioChapters: List<BookChapter>,
        val audioFileId: Long?,
        val audioTotalSeconds: Long?,
    ) : BookChaptersResult

    /** The chapters could not be read. */
    data class Failed(val error: BookProgressError) : BookChaptersResult
}

/** The outcome of [BookProgressRepository.getSessions]. */
sealed interface ReadingSessionsResult {

    /** Every session the server returned, newest first. */
    data class Success(val sessions: List<ReadingSession>) : ReadingSessionsResult

    /** A page failed, so no sessions are returned at all. */
    data class Failed(val error: BookProgressError) : ReadingSessionsResult
}
