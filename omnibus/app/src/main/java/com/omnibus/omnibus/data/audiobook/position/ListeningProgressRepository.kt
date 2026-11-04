package com.omnibus.omnibus.data.audiobook.position

/** A position saved on this device. [clientUpdatedAt] is unix seconds (I-RES-1). */
data class AudioPosition(
    val bookUuid: String,
    val positionSeconds: Double,
    val bookFileId: Long,
    val clientUpdatedAt: Long,
)

/** The server's newest audio position for a book. [bookFileId] null matches any file. [orderingClock] is unix seconds (I-RES-1). */
data class ServerAudioPosition(
    val positionSeconds: Double,
    val bookFileId: Long?,
    val orderingClock: Long,
)

/** One listening session report (I-SESS-1). Times are unix seconds; [progressUnits] is whole seconds listened. */
data class ListeningSessionReport(
    val bookUuid: String,
    val startedAt: Long,
    val endedAt: Long,
    val progressUnits: Long,
    val clientId: String,
    val utcOffsetMinutes: Int,
    val timeZone: String,
)

/** Reads and writes this device's audio positions and listening sessions, and pushes them to the server. */
interface ListeningProgressRepository {

    /**
     * The newer of the cached and the pending position of [bookUuid], from local storage only. A tie keeps
     * the cached one. Never throws.
     */
    suspend fun localCandidate(bookUuid: String): AudioPosition?

    /**
     * The server's newest positive audio record for [bookUuid] (R5). Null is "no server answer": no server
     * saved, a failed or undecodable response, or no audio record. Never throws, except cancellation.
     */
    suspend fun serverPosition(bookUuid: String): ServerAudioPosition?

    /**
     * Stores [position] locally in call order (I-SAVE-4). When [push], it also requests a flush of the
     * pending writes. Sends nothing itself.
     */
    suspend fun save(position: AudioPosition, push: Boolean)

    /** Queues [report] in local storage, then requests a flush of the queued sessions (I-SESS-3). */
    suspend fun queueSession(report: ListeningSessionReport)

    /** Starts a flush of every pending write and queued session on the application scope, and returns at once. */
    fun requestFlush()
}
