package com.omnibus.omnibus.data.configuration

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.omnibus.omnibus.data.auth.local.AuthTokenStore
import com.omnibus.omnibus.data.configuration.di.ServerConfigPreferences
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.di.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import javax.inject.Inject


class DefaultServerConfigurationRepository @Inject constructor(
    @param:ServerConfigPreferences private val serverConfigPreferences: DataStore<Preferences>,
    private val omnibusHttpClient: OmnibusHttpClient,
    private val authTokenStore: AuthTokenStore,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ServerConfigurationRepository {

    override suspend fun validateServerAddress(address: String): ServerValidationResult {
        return withContext(ioDispatcher) {
            val normalizedAddress = ServerAddress.normalize(address)

            normalizedAddress?.let { serverAddress ->
                val probeResponse = probeServerHealth(serverAddress.toHttpUrl())
                when (probeResponse) {
                    ServerProbeStatus.SERVER_FOUND -> {
                        ServerValidationResult.Success(serverAddress = serverAddress)
                    }

                    else -> ServerValidationResult.ServerNotFound
                }
            } ?: ServerValidationResult.InvalidAddressFormat
        }
    }

    /** Saves [address]. Saving a different server than before signs the user out of the old one. */
    override suspend fun saveServerConfig(address: String) {
        var previousAddress: String? = null
        serverConfigPreferences.updateData { prefs ->
            previousAddress = prefs[SERVER_URL_PREFS_KEY]
            prefs.toMutablePreferences().apply {
                this[SERVER_URL_PREFS_KEY] = address
            }
        }

        val previousServer = previousAddress?.let(ServerAddress::normalize)
        if (previousServer != ServerAddress.normalize(address)) {
            // A token is only valid on the server that issued it.
            clearAuthToken()
        }
    }

    override fun getSavedServerConfigFlow(): Flow<String?> =
        serverConfigPreferences.data.map { configDataStore -> configDataStore[SERVER_URL_PREFS_KEY] }

    private fun probeServerHealth(serverUrl: HttpUrl): ServerProbeStatus {
        val request = omnibusHttpClient.request(serverUrl, HEALTH_PATH).get().build()
        val isSuccessful = try {
            omnibusHttpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (_: IOException) {
            false
        }

        return if (isSuccessful) {
            ServerProbeStatus.SERVER_FOUND
        } else {
            ServerProbeStatus.SERVER_NOT_FOUND
        }
    }

    private suspend fun clearAuthToken() {
        try {
            authTokenStore.clear()
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Log.e(TAG, "Unable to clear the auth token for the previous server", ex)
        }
    }

    companion object {
        val SERVER_URL_PREFS_KEY = stringPreferencesKey("server_address")

        private const val TAG = "DefaultServerConfigurationRepository"
        private const val HEALTH_PATH = "api/_health"
    }
}

enum class ServerProbeStatus {
    SERVER_FOUND,
    SERVER_NOT_FOUND
}
