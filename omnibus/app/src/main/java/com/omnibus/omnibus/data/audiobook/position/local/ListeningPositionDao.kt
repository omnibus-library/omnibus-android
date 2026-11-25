package com.omnibus.omnibus.data.audiobook.position.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.omnibus.omnibus.data.audiobook.position.ListeningSessionReport
import com.omnibus.omnibus.data.libraries.local.BookRows

/** A cached or pending listening position, with the uuid of its book. */
data class ListeningPositionRow(
    @ColumnInfo(name = "book_uuid") val bookUuid: String,
    @ColumnInfo(name = "position_seconds") val positionSeconds: Double,
    @ColumnInfo(name = "book_file_id") val bookFileId: Long,
    @ColumnInfo(name = "client_updated_at") val clientUpdatedAt: Long,
)

/** A queued session, with the uuid of its book. Rows come back in queue order. */
data class ListeningSessionRow(
    @ColumnInfo(name = "book_uuid") val bookUuid: String,
    @ColumnInfo(name = "client_id") val clientId: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long,
    @ColumnInfo(name = "progress_units") val progressUnits: Long,
    @ColumnInfo(name = "utc_offset_minutes") val utcOffsetMinutes: Int,
    @ColumnInfo(name = "time_zone") val timeZone: String,
)

/** The `listening_positions` and `listening_sessions` tables, read and written per library. */
@Dao
abstract class ListeningPositionDao : BookRows() {

    @Query(
        """
        SELECT b.book_uuid AS book_uuid, p.position_seconds AS position_seconds,
            p.book_file_id AS book_file_id, p.client_updated_at AS client_updated_at
        FROM listening_positions p JOIN books b ON b.id = p.book_id
        WHERE b.library_id = :libraryId AND b.book_uuid = :bookUuid
        """,
    )
    abstract suspend fun cached(libraryId: String, bookUuid: String): ListeningPositionRow?

    @Query(
        """
        SELECT b.book_uuid AS book_uuid, p.pending_position_seconds AS position_seconds,
            p.pending_book_file_id AS book_file_id, p.pending_client_updated_at AS client_updated_at
        FROM listening_positions p JOIN books b ON b.id = p.book_id
        WHERE b.library_id = :libraryId AND b.book_uuid = :bookUuid
            AND p.pending_position_seconds IS NOT NULL
        """,
    )
    abstract suspend fun pending(libraryId: String, bookUuid: String): ListeningPositionRow?

    /** Every pending position of [libraryId], one per book. */
    @Query(
        """
        SELECT b.book_uuid AS book_uuid, p.pending_position_seconds AS position_seconds,
            p.pending_book_file_id AS book_file_id, p.pending_client_updated_at AS client_updated_at
        FROM listening_positions p JOIN books b ON b.id = p.book_id
        WHERE b.library_id = :libraryId AND p.pending_position_seconds IS NOT NULL
        """,
    )
    abstract suspend fun pendingPositions(libraryId: String): List<ListeningPositionRow>

    /** Stores the position as both the cached and the pending value of its book. */
    @Transaction
    open suspend fun save(libraryId: String, bookUuid: String, seconds: Double, fileId: Long, clientUpdatedAt: Long) {
        val bookId = findOrCreate(libraryId, bookUuid)
        upsert(
            ListeningPositionEntity(
                bookId = bookId,
                positionSeconds = seconds,
                bookFileId = fileId,
                clientUpdatedAt = clientUpdatedAt,
                pendingPositionSeconds = seconds,
                pendingBookFileId = fileId,
                pendingClientUpdatedAt = clientUpdatedAt,
            ),
        )
    }

    /** Clears the pending value of the book only while it still equals the given one. */
    @Query(
        """
        UPDATE listening_positions SET pending_position_seconds = NULL, pending_book_file_id = NULL,
            pending_client_updated_at = NULL
        WHERE book_id = (SELECT id FROM books WHERE library_id = :libraryId AND book_uuid = :bookUuid)
            AND pending_position_seconds = :seconds AND pending_book_file_id = :fileId
            AND pending_client_updated_at = :clientUpdatedAt
        """,
    )
    abstract suspend fun clearPendingIf(
        libraryId: String,
        bookUuid: String,
        seconds: Double,
        fileId: Long,
        clientUpdatedAt: Long,
    ): Int

    /** Writes the cached and pending groups of one imported book. [pending] is null when nothing is pending. */
    @Transaction
    open suspend fun importPosition(
        libraryId: String,
        bookUuid: String,
        cached: ListeningPositionRow,
        pending: ListeningPositionRow?,
    ) {
        val bookId = findOrCreate(libraryId, bookUuid)
        upsert(
            ListeningPositionEntity(
                bookId = bookId,
                positionSeconds = cached.positionSeconds,
                bookFileId = cached.bookFileId,
                clientUpdatedAt = cached.clientUpdatedAt,
                pendingPositionSeconds = pending?.positionSeconds,
                pendingBookFileId = pending?.bookFileId,
                pendingClientUpdatedAt = pending?.clientUpdatedAt,
            ),
        )
    }

    @Upsert
    abstract suspend fun upsert(position: ListeningPositionEntity)

    /** Appends [reports] to the queue, in order. A client id that is already queued is ignored. */
    @Transaction
    open suspend fun queueSessions(libraryId: String, reports: List<ListeningSessionReport>) {
        val lastOrder = maxQueueOrder()
        reports.forEachIndexed { index, report ->
            val bookId = findOrCreate(libraryId, report.bookUuid)
            insertSession(
                ListeningSessionEntity(
                    clientId = report.clientId,
                    bookId = bookId,
                    queueOrder = lastOrder + index + 1,
                    startedAt = report.startedAt,
                    endedAt = report.endedAt,
                    progressUnits = report.progressUnits,
                    utcOffsetMinutes = report.utcOffsetMinutes,
                    timeZone = report.timeZone,
                ),
            )
        }
    }

    /** Every queued session of [libraryId], in queue order. */
    @Query(
        """
        SELECT b.book_uuid AS book_uuid, s.client_id AS client_id, s.started_at AS started_at,
            s.ended_at AS ended_at, s.progress_units AS progress_units,
            s.utc_offset_minutes AS utc_offset_minutes, s.time_zone AS time_zone
        FROM listening_sessions s JOIN books b ON b.id = s.book_id
        WHERE b.library_id = :libraryId
        ORDER BY s.queue_order
        """,
    )
    abstract suspend fun sessions(libraryId: String): List<ListeningSessionRow>

    /** Removes the queued sessions with the given client ids, in chunks so the SQL stays under SQLite's variable limit. */
    @Transaction
    open suspend fun removeSessions(libraryId: String, clientIds: Collection<String>) {
        clientIds.chunked(SESSION_CHUNK_SIZE).forEach { deleteSessions(libraryId, it) }
    }

    @Query(
        """
        DELETE FROM listening_sessions
        WHERE client_id IN (:clientIds)
            AND book_id IN (SELECT id FROM books WHERE library_id = :libraryId)
        """,
    )
    abstract suspend fun deleteSessions(libraryId: String, clientIds: List<String>)

    @Query("SELECT COALESCE(MAX(queue_order), 0) FROM listening_sessions")
    abstract suspend fun maxQueueOrder(): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertSession(session: ListeningSessionEntity): Long

    private companion object {
        const val SESSION_CHUNK_SIZE = 500
    }
}
