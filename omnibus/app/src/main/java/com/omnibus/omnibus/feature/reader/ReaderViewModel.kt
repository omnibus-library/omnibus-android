package com.omnibus.omnibus.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.bookmarks.Bookmark
import com.omnibus.omnibus.data.bookmarks.BookmarkDraft
import com.omnibus.omnibus.data.bookmarks.BookmarksRepository
import com.omnibus.omnibus.data.bookmarks.BookmarksState
import com.omnibus.omnibus.data.library.BookResult
import com.omnibus.omnibus.data.library.LibraryRepository
import com.omnibus.omnibus.data.progress.PositionPushThrottle
import com.omnibus.omnibus.data.progress.PositionSaveResult
import com.omnibus.omnibus.data.progress.ProgressRepository
import com.omnibus.omnibus.data.progress.PositionSync
import com.omnibus.omnibus.data.progress.ReadingPosition
import com.omnibus.omnibus.data.progress.ServerPositionResult
import com.omnibus.omnibus.data.reader.ReaderAssetLoaderFactory
import com.omnibus.omnibus.data.reader.ReaderSettings
import com.omnibus.omnibus.data.reader.local.ReaderSettingsStore
import com.omnibus.omnibus.di.ApplicationScope
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.feature.library.LibraryUIError
import com.omnibus.omnibus.feature.library.ui.coverIdentity
import com.omnibus.omnibus.feature.reader.ui.ReaderContentsTab
import com.omnibus.omnibus.feature.reader.ui.ReaderEvent
import com.omnibus.omnibus.feature.reader.ui.ReaderLoadStatus
import com.omnibus.omnibus.feature.reader.ui.RelocateData
import com.omnibus.omnibus.feature.reader.ui.TocEntry
import com.omnibus.omnibus.ui.components.CoverIdentity
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

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
 * The stored reader settings are read once, before the page boots, and then owned here: each
 * change is applied to the page and saved for every book.
 *
 * @property assetLoaderFactory builds the WebView's asset loader for the screen. It isn't state.
 * @property persistScope runs the saves, so a flush outlives this ViewModel.
 * @property startCfi An EPUB CFI to open at, such as a bookmark's, instead of the saved position. It skips the
 *   server read and the sync offer. Progress then saves as for any open, so landing on an earlier bookmark saves
 *   that place as progress, as iOS does.
 */
