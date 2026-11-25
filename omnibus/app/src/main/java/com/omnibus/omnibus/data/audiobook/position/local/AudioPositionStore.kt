package com.omnibus.omnibus.data.audiobook.position.local

import com.omnibus.omnibus.data.audiobook.position.AudioPosition
import com.omnibus.omnibus.data.audiobook.position.ListeningSessionReport
import com.omnibus.omnibus.data.libraries.ActiveLibrary
import com.omnibus.omnibus.data.libraries.readFromActiveLibrary
import com.omnibus.omnibus.data.libraries.writeToActiveLibrary
import javax.inject.Inject

/**
 * This device's audio positions and queued listening sessions. Reads and writes never throw IO errors: a failed read
 * is absent, a failed write is logged. Both are scoped to the active library.
 */
interface AudioPositionStore {

    /** The cached position of [bookUuid] (I-RES-1): the last one this device saved. Null when absent. */
    suspend fun cached(bookUuid: String): AudioPosition?

    /** The pending write of [bookUuid] (I-SAVE-4): at most one, cleared once the server accepts it. */
    suspend fun pending(bookUuid: String): AudioPosition?

    /** Every pending write, one per book. */
    suspend fun allPending(): List<AudioPosition>

    /** Stores [position] as both the cached and the pending write of its book (R2). */
    suspend fun save(position: AudioPosition)

    /** Clears the pending write of [position]'s book only when it still equals [position] (I-SAVE-4). */
    suspend fun clearPendingIf(position: AudioPosition)

    /** Appends [reports] to the queued sessions, in order (I-SESS-3). */
    suspend fun queueSessions(reports: List<ListeningSessionReport>)

    /** The queued sessions, in queue order. Empty when none or unreadable. */
    suspend fun sessions(): List<ListeningSessionReport>

    /** Removes the queued sessions whose client id is in [clientIds]. */
    suspend fun removeSessions(clientIds: Set<String>)
}

/**
 * [AudioPositionStore] backed by the `listening_positions` and `listening_sessions` tables, scoped to the active library.
 * With no active library reads are absent and writes are dropped.
 */
class DefaultAudioPositionStore @Inject constructor(
    private val dao: ListeningPositionDao,
    private val activeLibrary: ActiveLibrary,
) : AudioPositionStore {

    override suspend fun cached(bookUuid: String): AudioPosition? =
        activeLibrary.readFromActiveLibrary(TAG) { dao.cached(it, bookUuid)?.toDomain() }

    override suspend fun pending(bookUuid: String): AudioPosition? =
        activeLibrary.readFromActiveLibrary(TAG) { dao.pending(it, bookUuid)?.toDomain() }

    override suspend fun allPending(): List<AudioPosition> =
        activeLibrary.readFromActiveLibrary(TAG) { libraryId ->
            dao.pendingPositions(libraryId).map { it.toDomain() }
        }.orEmpty()

    override suspend fun save(position: AudioPosition) {
        activeLibrary.writeToActiveLibrary(TAG) { libraryId ->
            dao.save(
                libraryId,
                position.bookUuid,
                position.positionSeconds,
                position.bookFileId,
                position.clientUpdatedAt,
            )
        }
    }

    override suspend fun clearPendingIf(position: AudioPosition) {
        activeLibrary.writeToActiveLibrary(TAG) { libraryId ->
            dao.clearPendingIf(
                libraryId,
                position.bookUuid,
                position.positionSeconds,
                position.bookFileId,
                position.clientUpdatedAt,
            )
        }
    }

    override suspend fun queueSessions(reports: List<ListeningSessionReport>) {
        activeLibrary.writeToActiveLibrary(TAG) { libraryId -> dao.queueSessions(libraryId, reports) }
    }

    override suspend fun sessions(): List<ListeningSessionReport> =
        activeLibrary.readFromActiveLibrary(TAG) { libraryId ->
            dao.sessions(libraryId).map { it.toDomain() }
        }.orEmpty()

    override suspend fun removeSessions(clientIds: Set<String>) {
        activeLibrary.writeToActiveLibrary(TAG) { libraryId -> dao.removeSessions(libraryId, clientIds) }
    }

    private fun ListeningPositionRow.toDomain() =
        AudioPosition(bookUuid, positionSeconds, bookFileId, clientUpdatedAt)

    private fun ListeningSessionRow.toDomain() = ListeningSessionReport(
        bookUuid = bookUuid,
        startedAt = startedAt,
        endedAt = endedAt,
        progressUnits = progressUnits,
        clientId = clientId,
        utcOffsetMinutes = utcOffsetMinutes,
        timeZone = timeZone,
    )

    private companion object {
        const val TAG = "AudioPositionStore"
    }
}
