package com.omnibus.omnibus.data.configuration

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.omnibus.omnibus.data.configuration.di.ServerConfigPreferences
import com.omnibus.omnibus.data.network.ServerBaseUrlProvider
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Reads the base URL from the server address saved by [ServerConfigurationRepository]. */
class SavedServerBaseUrlProvider @Inject constructor(
    @param:ServerConfigPreferences private val serverConfigPreferences: DataStore<Preferences>,
) : ServerBaseUrlProvider {

    override suspend fun baseUrl(): HttpUrl? {
        val saved = try {
            serverConfigPreferences.data.first()[DefaultServerConfigurationRepository.SERVER_URL_PREFS_KEY]
        } catch (ex: IOException) {
            Log.e(TAG, "Unable to read the saved server address", ex)
            null
        }
        // Normalized on read too, since addresses saved by older builds are stored as typed.
        return saved?.let(ServerAddress::normalize)?.toHttpUrl()
    }

    private companion object {
        const val TAG = "SavedServerBaseUrlProvider"
    }
}
