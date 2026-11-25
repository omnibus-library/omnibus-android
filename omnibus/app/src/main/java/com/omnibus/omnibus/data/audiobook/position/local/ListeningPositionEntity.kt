package com.omnibus.omnibus.data.audiobook.position.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.omnibus.omnibus.data.libraries.local.BookEntity

/**
 * A row of the `listening_positions` table. The cached group is the last position saved; the pending
 * group is the write still waiting for the server, and is null when nothing is pending.
 */
@Entity(
    tableName = "listening_positions",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ListeningPositionEntity(
    @PrimaryKey @ColumnInfo(name = "book_id") val bookId: Long,
    @ColumnInfo(name = "position_seconds") val positionSeconds: Double,
    @ColumnInfo(name = "book_file_id") val bookFileId: Long,
    @ColumnInfo(name = "client_updated_at") val clientUpdatedAt: Long,
    @ColumnInfo(name = "pending_position_seconds") val pendingPositionSeconds: Double?,
    @ColumnInfo(name = "pending_book_file_id") val pendingBookFileId: Long?,
    @ColumnInfo(name = "pending_client_updated_at") val pendingClientUpdatedAt: Long?,
)
