package com.omnibus.omnibus.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.auth.AuthRepository
import com.omnibus.omnibus.data.auth.AuthSignInResult
import com.omnibus.omnibus.data.configuration.ServerConfigurationRepository
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
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    serverConfigRepo: ServerConfigurationRepository,
) : ViewModel() {

    private val loginStatus = MutableStateFlow(LoginStatus.NOT_LOGGED_IN)

    val uiState: StateFlow<LoginUIState> =
        combine(serverConfigRepo.fetchSavedServerConfig(), loginStatus) { serverAddress, status ->

            val loginStatus =
                if (serverAddress.isNullOrBlank()) LoginStatus.MISSING_SERVER_CONFIG else status

            LoginUIState(loginStatus = loginStatus, serverAddress = serverAddress.orEmpty())
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = LoginUIState(),
        )

    fun attemptLogin(username: String, password: String) {
        if (loginStatus.value != LoginStatus.AUTH_PENDING) {
            loginStatus.update { LoginStatus.AUTH_PENDING }

            viewModelScope.launch {
                val signInResult = authRepository.signIn(
                    username = username,
                    password = password
                )

                when (signInResult) {
                    is AuthSignInResult.Success -> {
                        loginStatus.update { LoginStatus.LOGGED_IN }
                    }

                    is AuthSignInResult.NoServer -> {
                        loginStatus.update { LoginStatus.MISSING_SERVER_CONFIG }
                    }

                    is AuthSignInResult.Failure, is AuthSignInResult.Error -> {
                        loginStatus.update { LoginStatus.AUTH_FAILED }
                    }
                }
            }
        }
    }
}

data class LoginUIState(
    val loginStatus: LoginStatus = LoginStatus.NOT_LOGGED_IN,
    val serverAddress: String = "",
)

enum class LoginStatus {
    NOT_LOGGED_IN,
    AUTH_PENDING,
    LOGGED_IN,
    AUTH_FAILED,
    MISSING_SERVER_CONFIG
}
