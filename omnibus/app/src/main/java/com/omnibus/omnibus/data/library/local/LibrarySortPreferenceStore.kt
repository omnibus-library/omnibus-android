package com.omnibus.omnibus.data.library.local

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.omnibus.omnibus.data.library.LibrarySortPreference
import com.omnibus.omnibus.data.library.di.LibraryPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

/** Per-device storage for the Library's sort, direction and grouping. Reads are observed as a [Flow]; writes are suspend. */
interface LibrarySortPreferenceStore {

    /** The stored preference. Emits the defaults when nothing is stored or the read fails. */
    val preference: Flow<LibrarySortPreference>

    /** Stores [preference], replacing the previous one. Failures are logged and not thrown. */
    suspend fun save(preference: LibrarySortPreference)

    /** Whether the Library groups its books by series. Emits false when nothing is stored or the read fails. */
    val groupBySeries: Flow<Boolean>

    /** Stores whether the Library groups its books by series. Failures are logged and not thrown. */
    suspend fun saveGroupBySeries(enabled: Boolean)
}

/**
 * [LibrarySortPreferenceStore] backed by DataStore. Each field is its own string key holding the
 * raw wire token, so an unknown stored value is decoded per field rather than discarding both.
 */
class DefaultLibrarySortPreferenceStore @Inject constructor(
    @param:LibraryPreferences private val dataStore: DataStore<Preferences>,
) : LibrarySortPreferenceStore {

    override val preference: Flow<LibrarySortPreference> = dataStore.data
        .map { prefs -> LibrarySortPreference.decode(prefs[SORT_KEY], prefs[DIRECTION_KEY]) }
        .catch { error ->
            if (error is IOException) {
                Log.w(TAG, "Unable to read the library sort", error)
                emit(LibrarySortPreference())
            } else {
                throw error
            }
        }

    override suspend fun save(preference: LibrarySortPreference) {
        try {
            dataStore.updateData { prefs ->
                prefs.toMutablePreferences().apply {
                    this[SORT_KEY] = preference.sort.apiName
                    this[DIRECTION_KEY] = preference.direction.apiName
                }
            }
        } catch (error: IOException) {
            Log.e(TAG, "Unable to save the library sort", error)
        }
    }

    override val groupBySeries: Flow<Boolean> = dataStore.data
        .map { prefs -> prefs[GROUP_BY_SERIES_KEY] ?: false }
        .catch { error ->
            if (error is IOException) {
                Log.w(TAG, "Unable to read the series grouping", error)
                emit(false)
            } else {
                throw error
            }
        }

    override suspend fun saveGroupBySeries(enabled: Boolean) {
        try {
            dataStore.updateData { prefs ->
                prefs.toMutablePreferences().apply { this[GROUP_BY_SERIES_KEY] = enabled }
            }
        } catch (error: IOException) {
            Log.e(TAG, "Unable to save the series grouping", error)
        }
    }

    companion object {
        /** Holds the sort's wire token. The key names are part of the stored format, so keep them stable. */
        val SORT_KEY = stringPreferencesKey("library.sort")

        /** Holds the direction's wire token. */
        val DIRECTION_KEY = stringPreferencesKey("library.sortDirection")

        /** Holds whether the Library groups its books by series. */
        val GROUP_BY_SERIES_KEY = booleanPreferencesKey("library.groupBySeries")

        private const val TAG = "LibrarySortPreferenceStore"
    }
}
