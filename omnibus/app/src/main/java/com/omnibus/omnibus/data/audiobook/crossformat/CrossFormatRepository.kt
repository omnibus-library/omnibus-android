package com.omnibus.omnibus.data.audiobook.crossformat

/**
 * The ebook-to-audio resume read and the per-book memory of dismissed ebook offers (I-RES-7, P-SYNC-7, P-SYNC-AC6).
 *
 * [audioResume] never throws except for cancellation: every failure is [CrossFormatAudioResult.Failed].
 * [dismissedClock] returns null when the book was never dismissed, or when the memory can't be read (the offer is then
 * shown again). [storeDismissedClock] overwrites the earlier clock for the book.
 */
interface CrossFormatRepository {

    /** The server's audio position mapped from the ebook, for [bookUuid]. */
    suspend fun audioResume(bookUuid: String): CrossFormatAudioResult

    /** The source clock of the ebook position the listener last dismissed for [bookUuid], or null. */
    suspend fun dismissedClock(bookUuid: String): Long?

    /** Remembers [sourceClock] as dismissed for [bookUuid]. */
    suspend fun storeDismissedClock(bookUuid: String, sourceClock: Long)
}

/** The answer of [CrossFormatRepository.audioResume]. */
sealed interface CrossFormatAudioResult {

    /**
     * A mapped audio position. [bookFileId] null means the server didn't say which file; the holder decides the match.
     * [sourceAhead] is false when the ebook position is behind the audio one (a re-read). [follow] is the server's
     * follow mode, under which the position is taken without asking.
     */
    data class Candidate(
        val positionSeconds: Double,
        val bookFileId: Long?,
        val sourceAhead: Boolean,
        val sourceClock: Long,
        val follow: Boolean,
    ) : CrossFormatAudioResult

    /** No position to offer: any state other than `candidate`, or a candidate without a position. */
    data object NoCandidate : CrossFormatAudioResult

    /** The read failed: no server, a non-2xx answer, an IO or decode failure. */
    data object Failed : CrossFormatAudioResult
}
