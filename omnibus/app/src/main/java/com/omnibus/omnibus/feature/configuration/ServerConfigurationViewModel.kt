package com.omnibus.omnibus.feature.configuration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.configuration.ServerConfigurationRepository
import com.omnibus.omnibus.data.configuration.ServerValidationResult
import com.omnibus.omnibus.data.libraries.Library
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ServerConfigurationViewModel @Inject constructor(
    private val serverConfigRepo: ServerConfigurationRepository,
) : ViewModel() {

    private val serverConfigState: StateFlow<String?> = serverConfigRepo.getSavedServerConfigFlow()
        .stateIn(scope = viewModelScope, initialValue = "", started = SharingStarted.Lazily)

    // #371 the status and what it carries change together, so they are one state
    private val submission = MutableStateFlow(Submission())

    val uiState: StateFlow<ServerConfigUIState> =
        combine(serverConfigState, submission) { serverConfig, state ->
            ServerConfigUIState(
                submissionStatus = state.status,
                savedConfigurationValue = serverConfig ?: "",
                acceptedAddress = state.acceptedAddress,
                alreadySaved = state.alreadySaved,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = ServerConfigUIState()
        )

    fun submitServerUrl(serverUrl: String) {
        submission.update { Submission(ServerConfigSubmissionStatus.PENDING) }

        viewModelScope.launch {
            val submissionResult = serverConfigRepo.validateServerAddress(address = serverUrl)
            val submissionStatus = when (submissionResult) {
                is ServerValidationResult.Success -> ServerConfigSubmissionStatus.ACCEPTED
                is ServerValidationResult.InvalidAddressFormat -> ServerConfigSubmissionStatus.ERROR
                is ServerValidationResult.ServerNotFound -> ServerConfigSubmissionStatus.NOT_ACCEPTED
                // validateServerAddress never returns AlreadySaved
                is ServerValidationResult.AlreadySaved -> ServerConfigSubmissionStatus.ERROR
            }

            if (submissionResult is ServerValidationResult.Success) {
                serverConfigRepo.saveServerConfig(address = submissionResult.serverAddress)
            }

            submission.update { Submission(submissionStatus) }
        }
    }

    // #371
    /** Checks a new library's [serverUrl]. Never saves it: the sign-in that follows adds the library. */
    fun submitNewLibraryUrl(serverUrl: String) {
        submission.update { Submission(ServerConfigSubmissionStatus.PENDING) }

        viewModelScope.launch {
            val next = when (val result = serverConfigRepo.validateNewLibraryAddress(address = serverUrl)) {
                is ServerValidationResult.Success ->
                    Submission(ServerConfigSubmissionStatus.ACCEPTED, acceptedAddress = result.serverAddress)
                is ServerValidationResult.AlreadySaved ->
                    Submission(ServerConfigSubmissionStatus.ALREADY_SAVED, alreadySaved = result.library)
                is ServerValidationResult.InvalidAddressFormat -> Submission(ServerConfigSubmissionStatus.ERROR)
                is ServerValidationResult.ServerNotFound -> Submission(ServerConfigSubmissionStatus.NOT_ACCEPTED)
            }
            submission.update { next }
        }
    }

    // #371
    /** The reader changed the address, so an Already saved result no longer applies. */
    fun editAddress() {
        submission.update { if (it.status == ServerConfigSubmissionStatus.ALREADY_SAVED) Submission() else it }
    }

    // #371
    /** The screen has handed an accepted address on, so it is no longer pending a visit. */
    fun connectionShown() {
        submission.update { if (it.status == ServerConfigSubmissionStatus.ACCEPTED) Submission() else it }
    }

    /** The submission's status and what it carries, as one state. */
    private data class Submission(
        val status: ServerConfigSubmissionStatus = ServerConfigSubmissionStatus.UNSUBMITTED,
        val acceptedAddress: String? = null,
        val alreadySaved: Library? = null,
    )
}

data class ServerConfigUIState(
    val submissionStatus: ServerConfigSubmissionStatus = ServerConfigSubmissionStatus.UNSUBMITTED,
    val savedConfigurationValue: String = "",
    // #371
    /** The address that was accepted, once [submissionStatus] is ACCEPTED. */
    val acceptedAddress: String? = null,
    /** The saved library that matches the address, once [submissionStatus] is ALREADY_SAVED. */
    val alreadySaved: Library? = null,
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
    ERROR,

    // #371
    /** A new library's address matches a saved library. */
    ALREADY_SAVED,
}