package com.omnibus.omnibus.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.auth.AuthNewLibraryResult
import com.omnibus.omnibus.data.auth.AuthRegisterResult
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

    // #371
    /** The library a sign-in in adding mode saved, once it has. */
    private val addedLibraryId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<LoginUIState> =
        combine(
            serverConfigRepo.getSavedServerConfigFlow(),
            loginStatus,
            addedLibraryId,
        ) { serverAddress, status, added ->

            val loginStatus =
                if (serverAddress.isNullOrBlank()) LoginStatus.MISSING_SERVER_CONFIG else status

            LoginUIState(
                loginStatus = loginStatus,
                serverAddress = serverAddress.orEmpty(),
                addedLibraryId = added,
            )
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

                    AuthSignInResult.InvalidCredentials -> {
                        loginStatus.update { LoginStatus.INVALID_CREDENTIALS }
                    }

                    AuthSignInResult.TooManyAttempts -> {
                        loginStatus.update { LoginStatus.TOO_MANY_ATTEMPTS }
                    }

                    AuthSignInResult.Unreachable -> {
                        loginStatus.update { LoginStatus.SERVER_UNREACHABLE }
                    }

                    AuthSignInResult.ServerError -> {
                        loginStatus.update { LoginStatus.SERVER_ERROR }
                    }
                }
            }
        }
    }

    fun attemptRegister(username: String, password: String) {
        if (loginStatus.value != LoginStatus.AUTH_PENDING) {
            loginStatus.update { LoginStatus.AUTH_PENDING }

            viewModelScope.launch {
                val registerResult = authRepository.register(
                    username = username,
                    password = password
                )

                when (registerResult) {
                    is AuthRegisterResult.Success -> {
                        loginStatus.update { LoginStatus.LOGGED_IN }
                    }

                    is AuthRegisterResult.NoServer -> {
                        loginStatus.update { LoginStatus.MISSING_SERVER_CONFIG }
                    }

                    AuthRegisterResult.UsernameTaken -> {
                        loginStatus.update { LoginStatus.USERNAME_TAKEN }
                    }

                    AuthRegisterResult.RegistrationDisabled -> {
                        loginStatus.update { LoginStatus.REGISTRATION_DISABLED }
                    }

                    AuthRegisterResult.InvalidDetails -> {
                        loginStatus.update { LoginStatus.INVALID_DETAILS }
                    }

                    AuthRegisterResult.TooManyAttempts -> {
                        loginStatus.update { LoginStatus.TOO_MANY_ATTEMPTS }
                    }

                    AuthRegisterResult.Unreachable -> {
                        loginStatus.update { LoginStatus.SERVER_UNREACHABLE }
                    }

                    AuthRegisterResult.ServerError -> {
                        loginStatus.update { LoginStatus.SERVER_ERROR }
                    }
                }
            }
        }
    }

    // #371
    /** Signs in to the library at [address], which isn't saved yet, and adds it. */
    fun attemptAddLibrary(address: String, username: String, password: String) {
        attemptNewLibrary(address, username, password, createAccount = false)
    }

    // #371
    /** Registers an account on the library at [address], which isn't saved yet, and adds it. */
    fun attemptRegisterNewLibrary(address: String, username: String, password: String) {
        attemptNewLibrary(address, username, password, createAccount = true)
    }

    private fun attemptNewLibrary(address: String, username: String, password: String, createAccount: Boolean) {
        if (loginStatus.value != LoginStatus.AUTH_PENDING) {
            loginStatus.update { LoginStatus.AUTH_PENDING }

            viewModelScope.launch {
                val result = authRepository.signInToNewLibrary(
                    address = address,
                    username = username,
                    password = password,
                    createAccount = createAccount,
                )

                when (result) {
                    is AuthNewLibraryResult.Success -> {
                        addedLibraryId.update { result.libraryId }
                        loginStatus.update { LoginStatus.LOGGED_IN }
                    }

                    AuthNewLibraryResult.AlreadySaved,
                    AuthNewLibraryResult.ServerError -> {
                        loginStatus.update { LoginStatus.SERVER_ERROR }
                    }

                    AuthNewLibraryResult.InvalidCredentials -> {
                        loginStatus.update { LoginStatus.INVALID_CREDENTIALS }
                    }

                    AuthNewLibraryResult.TooManyAttempts -> {
                        loginStatus.update { LoginStatus.TOO_MANY_ATTEMPTS }
                    }

                    AuthNewLibraryResult.UsernameTaken -> {
                        loginStatus.update { LoginStatus.USERNAME_TAKEN }
                    }

                    AuthNewLibraryResult.RegistrationDisabled -> {
                        loginStatus.update { LoginStatus.REGISTRATION_DISABLED }
                    }

                    AuthNewLibraryResult.InvalidDetails -> {
                        loginStatus.update { LoginStatus.INVALID_DETAILS }
                    }

                    AuthNewLibraryResult.Unreachable -> {
                        loginStatus.update { LoginStatus.SERVER_UNREACHABLE }
                    }
                }
            }
        }
    }
}

data class LoginUIState(
    val loginStatus: LoginStatus = LoginStatus.NOT_LOGGED_IN,
    val serverAddress: String = "",
    // #371
    /** The library a sign-in in adding mode saved, once [loginStatus] is LOGGED_IN. */
    val addedLibraryId: String? = null,
)

enum class LoginStatus {
    NOT_LOGGED_IN,
    AUTH_PENDING,
    LOGGED_IN,
    INVALID_CREDENTIALS,
    TOO_MANY_ATTEMPTS,
    SERVER_UNREACHABLE,
    SERVER_ERROR,
    USERNAME_TAKEN,
    REGISTRATION_DISABLED,
    INVALID_DETAILS,
    MISSING_SERVER_CONFIG,
}

/** Whether this status is a failed sign-in or registration that should show an inline error. */
val LoginStatus.isFailure: Boolean
    get() = when (this) {
        LoginStatus.INVALID_CREDENTIALS,
        LoginStatus.TOO_MANY_ATTEMPTS,
        LoginStatus.SERVER_UNREACHABLE,
        LoginStatus.SERVER_ERROR,
        LoginStatus.USERNAME_TAKEN,
        LoginStatus.REGISTRATION_DISABLED,
        LoginStatus.INVALID_DETAILS,
        -> true

        LoginStatus.NOT_LOGGED_IN,
        LoginStatus.AUTH_PENDING,
        LoginStatus.LOGGED_IN,
        LoginStatus.MISSING_SERVER_CONFIG,
        -> false
    }
