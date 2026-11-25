package com.omnibus.omnibus.data.audiobook.rate.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.omnibus.omnibus.data.libraries.local.BookEntity

/**
 * A row of the `playback_rates` table. [rate] is the cached rate and may be null while a [pendingRate]
 * remains; [pendingRate] is the rate still waiting for the server.
 */
@Entity(
    tableName = "playback_rates",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PlaybackRateEntity(
    @PrimaryKey @ColumnInfo(name = "book_id") val bookId: Long,
    val rate: Double?,
    @ColumnInfo(name = "pending_rate") val pendingRate: Double?,
)
