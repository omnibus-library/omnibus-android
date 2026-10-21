package com.omnibus.omnibus.feature.libraryconfiguration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.libraryconfiguration.LibraryConfigurationRepository
import com.omnibus.omnibus.data.libraryconfiguration.ServerConfigSaveResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

class LibraryConfigurationScreenViewModel @Inject constructor(
    private val libraryConfigRepo: LibraryConfigurationRepository
) : ViewModel() {

    private var _uiState: MutableStateFlow<LibraryConfigurationUIState> =
        MutableStateFlow(LibraryConfigurationUIState.Rest)
    val uiState: StateFlow<LibraryConfigurationUIState> = _uiState.asStateFlow()

    fun submitServerConfig(serverAddress: String) {
        _uiState.update { LibraryConfigurationUIState.Loading }

        viewModelScope.launch {
            val result = libraryConfigRepo.saveServerConfiguration(serverAddress)
            when (result) {
                ServerConfigSaveResult.ValidationSuccessful -> _uiState.update {
                    LibraryConfigurationUIState.ConfigurationComplete
                }

                ServerConfigSaveResult.ValidationFailed -> _uiState.update {
                    LibraryConfigurationUIState.Error("Could not validate a running Omnibus server with the provided URL.")
                }

                else -> {
                    LibraryConfigurationUIState.Error("An unexpected error has occurred; please try again.")
                }
            }
        }
    }
}

abstract interface LibraryConfigurationUIState {
    data object Loading : LibraryConfigurationUIState
    data class Error(val message: String) : LibraryConfigurationUIState
    data object ConfigurationComplete : LibraryConfigurationUIState
    data object Rest : LibraryConfigurationUIState
}
