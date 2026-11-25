package com.omnibus.omnibus.data.configuration

import com.omnibus.omnibus.data.libraries.LibrariesRepository
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import javax.inject.Inject


class DefaultServerConfigurationRepository @Inject constructor(
    private val librariesRepository: LibrariesRepository,
    private val omnibusHttpClient: OmnibusHttpClient,
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

    /**
     * Saves [address] as the active library's server. With no library yet, adds and activates one. For the same
     * server, nothing changes. For a different one, the active library is removed with its data and token, and a
     * new library is added for the new address, so the user is signed out of the old server.
     */
    override suspend fun saveServerConfig(address: String) {
        withContext(ioDispatcher) {
            val normalized = ServerAddress.normalize(address) ?: address.trim()
            val active = librariesRepository.getActiveLibraryFlow().first()
            when {
                active == null -> librariesRepository.add(normalized, activate = true)
                sameServer(active.address, normalized) -> Unit
                // A token is only valid on the server that issued it, so the old library goes with its token.
                else -> {
                    librariesRepository.remove(active.id)
                    librariesRepository.add(normalized, activate = true)
                }
            }
        }
    }

    override fun getSavedServerConfigFlow(): Flow<String?> =
        librariesRepository.getActiveLibraryFlow().map { it?.address }

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

    /** Whether two addresses name the same server. Host names compare without regard to case. */
    private fun sameServer(a: String, b: String): Boolean {
        val left = ServerAddress.normalize(a)?.toHttpUrlOrNull()
        val right = ServerAddress.normalize(b)?.toHttpUrlOrNull()
        return if (left != null && right != null) left == right else a == b
    }

    companion object {
        private const val HEALTH_PATH = "api/_health"
    }
}

enum class ServerProbeStatus {
    SERVER_FOUND,
    SERVER_NOT_FOUND
}
