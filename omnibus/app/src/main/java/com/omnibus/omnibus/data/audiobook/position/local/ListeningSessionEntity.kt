package com.omnibus.omnibus.data.audiobook.position.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.omnibus.omnibus.data.libraries.local.BookEntity

/** A row of the `listening_sessions` table: one queued session. [queueOrder] keeps the queue order. */
@Entity(
    tableName = "listening_sessions",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["book_id"])],
)
data class ListeningSessionEntity(
    @PrimaryKey @ColumnInfo(name = "client_id") val clientId: String,
    @ColumnInfo(name = "book_id") val bookId: Long,
    @ColumnInfo(name = "queue_order") val queueOrder: Long,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long,
    @ColumnInfo(name = "progress_units") val progressUnits: Long,
    @ColumnInfo(name = "utc_offset_minutes") val utcOffsetMinutes: Int,
    @ColumnInfo(name = "time_zone") val timeZone: String,
)
