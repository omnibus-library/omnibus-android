package com.omnibus.omnibus.data.progress.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.omnibus.omnibus.data.libraries.local.BookEntity

/** A row of the `reading_positions` table: the last reading position of one book. */
@Entity(
    tableName = "reading_positions",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ReadingPositionEntity(
    @PrimaryKey @ColumnInfo(name = "book_id") val bookId: Long,
    val cfi: String?,
    val percent: Int?,
    @ColumnInfo(name = "client_updated_at") val clientUpdatedAt: Long,
)
