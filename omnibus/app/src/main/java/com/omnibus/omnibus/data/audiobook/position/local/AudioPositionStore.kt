package com.omnibus.omnibus.data.audiobook.position.local

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.omnibus.omnibus.data.audiobook.di.ListeningPositionPreferences
import com.omnibus.omnibus.data.audiobook.position.AudioPosition
import com.omnibus.omnibus.data.audiobook.position.ListeningSessionReport
import com.omnibus.omnibus.data.audiobook.position.remote.AudioProgressJson
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

/**
 * This device's audio positions and queued listening sessions. Every write is one `updateData`, so a
 * failure leaves the file as it was. Reads and writes never throw IO errors: a failed read is absent,
 * a failed write is logged.
 */
interface AudioPositionStore {

    /** The cached position of [bookUuid] (I-RES-1): the last one this device saved. Null when absent. */
    suspend fun cached(bookUuid: String): AudioPosition?

    /** The pending write of [bookUuid] (I-SAVE-4): at most one, cleared once the server accepts it. */
    suspend fun pending(bookUuid: String): AudioPosition?

    /** Every pending write, one per book. Undecodable entries are skipped. */
    suspend fun allPending(): List<AudioPosition>

    /** Stores [position] as both the cached and the pending write of its book (R2). */
    suspend fun save(position: AudioPosition)

    /** Clears the pending write of [position]'s book only when it still equals [position] (I-SAVE-4). */
    suspend fun clearPendingIf(position: AudioPosition)

    /** Appends [reports] to the queued sessions, in order (I-SESS-3). */
    suspend fun queueSessions(reports: List<ListeningSessionReport>)

    /** The queued sessions, in queue order. Empty when none or unreadable. */
    suspend fun sessions(): List<ListeningSessionReport>

    /** Removes the queued sessions whose client id is in [clientIds]. */
    suspend fun removeSessions(clientIds: Set<String>)
}

/**
 * [AudioPositionStore] backed by its own DataStore file. Each book has a cached and a pending key holding
 * a small JSON object, so a corrupt value affects only its own key.
 */
