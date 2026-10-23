package com.omnibus.omnibus.data.configuration

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

/** In-memory [ServerConfigurationRepository] for tests. */
class FakeServerConfigurationRepository(initialAddress: String? = null) :
    ServerConfigurationRepository {

    val savedAddress = MutableStateFlow(initialAddress)

    /** Returned by [validateServerAddress]. Defaults to accepting the address unchanged. */
    var validationResult: (address: String) -> ServerValidationResult =
        { ServerValidationResult.Success(serverAddress = it) }

    /** When set, [validateServerAddress] suspends until this completes, to observe pending state. */
    var validationGate: CompletableDeferred<Unit>? = null

    /** Every address passed to [validateServerAddress], in order. */
    val validatedAddresses = mutableListOf<String>()

    /** When set, [fetchSavedServerConfig] fails with this instead of emitting. */
    var readFailure: Throwable? = null

    override suspend fun validateServerAddress(address: String): ServerValidationResult {
        validatedAddresses += address
        validationGate?.await()
        return validationResult(address)
    }

    override suspend fun saveServerConfig(address: String) {
        savedAddress.value = address
    }

    override fun fetchSavedServerConfig(): Flow<String?> = flow {
        readFailure?.let { throw it }
        savedAddress.collect { emit(it) }
    }
}
