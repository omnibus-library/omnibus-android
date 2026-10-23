package com.omnibus.omnibus.data.configuration

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

/** In-memory [ServerConfigurationRepository] for tests. */
class FakeServerConfigurationRepository(initialAddress: String? = null) :
    ServerConfigurationRepository {

    val savedAddress = MutableStateFlow(initialAddress)

    /** When set, [fetchSavedServerConfig] fails with this instead of emitting. */
    var readFailure: Throwable? = null

    override suspend fun validateServerAddress(address: String): ServerValidationResult =
        ServerValidationResult.Success

    override suspend fun saveServerConfig(address: String) {
        savedAddress.value = address
    }

    override fun fetchSavedServerConfig(): Flow<String?> = flow {
        readFailure?.let { throw it }
        savedAddress.collect { emit(it) }
    }
}
