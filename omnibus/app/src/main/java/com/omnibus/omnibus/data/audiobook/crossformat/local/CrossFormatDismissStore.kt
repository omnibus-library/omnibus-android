package com.omnibus.omnibus.data.audiobook.crossformat.local

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import com.omnibus.omnibus.data.audiobook.di.ResumeOffersPreferences
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

/**
 * The ebook offers the listener dismissed, kept per book as the source clock of the dismissed position (R8). It is
 * this device's memory and is not account-scoped (A1).
 */
interface CrossFormatDismissStore {

    /** The dismissed source clock of [bookUuid], or null when none is stored or the memory can't be read. */
    suspend fun dismissedClock(bookUuid: String): Long?

    /** Stores [sourceClock] as the dismissed clock of [bookUuid], replacing any earlier one. */
    suspend fun store(bookUuid: String, sourceClock: Long)
}

/**
 * [CrossFormatDismissStore] backed by its own DataStore file. A read failure reads as absent, so the offer shows again;
 * a write failure is logged. Neither throws an IO error.
 */
class DefaultCrossFormatDismissStore @Inject constructor(
    @param:ResumeOffersPreferences private val dataStore: DataStore<Preferences>,
) : CrossFormatDismissStore {

    override suspend fun dismissedClock(bookUuid: String): Long? {
        val prefs = dataStore.data
            .catch { error ->
                if (error is IOException) {
                    Log.w(TAG, "Unable to read the dismissed ebook offers", error)
                    emit(emptyPreferences())
                } else {
                    throw error
                }
            }
            .first()
        return prefs[key(bookUuid)]
    }

    override suspend fun store(bookUuid: String, sourceClock: Long) {
        try {
            dataStore.edit { it[key(bookUuid)] = sourceClock }
        } catch (error: IOException) {
            Log.e(TAG, "Unable to store the dismissed ebook offer", error)
        }
    }

    private fun key(bookUuid: String) = longPreferencesKey("dismissed_clock:$bookUuid:audio")

    private companion object {
        const val TAG = "CrossFormatDismissStore"
    }
}
