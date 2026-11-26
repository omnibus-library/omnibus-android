package com.omnibus.omnibus.data.configuration

import com.omnibus.omnibus.data.libraries.Library
import kotlinx.coroutines.flow.Flow

interface ServerConfigurationRepository {
    suspend fun validateServerAddress(address: String): ServerValidationResult
    suspend fun saveServerConfig(address: String)

    fun getSavedServerConfigFlow(): Flow<String?>

    // #371
    /**
     * Checks [address] for adding a library. An address that matches a saved library gives
     * [ServerValidationResult.AlreadySaved] without probing. Otherwise it is probed as [validateServerAddress] does.
     * Never saves anything.
     */
    suspend fun validateNewLibraryAddress(address: String): ServerValidationResult
}

sealed interface ServerValidationResult {
    /** @property serverAddress The normalized address (scheme added, trailing slash removed) to save. */
    data class Success(val serverAddress: String) : ServerValidationResult
    data object InvalidAddressFormat : ServerValidationResult
    data object ServerNotFound : ServerValidationResult

    // #371
    /**
     * The address is already saved as [library]. Only [ServerConfigurationRepository.validateNewLibraryAddress]
     * returns it.
     */
    data class AlreadySaved(val library: Library) : ServerValidationResult
}