package com.omnibus.omnibus.feature.requests.start

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.auth.AuthRepository
import com.omnibus.omnibus.data.network.ConnectivityMonitor
import com.omnibus.omnibus.data.requests.RequestKind
import com.omnibus.omnibus.data.requests.RequestsError
import com.omnibus.omnibus.data.requests.RequestsRepository
import com.omnibus.omnibus.data.requests.RequestsResult
import com.omnibus.omnibus.feature.requests.di.RequestWordingSource
import com.omnibus.omnibus.feature.requests.startFailure
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why a start failed, said once. [id] numbers it so two with the same text each show. */
data class RequestStartMessage(val text: String, val id: Long)

/**
 * What the book page's request buttons show.
 *
 * @property canStart Whether the reader has book requests on and may start one. Without it nothing is offered.
 * @property starting The kind being started, so only its button reads "Requesting…" and every button waits.
 * @property pendingOpen The book whose request page the page should open, set when a start succeeds. It lives here, in
 * the ViewModel the nav entry owns, so a fold or a rotation doesn't lose it.
 * @property message Why the last start failed, until it has been shown.
 */
data class RequestStartUIState(
    val canStart: Boolean = false,
    val starting: RequestKind? = null,
    val pendingOpen: String? = null,
    val message: RequestStartMessage? = null,
    val isOnline: Boolean = true,
)

/** Owns the request buttons on one book's page: starting a request, and opening its page once it has started. */
@HiltViewModel(assistedFactory = RequestStartViewModel.Factory::class)
class RequestStartViewModel @AssistedInject constructor(
    @Assisted private val uuid: String,
    private val requests: RequestsRepository,
    authRepository: AuthRepository,
    connectivityMonitor: ConnectivityMonitor,
    private val wordingSource: RequestWordingSource,
) : ViewModel() {

    /** Creates a [RequestStartViewModel] for the book with [uuid]. */
    @AssistedFactory
    interface Factory {
        fun create(uuid: String): RequestStartViewModel
    }

    private val _uiState = MutableStateFlow(RequestStartUIState())

    /** The buttons' state. */
    val uiState: StateFlow<RequestStartUIState> = _uiState.asStateFlow()

    private var nextMessageId = 1L

    init {
        viewModelScope.launch {
            authRepository.currentUser.catch { emit(null) }.collect { user ->
                _uiState.update { it.copy(canStart = user?.bookRequests == true && user.canStartRequests) }
            }
        }
        viewModelScope.launch {
            connectivityMonitor.isOnline.collect { online -> _uiState.update { it.copy(isOnline = online) } }
        }
    }

    /** Starts a request of [kind] on the book. A press while one runs, or offline, is ignored, and its buttons look disabled. */
    fun start(kind: RequestKind) {
        val state = _uiState.value
        if (state.starting != null || !state.canStart || !state.isOnline) return
        _uiState.update { it.copy(starting = kind) }
        viewModelScope.launch {
            when (val result = requests.start(uuid, kind)) {
                is RequestsResult.Success -> _uiState.update { it.copy(starting = null, pendingOpen = uuid) }
                is RequestsResult.Failure -> fail(result.error)
                RequestsResult.NoServer -> fail(RequestsError.Unreachable)
            }
        }
    }

    /** Called once the page has been opened for [RequestStartUIState.pendingOpen]. */
    fun opened() {
        _uiState.update { it.copy(pendingOpen = null) }
    }

    /** Called once [message] has been shown. */
    fun messageShown(message: RequestStartMessage) {
        _uiState.update { if (it.message == message) it.copy(message = null) else it }
    }

    private fun fail(error: RequestsError) {
        val message = RequestStartMessage(startFailure(error, wordingSource.wording()), nextMessageId++)
        _uiState.update { it.copy(starting = null, message = message) }
    }
}
