package com.omnibus.omnibus.data.audiobook.rate.local

import com.omnibus.omnibus.data.libraries.ActiveLibrary
import com.omnibus.omnibus.data.libraries.readFromActiveLibrary
import com.omnibus.omnibus.data.libraries.writeToActiveLibrary
import javax.inject.Inject

/**
 * This device's playback rates per book: the last rate the server is known to hold (`cached`) and a local change the
 * server hasn't accepted yet (`pending`, at most one per book, the newer one wins) (I-SPD-1, I-SPD-3). Reads never
 * throw: a failed read is absent. A failed write is logged and dropped.
 */
interface PlaybackRateCache {

    /** The last known server rate of [bookUuid], or null when none is stored. */
    suspend fun cached(bookUuid: String): Double?

    /** The local change of [bookUuid] the server hasn't accepted, or null when none. */
    suspend fun pending(bookUuid: String): Double?

    /** Every pending change, keyed by book uuid. */
    suspend fun allPending(): Map<String, Double>

    /** Stores [rate] as both the cached and the pending rate of [bookUuid] (I-SPD-1). */
    suspend fun saveLocal(bookUuid: String, rate: Double)

    /** Stores the server's [rate] as the cached rate of [bookUuid], or removes it for null. Never touches pending. */
    suspend fun storeServer(bookUuid: String, rate: Double?)

    /** Clears the pending change of [bookUuid] only while it still equals [rate] (I-SPD-3). */
    suspend fun clearPendingIf(bookUuid: String, rate: Double)
}

/** [PlaybackRateCache] backed by the `playback_rates` table, scoped to the active library. */
class DefaultPlaybackRateCache @Inject constructor(
    private val dao: PlaybackRateDao,
    private val activeLibrary: ActiveLibrary,
) : PlaybackRateCache {

    override suspend fun cached(bookUuid: String): Double? =
        activeLibrary.readFromActiveLibrary(TAG) { dao.cached(it, bookUuid) }

    override suspend fun pending(bookUuid: String): Double? =
        activeLibrary.readFromActiveLibrary(TAG) { dao.pending(it, bookUuid) }

    override suspend fun allPending(): Map<String, Double> =
        activeLibrary.readFromActiveLibrary(TAG) { libraryId ->
            dao.pendingRates(libraryId).associate { it.bookUuid to it.pendingRate }
        }.orEmpty()

    override suspend fun saveLocal(bookUuid: String, rate: Double) {
        activeLibrary.writeToActiveLibrary(TAG) { libraryId -> dao.saveLocal(libraryId, bookUuid, rate) }
    }

    override suspend fun storeServer(bookUuid: String, rate: Double?) {
        activeLibrary.writeToActiveLibrary(TAG) { libraryId -> dao.storeServer(libraryId, bookUuid, rate) }
    }

    override suspend fun clearPendingIf(bookUuid: String, rate: Double) {
        activeLibrary.writeToActiveLibrary(TAG) { libraryId -> dao.clearPendingIf(libraryId, bookUuid, rate) }
    }

    private companion object {
        const val TAG = "PlaybackRateCache"
    }
}
