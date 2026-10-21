package com.omnibus.omnibus.data.libraryconfiguration

import android.util.Log
import com.omnibus.omnibus.data.auth.di.IoDispatcher
import com.omnibus.omnibus.data.auth.remote.OmnibusHttpClient
import com.omnibus.omnibus.data.libraryconfiguration.remote.ServerValidationRequestDto
import com.omnibus.omnibus.data.libraryconfiguration.remote.ServerValidationResponseDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface LibraryConfigurationRepository {
    suspend fun saveServerConfiguration(serverAddress: String): ServerConfigSaveResult
}

class DefaultLibraryConfigurationRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val omnibusHttpClient: OmnibusHttpClient,
) : LibraryConfigurationRepository {

    /**
     * Validates that [serverAddress] points at a live Omnibus server by probing its health
     * endpoint.
     *
     * @param serverAddress absolute server URL including scheme, as entered by the user.
     *
     * @return [ServerConfigSaveResult.ValidationSuccessful] only when the server responds 2xx
     * and identifies itself as a healthy Omnibus server; otherwise
     * [ServerConfigSaveResult.ValidationFailed], including for malformed addresses and
     * unreachable hosts.
     */
    override suspend fun saveServerConfiguration(serverAddress: String): ServerConfigSaveResult {
        return withContext(ioDispatcher) {
            try {
                val request = ServerValidationRequestDto(serverAddress.trim()).toRequest()

                omnibusHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful &&
                        ServerValidationResponseDto.fromJsonString(response.body.string())
                            .isHealthyOmnibusServer
                    ) {
                        ServerConfigSaveResult.ValidationSuccessful
                    } else {
                        ServerConfigSaveResult.ValidationFailed
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, ex.message ?: "Unknown Error")
                ServerConfigSaveResult.ValidationError
            }
        }
    }

    companion object {
        const val TAG = "LibraryConfigurationRepository"
    }
}

sealed interface ServerConfigSaveResult {
    data object ValidationSuccessful : ServerConfigSaveResult
    data object ValidationFailed : ServerConfigSaveResult
    data object ValidationError : ServerConfigSaveResult
}
