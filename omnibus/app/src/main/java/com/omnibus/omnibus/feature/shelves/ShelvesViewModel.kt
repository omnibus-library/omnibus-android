package com.omnibus.omnibus.feature.shelves

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.auth.AuthRepository
import com.omnibus.omnibus.data.library.LibraryRepository
import com.omnibus.omnibus.data.library.ShelfPreview
import com.omnibus.omnibus.data.library.ShelfPreviewsResult
import com.omnibus.omnibus.feature.library.LibraryScreenState
import com.omnibus.omnibus.feature.library.LibraryUIError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the Shelves screen's list. The screen reads one [ShelvesUIState] and sends intents back. Every load is tagged
 * so a superseded load cannot overwrite a newer one.
 */
@HiltViewModel
class ShelvesViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShelvesUIState(isInitialLoading = true))

    /** The screen's state. Shows the loading state until the first result arrives. */
    val uiState: StateFlow<ShelvesUIState> = _uiState.asStateFlow()

    /** Bumped by every load, so results from an older load are ignored. */
    private var loadToken = 0
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.currentUser.collect { user -> _uiState.update { it.copy(viewerId = user?.id) } }
        }
    }

    /** Loads the shelves. Called each time the screen is shown. */
    fun screenShown() = load(refreshing = false)

    /** Reloads the shelves for pull to refresh. */
    fun refresh() = load(refreshing = true)

    /** Reloads the shelves after the full-screen error. */
    fun retry() = load(refreshing = false)

    /** Clears the one-shot [ShelvesUIState.transientError] once the screen has shown it. */
    fun transientErrorShown() {
        _uiState.update { it.copy(transientError = null) }
    }

    private fun load(refreshing: Boolean) {
        val token = ++loadToken
        loadJob?.cancel()
        _uiState.update {
            if (refreshing) {
                it.copy(isRefreshing = true)
            } else {
                it.copy(isInitialLoading = true, error = null)
            }
        }
        // Every emission is applied, since a cached list can arrive before the network one.
        loadJob = viewModelScope.launch {
            libraryRepository.getShelfPreviewsFlow().collect { result ->
                if (token != loadToken) return@collect
                _uiState.update { it.applyRead(result) }
            }
            if (token == loadToken) {
                _uiState.update { it.copy(isInitialLoading = false, isRefreshing = false) }
            }
        }
    }

    private fun ShelvesUIState.applyRead(result: ShelfPreviewsResult): ShelvesUIState = when (result) {
        is ShelfPreviewsResult.Success -> copy(previews = result.previews, error = null)
        is ShelfPreviewsResult.NoServer -> withFailure(LibraryUIError.NoServer)
        is ShelfPreviewsResult.Failure -> withFailure(LibraryUIError.Failed(result.error))
    }

    /** A failure with no shelves on screen is the full-screen error. With shelves held, it is a snackbar. */
    private fun ShelvesUIState.withFailure(uiError: LibraryUIError): ShelvesUIState =
        if (previews.isEmpty()) copy(error = uiError) else copy(transientError = uiError)
}

/**
 * What the Shelves screen shows.
 *
 * @property previews Every shelf the user can see, in the server's order, as the last successful read gave them.
 * @property viewerId The signed-in user's id, or null while it is unknown or signed out. A change regroups the shelves
 * without a reload.
 * @property isInitialLoading Whether a load that is not a refresh has not yet finished.
 * @property isRefreshing Whether a pull-to-refresh load has not yet finished.
 * @property error A full-screen failure. Only shown when there are no [previews], otherwise it is [transientError].
 * @property transientError A one-shot failure shown as a snackbar, cleared by [ShelvesViewModel.transientErrorShown].
 */
data class ShelvesUIState(
    val previews: List<ShelfPreview> = emptyList(),
    val viewerId: Long? = null,
    val isInitialLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: LibraryUIError? = null,
    val transientError: LibraryUIError? = null,
) {

    /** [previews] grouped by owner, the viewer's own group first. */
    val groups: List<ShelfOwnerGroup>
        get() = shelfOwnerGroups(previews, viewerId)

    /** The header's counts. */
    val census: ShelvesCensus
        get() = shelvesCensus(previews, viewerId)

    /** Whether the private-shelves notice shows. */
    val showsNotice: Boolean
        get() = showsPrivateShelvesNotice(previews, viewerId)

    /**
     * The state to show. Loading beats Error, Error beats Empty, and Content is the fallback once shelves exist.
     */
    val screen: LibraryScreenState
        get() = when {
            isInitialLoading && previews.isEmpty() -> LibraryScreenState.Loading
            error != null && previews.isEmpty() -> LibraryScreenState.Error(error)
            previews.isEmpty() -> LibraryScreenState.Empty
            else -> LibraryScreenState.Content
        }
}
