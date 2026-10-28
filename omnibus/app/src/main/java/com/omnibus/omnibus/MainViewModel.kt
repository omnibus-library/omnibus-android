package com.omnibus.omnibus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.auth.AuthRepository
import com.omnibus.omnibus.data.configuration.ServerConfigurationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Decides which screen the app opens on.
 *
 * The saved server configuration is read once at launch. It is deliberately not observed after
 * that, so saving or changing the server later never changes [startDestination]. The session is
 * observed only so a session that ends returns to login.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    serverConfigRepo: ServerConfigurationRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    /** [StartDestination.Loading] until the saved server and the session have both been read. */
    val startDestination: StateFlow<StartDestination> = combine(
        serverConfigRepo.getSavedServerConfigFlow(),
        authRepository.isSignedIn,
    ) { serverAddress, signedIn ->
        when {
            serverAddress.isNullOrBlank() -> StartDestination.ServerConfiguration
            signedIn -> StartDestination.SignedIn
            else -> StartDestination.Login
        }
    }
        .take(1)
        // An unreadable store is treated as "not configured" so the user can set a server up again.
        .catch { emit(StartDestination.ServerConfiguration) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = StartDestination.Loading,
        )

    /**
     * Whether a session is stored, or null until that has been read.
     *
     * Null is not signed out. Treating it as false would send the shell back to login on a cold start.
     */
    val isSignedIn: StateFlow<Boolean?> = authRepository.isSignedIn.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )

    init {
        viewModelScope.launch {
            // Once per ViewModel, so it survives rotation. The outcome reaches the UI through isSignedIn.
            if (startDestination.first { it != StartDestination.Loading } == StartDestination.SignedIn) {
                authRepository.verifySession()
            }
        }
    }
}

/** The screen the app should open on. */
sealed interface StartDestination {
    /** The saved configuration hasn't been read yet. */
    data object Loading : StartDestination

    /** No server is saved, so the user needs to configure one. */
    data object ServerConfiguration : StartDestination

    /** A server is saved but no session is, so the user goes to login. */
    data object Login : StartDestination

    /** A server and a session are saved, so the user can go straight to the signed-in shell. */
    data object SignedIn : StartDestination
}
