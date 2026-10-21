package com.omnibus.omnibus.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.auth.AuthLoginResult
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

    private val _uiState: MutableStateFlow<LoginState> =
        MutableStateFlow(LoginState.Rest)
    val uiState: StateFlow<LoginState> = _uiState.asStateFlow()

    fun login(username: String, password: String) {
        _uiState.update { LoginState.Loading }
        viewModelScope.launch {

            val loginResult =
                authRepo.login(username, password)

            when (loginResult) {
                is AuthLoginResult.Authenticated -> {
                    _uiState.update { LoginState.Success }
                }

                is AuthLoginResult.Unauthenticated -> {
                    _uiState.update { LoginState.Failure(error = "Invalid username or password; please try again.") }
                }

                is AuthLoginResult.Error -> {
                    _uiState.update { LoginState.Failure("An unexpected error has occurred; please try again.") }
                }
            }
        }
    }
}

data class LoginScreenUIState(
    val loginState: LoginState = LoginState.Rest
)

abstract interface LoginState {
    data object Rest : LoginState
    data object Success : LoginState
    data class Failure(val error: String) : LoginState
    data object Loading : LoginState
}