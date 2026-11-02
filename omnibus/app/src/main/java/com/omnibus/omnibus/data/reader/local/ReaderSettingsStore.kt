package com.omnibus.omnibus.data.reader.local

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.omnibus.omnibus.data.reader.ReaderSettings
import com.omnibus.omnibus.data.reader.di.ReaderPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

/** Per-device storage for the reader's typography. Settings are global, not per book. */
interface ReaderSettingsStore {

    /** The stored settings. Emits the defaults when nothing is stored or the read fails. */
    val settings: Flow<ReaderSettings>

    /** Stores [settings], replacing the previous value. Failures are logged and not thrown. */
    suspend fun save(settings: ReaderSettings)
}

/** [ReaderSettingsStore] backed by a Preferences DataStore, holding the settings as one JSON object. */
class DefaultReaderSettingsStore @Inject constructor(
    @param:ReaderPreferences private val dataStore: DataStore<Preferences>,
) : ReaderSettingsStore {

    override val settings: Flow<ReaderSettings> = dataStore.data
        .map { prefs -> ReaderSettingsJson.decode(prefs[SETTINGS_KEY]) }
        .catch { error ->
            if (error is IOException) {
                Log.w(TAG, "Unable to read the reader settings", error)
                emit(ReaderSettings())
            } else {
                throw error
            }
        }

    override suspend fun save(settings: ReaderSettings) {
        try {
            dataStore.updateData { prefs ->
                prefs.toMutablePreferences().apply {
                    this[SETTINGS_KEY] = ReaderSettingsJson.encode(settings)
                }
            }
        } catch (error: IOException) {
            Log.e(TAG, "Unable to save the reader settings", error)
        }
    }

    companion object {
        /** Holds the settings JSON. The key name is part of the stored format, so keep it stable. */
        val SETTINGS_KEY = stringPreferencesKey("reader.settings")

        private const val TAG = "ReaderSettingsStore"
    }
}
