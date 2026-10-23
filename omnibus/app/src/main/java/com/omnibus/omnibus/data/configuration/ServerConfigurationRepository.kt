package com.omnibus.omnibus.data.configuration

import kotlinx.coroutines.flow.Flow

interface ServerConfigurationRepository {
    suspend fun validateServerAddress(address: String): ServerValidationResult
    suspend fun saveServerConfig(address: String)

    fun fetchSavedServerConfig(): Flow<String?>
}

sealed interface ServerValidationResult {
    /** @property serverAddress The normalized address (scheme added, trailing slash removed) to save. */
    data class Success(val serverAddress: String) : ServerValidationResult
    data object InvalidAddressFormat : ServerValidationResult
    data object ServerNotFound : ServerValidationResult
}