class DefaultAudioPositionStore @Inject constructor(
    @param:ListeningPositionPreferences private val dataStore: DataStore<Preferences>,
) : AudioPositionStore {

    override suspend fun cached(bookUuid: String): AudioPosition? =
        readPosition(bookUuid, cachedKey(bookUuid))

    override suspend fun pending(bookUuid: String): AudioPosition? =
        readPosition(bookUuid, pendingKey(bookUuid))

    override suspend fun allPending(): List<AudioPosition> =
        currentPrefs().asMap().mapNotNull { (key, value) ->
            val name = key.name
            if (!name.startsWith(PENDING_PREFIX) || value !is String) {
                null
            } else {
                decodePosition(name.removePrefix(PENDING_PREFIX), value)
            }
        }

    override suspend fun save(position: AudioPosition) {
        val stored = AudioProgressJson.encodeToString(position.toStored())
        write { prefs ->
            prefs[cachedKey(position.bookUuid)] = stored
            prefs[pendingKey(position.bookUuid)] = stored
        }
    }

    override suspend fun clearPendingIf(position: AudioPosition) {
        write { prefs ->
            val key = pendingKey(position.bookUuid)
            val current = prefs[key]?.let { decodePosition(position.bookUuid, it) }
            if (current == position) prefs.remove(key)
        }
    }

    override suspend fun queueSessions(reports: List<ListeningSessionReport>) {
        write { prefs ->
            val current = decodeSessions(prefs[sessionsKey])
            prefs[sessionsKey] = AudioProgressJson.encodeToString(
                current + reports.map { it.toStored() },
            )
        }
    }

    override suspend fun sessions(): List<ListeningSessionReport> =
        decodeSessions(currentPrefs()[sessionsKey]).map { it.toDomain() }

    override suspend fun removeSessions(clientIds: Set<String>) {
        write { prefs ->
            val kept = decodeSessions(prefs[sessionsKey]).filterNot { it.clientId in clientIds }
            prefs[sessionsKey] = AudioProgressJson.encodeToString(kept)
        }
    }

    private suspend fun readPosition(bookUuid: String, key: Preferences.Key<String>): AudioPosition? =
        currentPrefs()[key]?.let { decodePosition(bookUuid, it) }

    /** The stored preferences. A read failure is logged and reads as empty. */
    private suspend fun currentPrefs(): Preferences = dataStore.data
        .catch { error ->
            if (error is IOException) {
                Log.w(TAG, "Unable to read the audio positions", error)
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .first()

    /** Applies [transform] in one `updateData`. A write failure is logged and not thrown. */
    private suspend fun write(transform: (MutablePreferences) -> Unit) {
        try {
            dataStore.updateData { prefs -> prefs.toMutablePreferences().also(transform) }
        } catch (error: IOException) {
            Log.e(TAG, "Unable to write the audio positions", error)
        }
    }

    private fun decodePosition(bookUuid: String, raw: String): AudioPosition? =
        try {
            AudioProgressJson.decodeFromString<StoredAudioPosition>(raw).toDomain(bookUuid)
        } catch (error: SerializationException) {
            Log.w(TAG, "Dropping an unreadable audio position for $bookUuid", error)
            null
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "Dropping an unreadable audio position for $bookUuid", error)
            null
        }

    /** The session list. A corrupt list reads as empty, so the next write starts a fresh one. */
    private fun decodeSessions(raw: String?): List<StoredSessionReport> {
        if (raw == null) return emptyList()
        return try {
            AudioProgressJson.decodeFromString<List<StoredSessionReport>>(raw)
        } catch (error: SerializationException) {
            Log.w(TAG, "Dropping an unreadable session queue", error)
            emptyList()
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "Dropping an unreadable session queue", error)
            emptyList()
        }
    }

    /** The stored JSON shape of a position. The field names are part of the stored format. */
    @Serializable
    private data class StoredAudioPosition(
        val positionSeconds: Double,
        val bookFileId: Long,
        val clientUpdatedAt: Long,
    )

    /** The stored JSON shape of a queued session. The field names are part of the stored format. */
    @Serializable
    private data class StoredSessionReport(
        val bookUuid: String,
        val startedAt: Long,
        val endedAt: Long,
        val progressUnits: Long,
        val clientId: String,
        val utcOffsetMinutes: Int,
        val timeZone: String,
    )

    private fun AudioPosition.toStored() = StoredAudioPosition(positionSeconds, bookFileId, clientUpdatedAt)

    private fun StoredAudioPosition.toDomain(bookUuid: String) =
        AudioPosition(bookUuid, positionSeconds, bookFileId, clientUpdatedAt)

    private fun ListeningSessionReport.toStored() = StoredSessionReport(
        bookUuid = bookUuid,
        startedAt = startedAt,
        endedAt = endedAt,
        progressUnits = progressUnits,
        clientId = clientId,
        utcOffsetMinutes = utcOffsetMinutes,
        timeZone = timeZone,
    )

    private fun StoredSessionReport.toDomain() = ListeningSessionReport(
        bookUuid = bookUuid,
        startedAt = startedAt,
        endedAt = endedAt,
        progressUnits = progressUnits,
        clientId = clientId,
        utcOffsetMinutes = utcOffsetMinutes,
        timeZone = timeZone,
    )

    companion object {
        /*
         * The keys and the JSON field names are a stored format. Keep them stable: changing one strands
         * the positions and sessions already on the device.
         */
        const val CACHED_PREFIX = "audio.cached."
        const val PENDING_PREFIX = "audio.pending."
        const val SESSIONS_KEY = "audio.sessions"

        private val sessionsKey = stringPreferencesKey(SESSIONS_KEY)

        private fun cachedKey(bookUuid: String) = stringPreferencesKey("$CACHED_PREFIX$bookUuid")

        private fun pendingKey(bookUuid: String) = stringPreferencesKey("$PENDING_PREFIX$bookUuid")

        private const val TAG = "AudioPositionStore"
    }
}
