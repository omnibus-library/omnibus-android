package com.omnibus.omnibus.data.libraries.upgrade

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.omnibus.omnibus.data.audiobook.position.AudioPosition
import com.omnibus.omnibus.data.audiobook.position.ListeningSessionReport
import com.omnibus.omnibus.data.audiobook.position.remote.AudioProgressJson
import com.omnibus.omnibus.data.progress.ReadingPosition
import com.omnibus.omnibus.data.progress.remote.ProgressJson
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

/** The listening entries of the legacy audio position file. */
internal data class LegacyListening(
    val cached: Map<String, AudioPosition>,
    val pending: Map<String, AudioPosition>,
    val sessions: List<ListeningSessionReport>,
)

/** The playback rates of the legacy rate file, by book uuid. */
internal data class LegacyRates(
    val cached: Map<String, Double>,
    val pending: Map<String, Double>,
)

/**
 * Decodes the DataStore files of the builds before libraries, with the formats those builds wrote. The formats are
 * a stored format, so the keys and JSON field names stay as they were. A file that can't be read reads as empty,
 * and an entry that can't be decoded is dropped.
 */
internal object LegacyStorageReader {

    suspend fun readAddress(store: DataStore<Preferences>): String? = read(store)[SERVER_ADDRESS_KEY]

    suspend fun readReadingPositions(store: DataStore<Preferences>): List<ReadingPosition> =
        read(store).asMap().mapNotNull { (key, value) ->
            val name = key.name
            if (!name.startsWith(READING_PREFIX) || value !is String) {
                null
            } else {
                decodeReadingPosition(name.removePrefix(READING_PREFIX), value)
            }
        }

    suspend fun readListening(store: DataStore<Preferences>): LegacyListening {
        val prefs = read(store)
        val cached = mutableMapOf<String, AudioPosition>()
        val pending = mutableMapOf<String, AudioPosition>()
        prefs.asMap().forEach { (key, value) ->
            if (value !is String) return@forEach
            val name = key.name
            when {
                name.startsWith(CACHED_PREFIX) -> decodeAudioPosition(name.removePrefix(CACHED_PREFIX), value)
                    ?.let { cached[it.bookUuid] = it }
                name.startsWith(PENDING_PREFIX) -> decodeAudioPosition(name.removePrefix(PENDING_PREFIX), value)
                    ?.let { pending[it.bookUuid] = it }
            }
        }
        return LegacyListening(cached, pending, decodeSessions(prefs[SESSIONS_KEY]))
    }

    suspend fun readRates(store: DataStore<Preferences>): LegacyRates {
        val cached = mutableMapOf<String, Double>()
        val pending = mutableMapOf<String, Double>()
        read(store).asMap().forEach { (key, value) ->
            if (value !is Double) return@forEach
            val name = key.name
            when {
                name.startsWith(RATE_CACHED_PREFIX) -> cached[name.removePrefix(RATE_CACHED_PREFIX)] = value
                name.startsWith(RATE_PENDING_PREFIX) -> pending[name.removePrefix(RATE_PENDING_PREFIX)] = value
            }
        }
        return LegacyRates(cached, pending)
    }

    /** The dismissed source clock of each book uuid. */
    suspend fun readDismissed(store: DataStore<Preferences>): Map<String, Long> =
        read(store).asMap().mapNotNull { (key, value) ->
            val name = key.name
            if (!name.startsWith(DISMISSED_PREFIX) || !name.endsWith(DISMISSED_SUFFIX) || value !is Long) {
                null
            } else {
                name.removePrefix(DISMISSED_PREFIX).removeSuffix(DISMISSED_SUFFIX) to value
            }
        }.toMap()

    /** The stored preferences. A file that can't be read is logged and reads as empty. */
    private suspend fun read(store: DataStore<Preferences>): Preferences =
        try {
            store.data.first()
        } catch (error: IOException) {
            Log.w(TAG, "Unable to read a legacy storage file", error)
            emptyPreferences()
        }

    private fun decodeReadingPosition(bookUuid: String, raw: String): ReadingPosition? =
        try {
            val stored = ProgressJson.decodeFromString<StoredReadingPosition>(raw)
            ReadingPosition(
                bookUuid = bookUuid,
                cfi = stored.cfi,
                percent = stored.percent,
                clientUpdatedAt = stored.clientUpdatedAt,
            )
        } catch (error: SerializationException) {
            Log.w(TAG, "Dropping an unreadable legacy reading position for $bookUuid", error)
            null
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "Dropping an unreadable legacy reading position for $bookUuid", error)
            null
        }

    private fun decodeAudioPosition(bookUuid: String, raw: String): AudioPosition? =
        try {
            AudioProgressJson.decodeFromString<StoredAudioPosition>(raw).let {
                AudioPosition(bookUuid, it.positionSeconds, it.bookFileId, it.clientUpdatedAt)
            }
        } catch (error: SerializationException) {
            Log.w(TAG, "Dropping an unreadable legacy audio position for $bookUuid", error)
            null
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "Dropping an unreadable legacy audio position for $bookUuid", error)
            null
        }

    /** The session queue, in its stored order. A corrupt queue reads as empty. */
    private fun decodeSessions(raw: String?): List<ListeningSessionReport> {
        if (raw == null) return emptyList()
        return try {
            AudioProgressJson.decodeFromString<List<StoredSessionReport>>(raw).map { it.toDomain() }
        } catch (error: SerializationException) {
            Log.w(TAG, "Dropping an unreadable legacy session queue", error)
            emptyList()
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "Dropping an unreadable legacy session queue", error)
            emptyList()
        }
    }

    /** The old reading position shape. The field names are a stored format. */
    @Serializable
    private data class StoredReadingPosition(
        val cfi: String? = null,
        val percent: Int? = null,
        val clientUpdatedAt: Long,
    )

    /** The old audio position shape. The field names are a stored format. */
    @Serializable
    private data class StoredAudioPosition(
        val positionSeconds: Double,
        val bookFileId: Long,
        val clientUpdatedAt: Long,
    )

    /** The old queued session shape. The field names are a stored format. */
    @Serializable
    private data class StoredSessionReport(
        val bookUuid: String,
        val startedAt: Long,
        val endedAt: Long,
        val progressUnits: Long,
        val clientId: String,
        val utcOffsetMinutes: Int,
        val timeZone: String,
    ) {
        fun toDomain() = ListeningSessionReport(
            bookUuid = bookUuid,
            startedAt = startedAt,
            endedAt = endedAt,
            progressUnits = progressUnits,
            clientId = clientId,
            utcOffsetMinutes = utcOffsetMinutes,
            timeZone = timeZone,
        )
    }

    private val SERVER_ADDRESS_KEY = stringPreferencesKey("server_address")
    private const val READING_PREFIX = "position."
    private const val CACHED_PREFIX = "audio.cached."
    private const val PENDING_PREFIX = "audio.pending."
    private const val SESSIONS_KEY_NAME = "audio.sessions"
    private val SESSIONS_KEY = stringPreferencesKey(SESSIONS_KEY_NAME)
    private const val RATE_CACHED_PREFIX = "cached:"
    private const val RATE_PENDING_PREFIX = "pending:"
    private const val DISMISSED_PREFIX = "dismissed_clock:"
    private const val DISMISSED_SUFFIX = ":audio"
    private const val TAG = "LegacyStorageReader"
}
