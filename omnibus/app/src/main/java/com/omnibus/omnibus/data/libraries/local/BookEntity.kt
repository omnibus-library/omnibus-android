package com.omnibus.omnibus.data.libraries.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A row of the `books` table. A book is identified by its library and its uuid, so the same book
 * on two servers has two rows. Deleting the library deletes its books.
 */
@Entity(
    tableName = "books",
    foreignKeys = [
        ForeignKey(
            entity = LibraryEntity::class,
            parentColumns = ["id"],
            childColumns = ["library_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["library_id", "book_uuid"], unique = true)],
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "library_id") val libraryId: String,
    @ColumnInfo(name = "book_uuid") val bookUuid: String,
)
