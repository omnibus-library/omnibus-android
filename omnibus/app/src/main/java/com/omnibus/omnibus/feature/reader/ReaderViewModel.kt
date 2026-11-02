package com.omnibus.omnibus.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.library.BookResult
import com.omnibus.omnibus.data.library.LibraryRepository
import com.omnibus.omnibus.data.progress.PositionPushThrottle
import com.omnibus.omnibus.data.progress.PositionSaveResult
import com.omnibus.omnibus.data.progress.ProgressRepository
import com.omnibus.omnibus.data.progress.ReadingPosition
import com.omnibus.omnibus.data.reader.ReaderAssetLoaderFactory
import com.omnibus.omnibus.data.reader.ReaderSettings
import com.omnibus.omnibus.di.ApplicationScope
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.feature.library.LibraryUIError
import com.omnibus.omnibus.feature.reader.ui.ReaderEvent
import com.omnibus.omnibus.feature.reader.ui.ReaderLoadStatus
import com.omnibus.omnibus.feature.reader.ui.RelocateData
import com.omnibus.omnibus.feature.reader.ui.TocEntry
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the reader's state for one book. The screen reads [uiState], forwards the page's events to
 * [onPageEvent] and sends user intents back. Imperative page commands go out on [commands], so a
 * re-collected state can't re-navigate the page.
 *
 * The book is read when the ViewModel is created, into [ReaderUIState.book].
 *
 * Each movement of the reader is saved locally, and pushed when the throttle allows. Closing or
 * stopping the reader pushes the latest one.
 *
 * @property assetLoaderFactory builds the WebView's asset loader for the screen. It isn't state.
 * @property persistScope runs the saves, so a flush outlives this ViewModel.
 */
@HiltViewModel(assistedFactory = ReaderViewModel.Factory::class)
class ReaderViewModel @AssistedInject constructor(
    @Assisted val bookUuid: String,
    private val libraryRepository: LibraryRepository,
    val assetLoaderFactory: ReaderAssetLoaderFactory,
    private val progressRepository: ProgressRepository,
    private val timeSource: TimeSource,
    @param:ApplicationScope private val persistScope: CoroutineScope,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUIState())

    /** The reader's state. It changes only through [onPageEvent], the book load and the intents below. */
    val uiState: StateFlow<ReaderUIState> = _uiState.asStateFlow()

    private val _commands = Channel<ReaderCommand>(Channel.BUFFERED)

    /** Commands for the screen to apply to the page. Each is delivered once, to one collector. */
    val commands: Flow<ReaderCommand> = _commands.receiveAsFlow()

    private var bookJob: Job? = null

    /** Spaces unforced pushes of this open's positions. */
    private val throttle = PositionPushThrottle()

    /** Whether the open's landing (its first relocate) has been seen. */
    private var sawFirstRelocate = false

    /** The last position recorded, whether or not it was pushed. */
    private var lastSaved: ReadingPosition? = null

    /** The latest position not yet pushed. Null when everything recorded has gone out. */
    private var pendingPush: ReadingPosition? = null

    init {
        loadBook()
    }

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
            is ReaderEvent.Relocate -> {
                _uiState.update { it.copy(location = event.data) }
                record(event.data)
            }
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

    /** The reader's chrome was shown or hidden. Hiding it also closes the menu. */
    fun onChromeToggled() {
        _uiState.update { it.withChromeToggled() }
    }

    /** The reader tapped the menu button. Does nothing unless the chrome is up and has rows. */
    fun onMenuOpened() {
        _uiState.update { it.withMenuOpened() }
    }

    /** The reader tapped outside the open menu, or went back with it open. */
    fun onMenuDismissed() {
        _uiState.update { it.withMenuDismissed() }
    }

    /** The reader chose a row in the menu, which closes the menu and opens that row's sheet. */
    fun onMenuRowChosen(row: ReaderMenuRow) {
        _uiState.update { it.withMenuRowChosen(row) }
    }

    /** The reader asked for the contents sheet. */
    fun onContentsRequested() {
        _uiState.update { it.withSheet(ReaderSheet.Contents) }
    }

    /** The reader asked for the settings sheet. */
    fun onSettingsRequested() {
        _uiState.update { it.withSheet(ReaderSheet.Settings) }
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
     * The reader is being left. Pushes the latest position if it hasn't gone out. The session (#51)
     * will hook in here too.
     */
    fun onReaderClosed() = flush()

    /** The reader stopped (the app went to the background). Pushes the latest position, as [onReaderClosed]. */
    fun onReaderStopped() = flush()

    /** Backstop for a dismissal that bypasses [onReaderClosed]. The view model's scope is already cancelled here. */
    override fun onCleared() = flush()

    /** Records a movement of the reader for saving. The landing, echoes and an unmoved spot are not movements. */
    private fun record(data: RelocateData) {
        val first = !sawFirstRelocate
        sawFirstRelocate = true
        val cfi = data.cfi?.takeIf { it.isNotBlank() } ?: return
        if (first || data.echo || cfi == lastSaved?.cfi) return
        persist(
            ReadingPosition(
                bookUuid = bookUuid,
                cfi = cfi,
                percent = data.pct.takeIf { data.totalPages > 0 },
                clientUpdatedAt = timeSource.nowMillis() / 1000,
            ),
        )
    }

    /** Saves [position] locally, and pushes it only when the throttle allows an unforced push. */
    private fun persist(position: ReadingPosition) {
        lastSaved = position
        val push = throttle.shouldPush(force = false, nowMillis = timeSource.nowMillis())
        pendingPush = position
        save(position, push)
    }

    /** Pushes the latest position that hasn't gone out, forced past the throttle. Does nothing when none is pending. */
    private fun flush() {
        val latest = pendingPush ?: return
        throttle.shouldPush(force = true, nowMillis = timeSource.nowMillis())
        save(latest, push = true)
    }

    /** Runs the save in [persistScope] so it finishes even after this view model is cleared. */
    private fun save(position: ReadingPosition, push: Boolean) {
        // Cleared optimistically, so a second flush while this one is in flight sends nothing.
        if (push) pendingPush = null
        persistScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val result = progressRepository.savePosition(position, push)
            // A failed or skipped push stays pending, unless something newer is already waiting.
            if (push && result != PositionSaveResult.Pushed && pendingPush == null) pendingPush = position
        }
    }

    /** Reads the book again after it failed to load. Does nothing in any other state. */
    fun onRetryBookRequested() {
        if (_uiState.value.book is ReaderBookState.Failed) loadBook()
    }

    /** Cancels any read in flight and collects the book, so a retry can't land after a newer one. */
    private fun loadBook() {
        bookJob?.cancel()
        _uiState.update { it.copy(book = ReaderBookState.Loading) }
        bookJob = viewModelScope.launch {
            libraryRepository.getBookFlow(bookUuid).collect { result ->
                _uiState.update { it.copy(book = result.toBookState()) }
            }
        }
    }

    private fun BookResult.toBookState(): ReaderBookState = when (this) {
        is BookResult.Success ->
            if (book.hasEpub) {
                ReaderBookState.Ready(title = book.displayTitle)
            } else {
                ReaderBookState.Unavailable(ReaderUnavailableReason.NoEpub)
            }
        BookResult.NotFound -> ReaderBookState.Unavailable(ReaderUnavailableReason.NotFound)
        BookResult.NoServer -> ReaderBookState.Failed(LibraryUIError.NoServer)
        is BookResult.Failure -> ReaderBookState.Failed(LibraryUIError.Failed(error))
    }
}

