package com.omnibus.omnibus.data.audiobook.crossformat.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.omnibus.omnibus.data.libraries.local.BookEntity

/** A row of the `dismissed_resume_offers` table: the source clock of the resume offer the reader dismissed. */
@Entity(
    tableName = "dismissed_resume_offers",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class DismissedResumeOfferEntity(
    @PrimaryKey @ColumnInfo(name = "book_id") val bookId: Long,
    @ColumnInfo(name = "source_clock") val sourceClock: Long,
)
