package com.omnibus.omnibus.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginScreenViewModel @Inject constructor(private val authRepo: AuthRepository) : ViewModel() {

    private val _uiState: MutableStateFlow<LoginScreenUIState> =
        MutableStateFlow(LoginScreenUIState())
    val uiState: StateFlow<LoginScreenUIState> = _uiState.asStateFlow()

    fun updateUsername(username: String) {
        _uiState.update { it.copy(usernameField = username) }
    }

    fun updatePassword(password: String) {
        _uiState.update { it.copy(passwordField = password) }
    }

    fun login() {
        viewModelScope.launch {
            authRepo.login(uiState.value.usernameField, uiState.value.passwordField)
        }
    }
}

data class LoginScreenUIState(
    val usernameField: String = "",
    val passwordField: String = "",
    val loginAttempt: LoginAttemptState = LoginAttemptState.Rest
)

abstract interface LoginAttemptState {
    data object Rest : LoginAttemptState
    data object Success : LoginAttemptState
    data class Failure(val error: String) : LoginAttemptState
    data object Loading : LoginAttemptState
}