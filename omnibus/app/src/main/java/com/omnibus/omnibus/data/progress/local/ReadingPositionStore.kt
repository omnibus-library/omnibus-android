package com.omnibus.omnibus.data.progress.local

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.omnibus.omnibus.data.progress.ReadingPosition
import com.omnibus.omnibus.data.progress.di.ReadingPositionPreferences
import com.omnibus.omnibus.data.progress.remote.ProgressJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.io.IOException
import javax.inject.Inject

/** Per-device storage of the last reading position of each book. */
interface ReadingPositionStore {

    /** The stored position of [bookUuid]. Emits null when none is stored or the read fails. */
    fun getPositionFlow(bookUuid: String): Flow<ReadingPosition?>

    /** Stores [position] for its book, replacing the previous one. Failures are logged and not thrown. */
    suspend fun save(position: ReadingPosition)
}

/**
 * [ReadingPositionStore] backed by DataStore. Each book is one string key holding a small JSON
 * object, so a write is atomic and a corrupt value only affects its own book.
 */
class DefaultReadingPositionStore @Inject constructor(
    @param:ReadingPositionPreferences private val dataStore: DataStore<Preferences>,
) : ReadingPositionStore {

    override fun getPositionFlow(bookUuid: String): Flow<ReadingPosition?> = dataStore.data
        .map { prefs -> prefs[keyFor(bookUuid)]?.let { decode(bookUuid, it) } }
        .catch { error ->
            if (error is IOException) {
                Log.w(TAG, "Unable to read the reading position", error)
                emit(null)
            } else {
                throw error
            }
        }

    override suspend fun save(position: ReadingPosition) {
        val stored = ProgressJson.encodeToString(
            StoredReadingPosition(
                cfi = position.cfi,
                percent = position.percent,
                clientUpdatedAt = position.clientUpdatedAt,
            ),
        )
        try {
            dataStore.updateData { prefs ->
                prefs.toMutablePreferences().apply { this[keyFor(position.bookUuid)] = stored }
            }
        } catch (error: IOException) {
            Log.e(TAG, "Unable to save the reading position", error)
        }
    }

    private fun decode(bookUuid: String, raw: String): ReadingPosition? =
        try {
            val stored = ProgressJson.decodeFromString<StoredReadingPosition>(raw)
            ReadingPosition(
                bookUuid = bookUuid,
                cfi = stored.cfi,
                percent = stored.percent,
                clientUpdatedAt = stored.clientUpdatedAt,
            )
        } catch (error: SerializationException) {
            Log.w(TAG, "Dropping an unreadable reading position for $bookUuid", error)
            null
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "Dropping an unreadable reading position for $bookUuid", error)
            null
        }

    /** The stored JSON shape. The field names are part of the stored format. */
    @Serializable
    private data class StoredReadingPosition(
        val cfi: String? = null,
        val percent: Int? = null,
        val clientUpdatedAt: Long,
    )

    companion object {
        /**
         * The key for [bookUuid]. The key prefix and the JSON field names are a stored format, so
         * keep them stable.
         */
        fun keyFor(bookUuid: String) = stringPreferencesKey("$KEY_PREFIX$bookUuid")

        /** The prefix of every book's key. Part of the stored format. */
        const val KEY_PREFIX = "position."

        private const val TAG = "ReadingPositionStore"
    }
}