/** What the reader shows. The defaults are the state before the first page event. */
data class ReaderUIState(
    val book: ReaderBookState = ReaderBookState.Loading,
    val loadStatus: ReaderLoadStatus = ReaderLoadStatus.Loading,
    val location: RelocateData? = null,
    val toc: List<TocEntry> = emptyList(),
    val chromeVisible: Boolean = false,
    val settings: ReaderSettings = ReaderSettings(),
    val sheet: ReaderSheet? = null,
    /** Whether the chrome's menu is open. It only opens when the chrome is up and [menuRows] is non-empty. */
    val menuOpen: Boolean = false,
    /**
     * The rows for reader features that exist. #47 adds `Settings`, #48 adds `Contents`. Empty hides
     * the menu button.
     */
    val menuRows: List<ReaderMenuRow> = emptyList(),
)

/** Whether the book for the reader could be read. */
sealed interface ReaderBookState {

    /** The book is being read from the library. */
    data object Loading : ReaderBookState

    /** The book can be read. [title] is its display title. */
    data class Ready(val title: String) : ReaderBookState

    /** The book is in the library but can't be opened here, or isn't there at all. */
    data class Unavailable(val reason: ReaderUnavailableReason) : ReaderBookState

    /** The read failed. [error] is what the Library screen would show for it. */
    data class Failed(val error: LibraryUIError) : ReaderBookState
}

/** Why a book is [ReaderBookState.Unavailable]. */
enum class ReaderUnavailableReason {
    /** The server has no book with this uuid. */
    NotFound,

    /** The book has no EPUB, so the reader can't open it. */
    NoEpub,
}

/** A sheet the reader can show over the page. */
enum class ReaderSheet { Contents, Settings }

/**
 * A row in the chrome's menu, each of which opens a [ReaderSheet]. #57 adds `BookmarksAndHighlights`,
 * whose count comes from its own state.
 */
enum class ReaderMenuRow { Contents, Settings }

internal fun ReaderUIState.withChromeToggled(): ReaderUIState =
    if (chromeVisible) copy(chromeVisible = false, menuOpen = false) else copy(chromeVisible = true)

internal fun ReaderUIState.withMenuOpened(): ReaderUIState =
    if (chromeVisible && menuRows.isNotEmpty()) copy(menuOpen = true) else this

internal fun ReaderUIState.withMenuDismissed(): ReaderUIState = copy(menuOpen = false)

internal fun ReaderUIState.withSheet(sheet: ReaderSheet): ReaderUIState = copy(sheet = sheet, menuOpen = false)

internal fun ReaderUIState.withMenuRowChosen(row: ReaderMenuRow): ReaderUIState = withSheet(
    when (row) {
        ReaderMenuRow.Contents -> ReaderSheet.Contents
        ReaderMenuRow.Settings -> ReaderSheet.Settings
    },
)

/** An imperative request for the page, which the screen applies to the controller. */
sealed interface ReaderCommand {
    /** Shows the page at [target], an href from the contents or a CFI. */
    data class Display(val target: String) : ReaderCommand
}
