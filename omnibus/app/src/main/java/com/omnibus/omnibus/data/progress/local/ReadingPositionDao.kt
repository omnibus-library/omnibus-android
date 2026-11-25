package com.omnibus.omnibus.data.progress.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.omnibus.omnibus.data.libraries.local.BookRows
import kotlinx.coroutines.flow.Flow

/** The `reading_positions` table, read and written per library and book. */
@Dao
abstract class ReadingPositionDao : BookRows() {

    @Query(
        """
        SELECT r.* FROM reading_positions r
        JOIN books b ON b.id = r.book_id
        WHERE b.library_id = :libraryId AND b.book_uuid = :bookUuid
        """,
    )
    abstract fun positionFlow(libraryId: String, bookUuid: String): Flow<ReadingPositionEntity?>

    /** Stores the position of the book, creating the book's row when needed. */
    @Transaction
    open suspend fun save(libraryId: String, bookUuid: String, cfi: String?, percent: Int?, clientUpdatedAt: Long) {
        val bookId = findOrCreate(libraryId, bookUuid)
        upsert(ReadingPositionEntity(bookId = bookId, cfi = cfi, percent = percent, clientUpdatedAt = clientUpdatedAt))
    }

    @Upsert
    abstract suspend fun upsert(position: ReadingPositionEntity)
}
