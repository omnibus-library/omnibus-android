package com.omnibus.omnibus.data.audiobook.crossformat.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.omnibus.omnibus.data.libraries.local.BookRows

/** The `dismissed_resume_offers` table, read and written per library and book. */
@Dao
abstract class DismissedResumeOfferDao : BookRows() {

    @Query(
        """
        SELECT d.source_clock FROM dismissed_resume_offers d JOIN books b ON b.id = d.book_id
        WHERE b.library_id = :libraryId AND b.book_uuid = :bookUuid
        """,
    )
    abstract suspend fun dismissedClock(libraryId: String, bookUuid: String): Long?

    /** Stores the dismissed source clock of the book, creating the book's row when needed. */
    @Transaction
    open suspend fun store(libraryId: String, bookUuid: String, sourceClock: Long) {
        val bookId = findOrCreate(libraryId, bookUuid)
        upsert(DismissedResumeOfferEntity(bookId = bookId, sourceClock = sourceClock))
    }

    @Upsert
    abstract suspend fun upsert(offer: DismissedResumeOfferEntity)
}
