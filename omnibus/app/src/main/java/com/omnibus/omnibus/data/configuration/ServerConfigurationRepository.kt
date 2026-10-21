package com.omnibus.omnibus.data.configuration

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.omnibus.omnibus.data.configuration.di.ServerConfigPreferences
import com.omnibus.omnibus.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import javax.inject.Inject
import kotlin.apply

class DefaultServerConfigurationRepository @Inject constructor(
    @param:ServerConfigPreferences private val serverConfigPreferences: DataStore<Preferences>,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ServerConfigurationRepository {

    override suspend fun validateServerAddress(address: String): ServerValidationResult {
        return withContext(ioDispatcher) {
            val normalizedAddress = normalizeServerAddress(address)

            normalizedAddress?.let { serverAddress ->
                val probeResponse = probeServerHealth(serverAddress)
                when (probeResponse) {
                    ServerProbeStatus.SERVER_FOUND -> {
                        ServerValidationResult.Success
                    }

                    else -> ServerValidationResult.ServerNotFound
                }
            } ?: ServerValidationResult.InvalidAddressFormat
        }
    }

    override suspend fun saveServerConfig(address: String) {
        serverConfigPreferences.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                this[SERVER_URL_PREFS_KEY] = address
            }
        }
    }

    override fun fetchSavedServerConfig(): Flow<String?> =
        serverConfigPreferences.data.map { configDataStore -> configDataStore[SERVER_URL_PREFS_KEY] }

    private fun probeServerHealth(serverUrl: String): ServerProbeStatus {
        val httpClient = OkHttpClient.Builder().build()
        val request = Request.Builder().url("$serverUrl/api/_health").get().build()
        val probeResponse = httpClient.newCall(request).execute()

        return if (probeResponse.isSuccessful) {
            ServerProbeStatus.SERVER_FOUND
        } else {
            ServerProbeStatus.SERVER_NOT_FOUND
        }
    }

    private fun normalizeServerAddress(address: String): String? {
        var normalized = address.trim()
        if (normalized.isEmpty()) return null
        if (!normalized.contains("://")) {
            normalized = "http://$normalized"
        }
        normalized = normalized.trimEnd('/')
        val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
        if (uri.host == null) return null
        return normalized
    }

    companion object {
        val SERVER_URL_PREFS_KEY = stringPreferencesKey("server_address")
    }
}

interface ServerConfigurationRepository {
    suspend fun validateServerAddress(address: String): ServerValidationResult
    suspend fun saveServerConfig(address: String)

    fun fetchSavedServerConfig(): Flow<String?>
}

enum class ServerProbeStatus {
    SERVER_FOUND,
    SERVER_NOT_FOUND
}

abstract interface ServerValidationResult {
    data object Success : ServerValidationResult
    data object InvalidAddressFormat : ServerValidationResult
    data object ServerNotFound : ServerValidationResult
}