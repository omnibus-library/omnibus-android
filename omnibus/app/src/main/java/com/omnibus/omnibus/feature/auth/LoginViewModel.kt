package com.omnibus.omnibus.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.auth.AuthRepository
import com.omnibus.omnibus.data.auth.AuthSignInResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState: MutableStateFlow<LoginUIState> = MutableStateFlow(LoginUIState())
    val uiState: StateFlow<LoginUIState> = _uiState.asStateFlow()

    fun attemptLogin(username: String, password: String) {
        if (uiState.value.loginStatus != LoginStatus.AUTH_PENDING) {
            viewModelScope.launch {
                val signInResult = authRepository.signIn(
                    username = username,
                    password = password
                )

                when (signInResult) {
                    is AuthSignInResult.Success -> {
                        _uiState.update { it.copy(loginStatus = LoginStatus.LOGGED_IN) }
                    }
                    else -> {
                        _uiState.update { it.copy(loginStatus = LoginStatus.AUTH_FAILED) }
                    }
                }
            }
        }
    }
}

data class LoginUIState(
    val loginStatus: LoginStatus = LoginStatus.NOT_LOGGED_IN
)

enum class LoginStatus {
    NOT_LOGGED_IN,
    AUTH_PENDING,
    LOGGED_IN,
    AUTH_FAILED
}