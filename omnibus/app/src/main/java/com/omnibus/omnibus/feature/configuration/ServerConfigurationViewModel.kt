package com.omnibus.omnibus.feature.configuration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.configuration.ServerConfigurationRepository
import com.omnibus.omnibus.data.configuration.ServerValidationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@HiltViewModel
class ServerConfigurationViewModel @Inject constructor(
    private val serverConfigRepo: ServerConfigurationRepository,
) : ViewModel() {

    private val serverConfigState: StateFlow<String?> = serverConfigRepo.fetchSavedServerConfig()
        .stateIn(scope = viewModelScope, initialValue = "", started = SharingStarted.Lazily)

    private var _submissionStatusState = MutableStateFlow(ServerConfigSubmissionStatus.UNSUBMITTED)

    val uiState: StateFlow<ServerConfigUIState> =
        combine(serverConfigState, _submissionStatusState) { serverConfig, submissionStatus ->
            ServerConfigUIState(
                submissionStatus = submissionStatus,
                savedConfigurationValue = serverConfig ?: ""
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = ServerConfigUIState()
        )

    fun submitServerUrl(serverUrl: String) {
        _submissionStatusState.update { ServerConfigSubmissionStatus.PENDING }

        viewModelScope.launch {
            val submissionResult = serverConfigRepo.validateServerAddress(address = serverUrl)
            val submissionStatus = when (submissionResult) {
                is ServerValidationResult.Success -> ServerConfigSubmissionStatus.ACCEPTED
                is ServerValidationResult.InvalidAddressFormat -> ServerConfigSubmissionStatus.ERROR
                is ServerValidationResult.ServerNotFound -> ServerConfigSubmissionStatus.NOT_ACCEPTED
                else -> ServerConfigSubmissionStatus.UNSUBMITTED
            }

            if (submissionResult == ServerValidationResult.Success) {
                serverConfigRepo.saveServerConfig(address = serverUrl)
            }

            _submissionStatusState.update { submissionStatus }
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