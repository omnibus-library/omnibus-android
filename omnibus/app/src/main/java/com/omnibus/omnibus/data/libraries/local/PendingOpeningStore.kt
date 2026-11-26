package com.omnibus.omnibus.data.libraries.local

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.omnibus.omnibus.data.libraries.PendingOpening
import com.omnibus.omnibus.data.libraries.SwitchKind
import com.omnibus.omnibus.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Keeps the pending opening of a switch, the library a restarted app reopens on (local-storage.md 1.1.0). */
interface PendingOpeningStore {

    /** The pending opening, or null when none is saved, or the saved one can't be read. Emits again when it changes. */
    fun getFlow(): Flow<PendingOpening?>

    /** Replaces the pending opening with [opening]. */
    suspend fun save(opening: PendingOpening)

    /** Removes the pending opening. */
    suspend fun clear()
}

/**
 * The pending opening in the device-wide `pending_opening.preferences_pb`, unencrypted, since it names no secret. Its
 * own DataStore on its own file, so it is never read or written by the libraries' token store.
 */
@Singleton
class DefaultPendingOpeningStore internal constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val scope: CoroutineScope,
    private val file: () -> File,
) : PendingOpeningStore {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(ioDispatcher, CoroutineScope(SupervisorJob() + ioDispatcher), { context.dataStoreFile(FILE_NAME) })

    private val dataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = scope,
            produceFile = file,
        )
    }

    override fun getFlow(): Flow<PendingOpening?> = dataStore.data
        .map { preferences -> preferences.toOpening() }
        .catch { error ->
            if (error is IOException) {
                Log.e(TAG, "Unable to read the pending opening", error)
                emit(null)
            } else {
                throw error
            }
        }

    override suspend fun save(opening: PendingOpening) {
        withContext(ioDispatcher) {
            try {
                dataStore.updateData { preferences ->
                    preferences.toMutablePreferences().apply {
                        this[LIBRARY_ID] = opening.libraryId
                        this[KIND] = opening.kind.name
                    }
                }
            } catch (error: IOException) {
                Log.e(TAG, "Unable to save the pending opening", error)
            }
        }
    }

    override suspend fun clear() {
        withContext(ioDispatcher) {
            try {
                dataStore.updateData { preferences ->
                    preferences.toMutablePreferences().apply {
                        remove(LIBRARY_ID)
                        remove(KIND)
                    }
                }
            } catch (error: IOException) {
                Log.e(TAG, "Unable to clear the pending opening", error)
            }
        }
    }

    /** The opening these preferences hold, or null when the library id is missing or the kind is unknown. */
    private fun Preferences.toOpening(): PendingOpening? {
        val libraryId = this[LIBRARY_ID] ?: return null
        val kind = SwitchKind.entries.firstOrNull { it.name == this[KIND] } ?: return null
        return PendingOpening(libraryId, kind)
    }

    private companion object {
        const val TAG = "PendingOpeningStore"
        const val FILE_NAME = "pending_opening.preferences_pb"
        val LIBRARY_ID = stringPreferencesKey("library_id")
        val KIND = stringPreferencesKey("kind")
    }
}
