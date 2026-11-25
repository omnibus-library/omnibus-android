package com.omnibus.omnibus.data.audiobook.rate.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.omnibus.omnibus.data.libraries.local.BookRows

/** A pending playback rate, with the uuid of its book. */
data class PendingRateRow(
    @ColumnInfo(name = "book_uuid") val bookUuid: String,
    @ColumnInfo(name = "pending_rate") val pendingRate: Double,
)

/** The `playback_rates` table, read and written per library and book. */
@Dao
abstract class PlaybackRateDao : BookRows() {

    @Query(
        """
        SELECT r.rate FROM playback_rates r JOIN books b ON b.id = r.book_id
        WHERE b.library_id = :libraryId AND b.book_uuid = :bookUuid
        """,
    )
    abstract suspend fun cached(libraryId: String, bookUuid: String): Double?

    @Query(
        """
        SELECT r.pending_rate FROM playback_rates r JOIN books b ON b.id = r.book_id
        WHERE b.library_id = :libraryId AND b.book_uuid = :bookUuid
        """,
    )
    abstract suspend fun pending(libraryId: String, bookUuid: String): Double?

    /** Every pending rate of [libraryId], one per book. */
    @Query(
        """
        SELECT b.book_uuid AS book_uuid, r.pending_rate AS pending_rate
        FROM playback_rates r JOIN books b ON b.id = r.book_id
        WHERE b.library_id = :libraryId AND r.pending_rate IS NOT NULL
        """,
    )
    abstract suspend fun pendingRates(libraryId: String): List<PendingRateRow>

    /** Stores [rate] as both the cached and the pending rate of the book. */
    @Transaction
    open suspend fun saveLocal(libraryId: String, bookUuid: String, rate: Double) {
        val bookId = findOrCreate(libraryId, bookUuid)
        upsert(PlaybackRateEntity(bookId = bookId, rate = rate, pendingRate = rate))
    }

    /**
     * Stores [rate] as the cached rate of the book, leaving its pending rate alone. A null rate removes
     * the cached rate and creates no book.
     */
    @Transaction
    open suspend fun storeServer(libraryId: String, bookUuid: String, rate: Double?) {
        if (rate == null) {
            clearCached(libraryId, bookUuid)
            return
        }
        val bookId = findOrCreate(libraryId, bookUuid)
        if (updateCached(bookId, rate) == 0) {
            insertRate(PlaybackRateEntity(bookId = bookId, rate = rate, pendingRate = null))
        }
    }

    /** Stores the cached and pending rates of one imported book. Either may be null. */
    @Transaction
    open suspend fun importRate(libraryId: String, bookUuid: String, cached: Double?, pending: Double?) {
        val bookId = findOrCreate(libraryId, bookUuid)
        upsert(PlaybackRateEntity(bookId = bookId, rate = cached, pendingRate = pending))
    }

    /** Clears the pending rate of the book only while it still equals [rate]. */
    @Query(
        """
        UPDATE playback_rates SET pending_rate = NULL
        WHERE book_id = (SELECT id FROM books WHERE library_id = :libraryId AND book_uuid = :bookUuid)
            AND pending_rate = :rate
        """,
    )
    abstract suspend fun clearPendingIf(libraryId: String, bookUuid: String, rate: Double): Int

    @Query(
        """
        UPDATE playback_rates SET rate = NULL
        WHERE book_id = (SELECT id FROM books WHERE library_id = :libraryId AND book_uuid = :bookUuid)
        """,
    )
    abstract suspend fun clearCached(libraryId: String, bookUuid: String): Int

    @Query("UPDATE playback_rates SET rate = :rate WHERE book_id = :bookId")
    abstract suspend fun updateCached(bookId: Long, rate: Double): Int

    @Insert
    abstract suspend fun insertRate(rate: PlaybackRateEntity): Long

    @Upsert
    abstract suspend fun upsert(rate: PlaybackRateEntity)
}
