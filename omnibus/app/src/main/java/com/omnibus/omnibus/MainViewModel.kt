package com.omnibus.omnibus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.auth.AuthRepository
import com.omnibus.omnibus.data.configuration.ServerConfigurationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import javax.inject.Inject

/**
 * Decides which screen the app opens on.
 *
 * The saved server configuration is read once at launch. It is deliberately not observed after
 * that, so saving or changing the server later never triggers navigation from here.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    serverConfigRepo: ServerConfigurationRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    /** [StartDestination.Loading] until the saved configuration has been read. */
    val startDestination: StateFlow<StartDestination> = serverConfigRepo.fetchSavedServerConfig()
        .take(1)
        // An unreadable store is treated as "not configured" so the user can set a server up again.
        .catch { emit(null) }
        .map { serverAddress ->
            if (serverAddress.isNullOrBlank()) StartDestination.ServerConfiguration
            else StartDestination.Login
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = StartDestination.Loading,
        )

    /** Whether a session is stored, or null until that has been read. */
    // STUB(Stage 5): replace with authRepository.isSignedIn.stateIn(viewModelScope, Eagerly, null).
    val isSignedIn: StateFlow<Boolean?>
        get() = TODO("Stage 5")
}

/** The screen the app should open on. */
sealed interface StartDestination {
    /** The saved configuration hasn't been read yet. */
    data object Loading : StartDestination

    /** No server is saved, so the user needs to configure one. */
    data object ServerConfiguration : StartDestination

    /** A server is saved, so the user can go straight to login. */
    data object Login : StartDestination

    /** A server and a session are saved, so the user can go straight to the signed-in shell. */
    data object SignedIn : StartDestination
}
