package com.omnibus.omnibus.feature.configuration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.configuration.ServerConfigurationRepository
import com.omnibus.omnibus.data.configuration.ServerValidationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ServerConfigurationViewModel @Inject constructor(
    private val serverConfigRepo: ServerConfigurationRepository,
) : ViewModel() {

    private val _uiState: MutableStateFlow<ServerConfigUIState> =
        MutableStateFlow(ServerConfigUIState())
    val uiState: StateFlow<ServerConfigUIState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            serverConfigRepo.fetchSavedServerConfig().map { savedConfigValue ->
                _uiState.update { it.copy(savedConfigurationValue = savedConfigValue ?: "") }
            }
        }
    }

    fun submitServerUrl(serverUrl: String) {
        _uiState.update { it.copy(submissionStatus = ServerConfigSubmissionStatus.PENDING) }
        viewModelScope.launch {
            val submissionResult = serverConfigRepo.validateServerAddress(address = serverUrl)
            val submissionStatus = when (submissionResult) {
                is ServerValidationResult.Success -> ServerConfigSubmissionStatus.ACCEPTED
                is ServerValidationResult.InvalidAddressFormat -> ServerConfigSubmissionStatus.ERROR
                is ServerValidationResult.ServerNotFound -> ServerConfigSubmissionStatus.NOT_ACCEPTED
                else -> ServerConfigSubmissionStatus.UNSUBMITTED
            }

            _uiState.update { it.copy(submissionStatus = submissionStatus) }
        }
    }
}

data class ServerConfigUIState(
    val submissionStatus: ServerConfigSubmissionStatus = ServerConfigSubmissionStatus.UNSUBMITTED,
    val savedConfigurationValue: String = ""
) {
    val submitButtonEnabled: Boolean = when (submissionStatus) {
        ServerConfigSubmissionStatus.UNSUBMITTED,
        ServerConfigSubmissionStatus.ERROR,
        ServerConfigSubmissionStatus.NOT_ACCEPTED -> true

        else -> false
    }
}

enum class ServerConfigSubmissionStatus {
    UNSUBMITTED,
    PENDING,
    ACCEPTED,
    NOT_ACCEPTED,
    ERROR
}