package com.omnibus.omnibus.data.configuration

import kotlinx.coroutines.flow.Flow

interface ServerConfigurationRepository {
    suspend fun validateServerAddress(address: String): ServerValidationResult
    suspend fun saveServerConfig(address: String)

    fun fetchSavedServerConfig(): Flow<String?>
}

interface ServerValidationResult {
    data object Success : ServerValidationResult
    data object InvalidAddressFormat : ServerValidationResult
    data object ServerNotFound : ServerValidationResult
}