@HiltViewModel(assistedFactory = ReaderViewModel.Factory::class)
class ReaderViewModel @AssistedInject constructor(
    @Assisted("bookUuid") val bookUuid: String,
    private val libraryRepository: LibraryRepository,
    val assetLoaderFactory: ReaderAssetLoaderFactory,
    private val progressRepository: ProgressRepository,
    private val settingsStore: ReaderSettingsStore,
    private val bookmarksRepository: BookmarksRepository,
    private val timeSource: TimeSource,
    @param:ApplicationScope private val persistScope: CoroutineScope,
    @Assisted("startCfi") private val startCfi: String? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ReaderUIState(
            opening = ReaderOpening.Resolving,
            settingsLoaded = false,
            menuRows = listOf(ReaderMenuRow.Contents, ReaderMenuRow.Bookmarks, ReaderMenuRow.Settings),
            bookmarksStatus = ReaderBookmarksStatus.Loading,
        ),
    )

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

    /** Where the page booted. */
    private var openingStart: ReaderStart = ReaderStart.Beginning

    /** The percent of the record the open started from, or null. */
    private var openedPercent: Int? = null

    /** Whether the seek for a percent-only opening has been sent. It is sent once, when the page is first ready. */
    private var openingSeekSent = false

    /** Whether a sync offer was accepted or dismissed. Neither re-arms for this open. */
    private var offerSettled = false

    /** Holds back a percent-only open's writes until the reader moves. */
    private var gate = PersistGate(held = false)

    /** Keeps the stored percent from falling while reading forward from the opened position. */
    private var floor = PercentFloor(openedPercent = null)

    init {
        loadSettings()
        loadBook()
        resolveOpening()
        loadBookmarks()
    }

    /** Creates a [ReaderViewModel] for the book with [bookUuid]. */
    @AssistedFactory
    interface Factory {
        fun create(@Assisted("bookUuid") bookUuid: String, @Assisted("startCfi") startCfi: String?): ReaderViewModel
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
            is ReaderEvent.LoadStatusChanged -> {
                _uiState.update { it.copy(loadStatus = event.status) }
                if (event.status == ReaderLoadStatus.Ready) sendOpeningSeek()
            }
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

    /** The reader left context mode, by back or by tapping the page. Unlike a toggle, it never turns the chrome on. */
    fun onContextLeft() {
        _uiState.update { it.withContextLeft() }
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

    /** The reader asked for the contents sheet, on its Contents segment. */
    fun onContentsRequested() {
        _uiState.update { it.withSheet(ReaderSheet.Contents).copy(contentsTab = ReaderContentsTab.Contents) }
    }

    /** The reader asked for the settings sheet. */
    fun onSettingsRequested() {
        _uiState.update { it.withSheet(ReaderSheet.Settings) }
    }

    /** The reader changed a setting in the sheet. The page applies it live, and it is saved for every book. */
    fun onSettingsChanged(settings: ReaderSettings) {
        val next = settings.normalized()
        if (next == _uiState.value.settings) return
        _uiState.update { it.copy(settings = next) }
        // Application scope so the save outlives the reader; UNDISPATCHED so rapid taps reach DataStore in order.
        persistScope.launch(start = CoroutineStart.UNDISPATCHED) { settingsStore.save(next) }
    }

    /** Adds a bookmark at the current page, or removes every bookmark there. The menu stays open. */
    fun onBookmarkToggled() {
        val state = _uiState.value
        val onPage = state.pageBookmarks
        if (onPage.isNotEmpty()) {
            onPage.forEach { bookmark -> viewModelScope.launch { bookmarksRepository.removeBookmark(bookmark) } }
            return
        }
        val location = state.location ?: return
        val draft = BookmarkDraft.create(bookUuid, location.cfi, location.chapterTitle) ?: return
        viewModelScope.launch { bookmarksRepository.addBookmark(draft) }
    }

    /**
     * The reader chose a bookmark: closes the sheet, leaves context mode and asks the page to show its position. #58
     * will mark a return point here, before the jump.
     */
    fun onBookmarkChosen(bookmark: Bookmark) {
        _uiState.update { it.copy(sheet = null).withContextLeft() }
        _commands.trySend(ReaderCommand.Display(bookmark.position))
    }

    /** The reader asked to retry a bookmarks load that failed. */
    fun onBookmarksRetryRequested() {
        viewModelScope.launch { bookmarksRepository.refresh(bookUuid) }
    }

    /** The open sheet was dismissed. */
    fun onSheetDismissed() {
        _uiState.update { it.copy(sheet = null) }
    }

    /** A contents entry was chosen: closes the sheet, leaves context mode and asks the page to show the entry. */
    fun onTocEntryChosen(entry: TocEntry) {
        _uiState.update { it.copy(sheet = null).withContextLeft() }
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
        if (!gate.admit(data)) return
        val percent = floor.percentFor(data)
        val cfi = data.cfi?.takeIf { it.isNotBlank() } ?: return
        if (first || data.echo || cfi == lastSaved?.cfi) return
        persist(
            ReadingPosition(
                bookUuid = bookUuid,
                cfi = cfi,
                percent = percent,
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

    /** Sends the seek of a percent-only opening that boots at the cover, once the page is ready. */
    private fun sendOpeningSeek() {
        val start = openingStart as? ReaderStart.AtPercent ?: return
        if (openingSeekSent || start.bootCfi != null) return
        openingSeekSent = true
        gate.onSeekSent()
        _commands.trySend(ReaderCommand.Seek(start.fraction))
    }

    /** The reader chose the offered position. Moves to it once, and the offer is gone for this open. */
    fun onSyncOfferAccepted() {
        val offer = _uiState.value.syncOffer ?: return
        offerSettled = true
        _uiState.update { it.copy(syncOffer = null) }
        // The reader's own choice, so a held percent-only open may now be written.
        gate.release()
        when (val target = offer.target) {
            is ReaderStart.AtCfi -> _commands.trySend(ReaderCommand.Display(target.cfi))
            is ReaderStart.AtPercent -> _commands.trySend(ReaderCommand.Seek(target.fraction))
            ReaderStart.Beginning -> Unit
        }
    }

    /** The reader dismissed the offer. It does not come back for this open. */
    fun onSyncOfferDismissed() {
        if (_uiState.value.syncOffer == null) return
        offerSettled = true
        _uiState.update { it.copy(syncOffer = null) }
    }

    /** Reads the book again after it failed to load. Does nothing in any other state. */
    fun onRetryBookRequested() {
        if (_uiState.value.book is ReaderBookState.Failed) loadBook()
    }

    /**
     * Works out where the page boots. The local position and the server's are read at once, and the
     * server has [PositionSync.OPEN_DEADLINE] to answer. A newer answer in time is the opening
     * position. Otherwise the page opens at the local one, and a newer answer that arrives late is
     * offered (never applied). The book load doesn't wait for this, and this doesn't wait for the book.
     */
    private fun resolveOpening() = viewModelScope.launch {
        // An explicit start (a bookmark) wins: no server read, no sync offer. Progress then saves as for any open.
        val explicit = startCfi?.trim()?.takeIf { PositionSync.isEpubCfi(it) }
        if (explicit != null) {
            begin(ReaderStart.AtCfi(explicit), openedPercent = null)
            return@launch
        }
        // Started before the read, so an answer at exactly the deadline loses the tie and is offered (TC-65).
        val deadline = async { delay(PositionSync.OPEN_DEADLINE) }
        val remote = async { progressRepository.getServerPosition(bookUuid) }
        val local = progressRepository.getLocalPosition(bookUuid)
        val early: ServerPositionResult? = select {
            remote.onAwait { it }
            deadline.onAwait { null }
        }
        deadline.cancel()

        val adopted = (early as? ServerPositionResult.Found)?.position?.takeIf { PositionSync.isNewer(it, local) }
        val start = adopted?.let { readerStartOf(it.cfi, it.derivedCfi, it.percent) }
            ?: readerStartOf(local?.cfi, null, local?.percent)
        begin(start, openedPercent = (adopted?.percent ?: local?.percent)?.takeIf { it > 0 })

        if (adopted != null) {
            // An adopted position is kept on this device, with the server's own clock, and not pushed back.
            val saved = ReadingPosition(
                bookUuid = bookUuid,
                cfi = adopted.cfi?.trim()?.takeIf { PositionSync.isEpubCfi(it) },
                percent = adopted.percent,
                clientUpdatedAt = adopted.orderingClock,
            )
            persistScope.launch(start = CoroutineStart.UNDISPATCHED) { progressRepository.savePosition(saved, push = false) }
        }
        if (early != null) return@launch

        // Late: the read is not cancelled. A newer position that starts somewhere else becomes an offer.
        val record = (remote.await() as? ServerPositionResult.Found)?.position ?: return@launch
        if (!PositionSync.isNewer(record, local)) return@launch
        val target = readerStartOf(record.cfi, record.derivedCfi, record.percent)
        if (target == start || offerSettled) return@launch
        _uiState.update { it.copy(syncOffer = SyncOffer(target)) }
    }

    /** Records where the page boots, and that it is now open. */
    private fun begin(start: ReaderStart, openedPercent: Int?) {
        openingStart = start
        this.openedPercent = openedPercent
        gate = PersistGate(held = start is ReaderStart.AtPercent)
        floor = PercentFloor(openedPercent)
        _uiState.update { it.copy(opening = ReaderOpening.Resolved(start)) }
    }

    /**
     * Reads the stored settings once, before the page boots. Not collected again: the ViewModel owns
     * the settings from here, so a late store emission can't undo a newer tap.
     */
    private fun loadSettings() = viewModelScope.launch {
        val stored = settingsStore.settings.first()
        _uiState.update { it.copy(settings = stored, settingsLoaded = true) }
    }

    /** Collects the book's bookmarks, which re-reads the server on every open, and mirrors them into the state. */
    private fun loadBookmarks() = viewModelScope.launch {
        bookmarksRepository.getBookmarksFlow(bookUuid).collect { state ->
            _uiState.update { it.withBookmarks(state) }
        }
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
                ReaderBookState.Ready(title = book.displayTitle, cover = book.coverIdentity())
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
    /** Whether the reader is in context mode: the book is zoomed out and the top bar is up. False is focused. */
    val chromeVisible: Boolean = false,
    val settings: ReaderSettings = ReaderSettings(),
    val sheet: ReaderSheet? = null,
    /** Whether the chrome's menu is open. It only opens when the chrome is up and [menuRows] is non-empty. */
    val menuOpen: Boolean = false,
    /**
     * The rows for reader features that exist. The ViewModel offers `Contents`, `Bookmarks` and `Settings`. Empty
     * hides the menu button, which is the default for hand-built states.
     */
    val menuRows: List<ReaderMenuRow> = emptyList(),
    /**
     * False only while the ViewModel reads the stored settings. The page isn't booted until it is
     * true, so its first paint already has them. Defaults to true for previews and hand-built states.
     */
    val settingsLoaded: Boolean = true,
    /**
     * Where the page boots. The default is for previews and tests that build a state by hand; the
     * ViewModel starts at [ReaderOpening.Resolving].
     */
    val opening: ReaderOpening = ReaderOpening.Resolved(ReaderStart.Beginning),
    /** A newer position from another device, offered once the page is ready. Null when there is none to offer. */
    val syncOffer: SyncOffer? = null,
    /** The book's bookmarks in the repository's order, including adds the server has not answered yet. */
    val bookmarks: List<Bookmark> = emptyList(),
    /** Where [bookmarks] stands. The Bookmarks segment shows a loading or error view until it is Loaded. */
    val bookmarksStatus: ReaderBookmarksStatus = ReaderBookmarksStatus.Loaded,
    /** The segment the contents sheet opens on. */
    val contentsTab: ReaderContentsTab = ReaderContentsTab.Contents,
) {

    /** The number of bookmarks in the state, so an optimistic add counts at once and a failed one drops off. */
    val bookmarkCount: Int get() = bookmarks.size

    /** The bookmarks at the current page: a trimmed position equal to the trimmed location CFI. */
    internal val pageBookmarks: List<Bookmark>
        get() {
            val cfi = location?.cfi?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
            return bookmarks.filter { it.position.trim() == cfi }
        }

    /** Whether a bookmark sits at the current page. */
    val isPageBookmarked: Boolean get() = pageBookmarks.isNotEmpty()

    /** The menu's Contents percent. Null until the book has locations, when the page's percent is a wrong 0. */
    val menuPercent: Int? get() = location?.takeIf { it.totalPages > 0 }?.pct
}

/** Whether the book for the reader could be read. */
sealed interface ReaderBookState {

    /** The book is being read from the library. */
    data object Loading : ReaderBookState

    /**
     * The book can be read. [title] is its display title. [cover] draws the book in the contents
     * sheet's header.
     */
    data class Ready(
        val title: String,
        val cover: CoverIdentity = CoverIdentity(uuid = "", title = title, hasCover = false),
    ) : ReaderBookState

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

/** Where the book's bookmarks stand, as the Bookmarks segment shows them. */
enum class ReaderBookmarksStatus { Loading, Failed, Loaded }

/**
 * A row in the chrome's menu, each of which opens a [ReaderSheet]. #57 renames `Bookmarks` to
 * `BookmarksAndHighlights` and adds the highlight count.
 */
enum class ReaderMenuRow { Contents, Bookmarks, Settings }

/** What system back does next, in order: the sheet, the dropdown, context mode, then the reader itself. */
enum class ReaderBackAction { DismissSheet, DismissMenu, LeaveContext, LeaveReader }

internal fun ReaderUIState.backAction(): ReaderBackAction = when {
    sheet != null -> ReaderBackAction.DismissSheet
    menuOpen -> ReaderBackAction.DismissMenu
    chromeVisible -> ReaderBackAction.LeaveContext
    else -> ReaderBackAction.LeaveReader
}

/** Leaves context mode and closes the dropdown. Idempotent, unlike a toggle. */
internal fun ReaderUIState.withContextLeft(): ReaderUIState = copy(chromeVisible = false, menuOpen = false)

internal fun ReaderUIState.withChromeToggled(): ReaderUIState =
    if (chromeVisible) copy(chromeVisible = false, menuOpen = false) else copy(chromeVisible = true)

internal fun ReaderUIState.withMenuOpened(): ReaderUIState =
    if (chromeVisible && menuRows.isNotEmpty()) copy(menuOpen = true) else this

internal fun ReaderUIState.withMenuDismissed(): ReaderUIState = copy(menuOpen = false)

internal fun ReaderUIState.withSheet(sheet: ReaderSheet): ReaderUIState = copy(sheet = sheet, menuOpen = false)

internal fun ReaderUIState.withMenuRowChosen(row: ReaderMenuRow): ReaderUIState = when (row) {
    ReaderMenuRow.Contents -> withSheet(ReaderSheet.Contents).copy(contentsTab = ReaderContentsTab.Contents)
    ReaderMenuRow.Bookmarks -> withSheet(ReaderSheet.Contents).copy(contentsTab = ReaderContentsTab.Bookmarks)
    ReaderMenuRow.Settings -> withSheet(ReaderSheet.Settings)
}

/** Mirrors the repository's [state] into the bookmarks fields. A Loading or Failed state has no rows to show. */
internal fun ReaderUIState.withBookmarks(state: BookmarksState): ReaderUIState = when (state) {
    BookmarksState.Loading -> copy(bookmarks = emptyList(), bookmarksStatus = ReaderBookmarksStatus.Loading)
    BookmarksState.Failed -> copy(bookmarks = emptyList(), bookmarksStatus = ReaderBookmarksStatus.Failed)
    is BookmarksState.Loaded -> copy(bookmarks = state.bookmarks, bookmarksStatus = ReaderBookmarksStatus.Loaded)
}

/** An imperative request for the page, which the screen applies to the controller. */
sealed interface ReaderCommand {
    /** Shows the page at [target], an href from the contents or a CFI. */
    data class Display(val target: String) : ReaderCommand

    /** Jumps to a whole-book [fraction] (0.0 to 1.0), for a percent-only position. */
    data class Seek(val fraction: Double) : ReaderCommand
}
