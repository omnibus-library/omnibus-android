package com.omnibus.omnibus.feature.bookdetail.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.network.ConnectivityMonitor
import com.omnibus.omnibus.data.wishlist.WishlistRemovalResult
import com.omnibus.omnibus.data.wishlist.WishlistRepository
import com.omnibus.omnibus.data.wishlist.WishlistState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The More menu's "Remove from wishlist" state for one book: the shared wishlist state, whether the device is online,
 * whether the confirmation is open, and the outcome of the last removal for the page to report once.
 *
 * @property isOnline Whether the device is online. Offline, the menu row is disabled.
 * @property removing True while a removal request is in flight.
 * @property outcome The latest removal result. Cleared by [WishlistRemovalViewModel.outcomeShown].
 */
data class WishlistRemovalUIState(
    val state: WishlistState = WishlistState.Unknown,
    val isOnline: Boolean = true,
    val confirmOpen: Boolean = false,
    val removing: Boolean = false,
    val outcome: RemovalOutcome? = null,
)

/**
 * A finished removal, reported once. [eventId] increases with each removal, so the page reports each one once.
 */
sealed interface RemovalOutcome {
    val eventId: Long

    /** The entry was removed. [bookDeleted] is true when the server also deleted the book. */
    data class Removed(override val eventId: Long, val bookDeleted: Boolean) : RemovalOutcome

    /** The removal failed and the wishlist state is unchanged. */
    data class Failed(override val eventId: Long) : RemovalOutcome
}

/**
 * Owns the "Remove from wishlist" confirmation for one book. The confirmation state lives here, so it survives a
 * configuration change. The removal runs in [viewModelScope] and the repository runs the request in its own scope, so
 * closing the page does not cancel it.
 *
 * @property bookUuid The book the entry is for.
 */
@HiltViewModel(assistedFactory = WishlistRemovalViewModel.Factory::class)
class WishlistRemovalViewModel @AssistedInject constructor(
    @Assisted private val bookUuid: String,
    private val repository: WishlistRepository,
    connectivityMonitor: ConnectivityMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WishlistRemovalUIState())

    /** The menu and dialog state. It changes only through the intents below and the reads and writes they start. */
    val uiState: StateFlow<WishlistRemovalUIState> = _uiState.asStateFlow()

    private var lastReloadKey: Int? = null
    private var nextEventId = 1L

    init {
        viewModelScope.launch {
            repository.getWishlistFlow(bookUuid).collect { state -> _uiState.update { it.copy(state = state) } }
        }
        viewModelScope.launch {
            connectivityMonitor.isOnline.collect { online -> _uiState.update { it.copy(isOnline = online) } }
        }
    }

    /** Opens the confirmation, but only when the menu row is enabled. */
    fun openConfirm() {
        val current = _uiState.value
        if (removeRow(current.state, current.isOnline, current.removing) != RemoveRow.Enabled) return
        _uiState.update { it.copy(confirmOpen = true) }
    }

    /** Closes the confirmation without a request. */
    fun dismissConfirm() {
        _uiState.update { it.copy(confirmOpen = false) }
    }

    /** Closes the confirmation and removes the book from the wishlist. Ignored while a removal is in flight. */
    fun confirmRemoval() {
        if (_uiState.value.removing) return
        _uiState.update { it.copy(confirmOpen = false, removing = true) }
        viewModelScope.launch {
            val result = repository.removeFromWishlist(bookUuid)
            val id = nextEventId++
            _uiState.update {
                it.copy(
                    removing = false,
                    outcome = when (result) {
                        is WishlistRemovalResult.Removed -> RemovalOutcome.Removed(id, result.bookDeleted)
                        is WishlistRemovalResult.Failed -> RemovalOutcome.Failed(id)
                    },
                )
            }
        }
    }

    /** Clears [WishlistRemovalUIState.outcome] if it is the outcome [eventId]. A newer outcome stays. */
    fun outcomeShown(eventId: Long) {
        _uiState.update { if (it.outcome?.eventId == eventId) it.copy(outcome = null) else it }
    }

    /**
     * Refetches the wishlist state when the page reloads. The first key seen is only recorded, since the flow fetches
     * on its own.
     */
    fun pageReloaded(reloadKey: Int) {
        val previous = lastReloadKey
        lastReloadKey = reloadKey
        if (previous != null && previous != reloadKey) {
            viewModelScope.launch { repository.refresh(bookUuid) }
        }
    }

    /** Creates a [WishlistRemovalViewModel] for the book with [bookUuid]. */
    @AssistedFactory
    interface Factory {
        fun create(bookUuid: String): WishlistRemovalViewModel
    }
}
