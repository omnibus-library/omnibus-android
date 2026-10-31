package com.omnibus.omnibus.feature.reader

import androidx.lifecycle.ViewModel
import com.omnibus.omnibus.data.reader.ReaderSettings
import com.omnibus.omnibus.feature.reader.ui.ReaderEvent
import com.omnibus.omnibus.feature.reader.ui.ReaderLoadStatus
import com.omnibus.omnibus.feature.reader.ui.RelocateData
import com.omnibus.omnibus.feature.reader.ui.TocEntry
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * Owns the reader's state for one book. The screen reads [uiState], forwards the page's events to
 * [onPageEvent] and sends user intents back. Imperative page commands go out on [commands], so a
 * re-collected state can't re-navigate the page.
 */
@HiltViewModel(assistedFactory = ReaderViewModel.Factory::class)
class ReaderViewModel @AssistedInject constructor(
    @Assisted val bookUuid: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUIState())

    /** The reader's state. It changes only through [onPageEvent] and the intents below. */
    val uiState: StateFlow<ReaderUIState> = _uiState.asStateFlow()

    private val _commands = Channel<ReaderCommand>(Channel.BUFFERED)

    /** Commands for the screen to apply to the page. Each is delivered once, to one collector. */
    val commands: Flow<ReaderCommand> = _commands.receiveAsFlow()

    /** Creates a [ReaderViewModel] for the book with [bookUuid]. */
    @AssistedFactory
    interface Factory {
        fun create(bookUuid: String): ReaderViewModel
    }

    /**
     * Reduces one event from the page, or from the host about the load status. Synchronous, so the
     * state is up to date as soon as this returns.
     */
    fun onPageEvent(event: ReaderEvent) {
        when (event) {
            is ReaderEvent.Relocate -> _uiState.update { it.copy(location = event.data) }
            is ReaderEvent.Toc -> _uiState.update { it.copy(toc = event.entries) }
            ReaderEvent.ToggleChrome -> onChromeToggled()
            is ReaderEvent.LoadStatusChanged -> _uiState.update { it.copy(loadStatus = event.status) }
            // Status is ignored: the controller owns load status, and LoadStatusChanged reports it.
            is ReaderEvent.Status,
            // The rest belong to later work (#54, #55, #63), so they leave the state alone.
            ReaderEvent.HostReady,
            is ReaderEvent.Selection,
            ReaderEvent.SelectionCleared,
            is ReaderEvent.AnnotationTap,
            is ReaderEvent.ShareText,
            is ReaderEvent.ShareImage,
            is ReaderEvent.TurnRequest,
            -> Unit
        }
    }

    /** The reader's chrome was shown or hidden. */
    fun onChromeToggled() {
        _uiState.update { it.copy(chromeVisible = !it.chromeVisible) }
    }

    /** The reader asked for the contents sheet. */
    fun onContentsRequested() {
        _uiState.update { it.copy(sheet = ReaderSheet.Contents) }
    }

    /** The reader asked for the settings sheet. */
    fun onSettingsRequested() {
        _uiState.update { it.copy(sheet = ReaderSheet.Settings) }
    }

    /** The open sheet was dismissed. */
    fun onSheetDismissed() {
        _uiState.update { it.copy(sheet = null) }
    }

    /** A contents entry was chosen: closes the sheet and asks the page to show the entry. */
    fun onTocEntryChosen(entry: TocEntry) {
        _uiState.update { it.copy(sheet = null) }
        _commands.trySend(ReaderCommand.Display(entry.href))
    }

    /**
     * The reader is being left. This is the flush hook for saving the position (#50) and the
     * session (#51). It does nothing yet.
     */
    fun onReaderClosed() = Unit
}

/** What the reader shows. The defaults are the state before the first page event. */
data class ReaderUIState(
    val loadStatus: ReaderLoadStatus = ReaderLoadStatus.Loading,
    val location: RelocateData? = null,
    val toc: List<TocEntry> = emptyList(),
    val chromeVisible: Boolean = false,
    val settings: ReaderSettings = ReaderSettings(),
    val sheet: ReaderSheet? = null,
)

/** A sheet the reader can show over the page. */
enum class ReaderSheet { Contents, Settings }

/** An imperative request for the page, which the screen applies to the controller. */
sealed interface ReaderCommand {
    /** Shows the page at [target], an href from the contents or a CFI. */
    data class Display(val target: String) : ReaderCommand
}
