package com.omnibus.omnibus.data.audiobook.rate.local

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.omnibus.omnibus.data.audiobook.di.PlaybackRatePreferences
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

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

/** [PlaybackRateCache] backed by its own DataStore file, with two keys per book. */
class DefaultPlaybackRateCache @Inject constructor(
    @param:PlaybackRatePreferences private val dataStore: DataStore<Preferences>,
) : PlaybackRateCache {

    override suspend fun cached(bookUuid: String): Double? = read()[cachedKey(bookUuid)]

    override suspend fun pending(bookUuid: String): Double? = read()[pendingKey(bookUuid)]

    override suspend fun allPending(): Map<String, Double> =
        read().asMap().mapNotNull { (key, value) ->
            val name = key.name
            if (name.startsWith(PENDING_PREFIX) && value is Double) name.removePrefix(PENDING_PREFIX) to value else null
        }.toMap()

    override suspend fun saveLocal(bookUuid: String, rate: Double) = write { prefs ->
        prefs[cachedKey(bookUuid)] = rate
        prefs[pendingKey(bookUuid)] = rate
    }

    override suspend fun storeServer(bookUuid: String, rate: Double?) = write { prefs ->
        if (rate == null) {
            prefs.remove(cachedKey(bookUuid))
        } else {
            prefs[cachedKey(bookUuid)] = rate
        }
    }

    override suspend fun clearPendingIf(bookUuid: String, rate: Double) = write { prefs ->
        val key = pendingKey(bookUuid)
        if (prefs[key] == rate) prefs.remove(key)
    }

    /** The stored preferences. A read failure is logged and reads as empty. */
    private suspend fun read(): Preferences = dataStore.data
        .catch { error ->
            if (error is IOException) {
                Log.w(TAG, "Unable to read the playback rates", error)
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .first()

    /** One edit, so a failure leaves the file as it was. A failed write is logged, not thrown. */
    private suspend fun write(transform: (MutablePreferences) -> Unit) {
        try {
            dataStore.edit { transform(it) }
        } catch (error: IOException) {
            Log.e(TAG, "Unable to store a playback rate", error)
        }
    }

    private fun cachedKey(bookUuid: String) = doublePreferencesKey("$CACHED_PREFIX$bookUuid")

    private fun pendingKey(bookUuid: String) = doublePreferencesKey("$PENDING_PREFIX$bookUuid")

    private companion object {
        const val TAG = "PlaybackRateCache"
        const val CACHED_PREFIX = "cached:"
        const val PENDING_PREFIX = "pending:"
    }
}
