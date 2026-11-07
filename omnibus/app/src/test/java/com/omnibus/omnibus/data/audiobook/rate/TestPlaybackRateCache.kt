package com.omnibus.omnibus.data.audiobook.rate

import com.omnibus.omnibus.data.audiobook.rate.local.PlaybackRateCache

/**
 * In-memory [PlaybackRateCache] with the same semantics as the DataStore one. [failWith] is thrown by every call when set.
 */
class TestPlaybackRateCache : PlaybackRateCache {

    /** The last known server rate per book. */
    val cachedRates = mutableMapOf<String, Double>()

    /** The unsent local rate per book. */
    val pendingRates = mutableMapOf<String, Double>()

    /** The `(bookUuid, rate)` of each [saveLocal], in call order. */
    val localWrites = mutableListOf<Pair<String, Double>>()

    /** Thrown by every call when set. */
    var failWith: Exception? = null

    override suspend fun cached(bookUuid: String): Double? {
        failWith?.let { throw it }
        return cachedRates[bookUuid]
    }

    override suspend fun pending(bookUuid: String): Double? {
        failWith?.let { throw it }
        return pendingRates[bookUuid]
    }

    override suspend fun allPending(): Map<String, Double> {
        failWith?.let { throw it }
        return pendingRates.toMap()
    }

    override suspend fun saveLocal(bookUuid: String, rate: Double) {
        failWith?.let { throw it }
        localWrites += bookUuid to rate
        cachedRates[bookUuid] = rate
        pendingRates[bookUuid] = rate
    }

    override suspend fun storeServer(bookUuid: String, rate: Double?) {
        failWith?.let { throw it }
        if (rate == null) cachedRates.remove(bookUuid) else cachedRates[bookUuid] = rate
    }

    override suspend fun clearPendingIf(bookUuid: String, rate: Double) {
        failWith?.let { throw it }
        if (pendingRates[bookUuid] == rate) pendingRates.remove(bookUuid)
    }
}
