package com.omnibus.omnibus.feature.audiobook.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.audiobook.playback.AudiobookController
import com.omnibus.omnibus.data.audiobook.playback.PlaybackError
import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerState
import com.omnibus.omnibus.data.audiobook.playback.currentChapter
import com.omnibus.omnibus.data.audiobook.playback.hasChapters
import com.omnibus.omnibus.data.audiobook.playback.nextChapterTarget
import com.omnibus.omnibus.data.audiobook.playback.previousChapterTarget
import com.omnibus.omnibus.data.audiobook.playback.spanLengthSeconds
import com.omnibus.omnibus.data.audiobook.playback.spanPositionSeconds
import com.omnibus.omnibus.data.audiobook.playback.spanStartSeconds
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.BookAvailability
import com.omnibus.omnibus.data.library.BookResult
import com.omnibus.omnibus.data.library.LibraryRepository
import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.feature.library.ui.coverIdentity
import com.omnibus.omnibus.ui.components.CoverIdentity
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Whether the player may show this book. Starts at [Checking]; [Offline] shows the dialog instead of the controls. */
sealed interface PlayerGate {
    /** The gate has not been decided yet. */
    data object Checking : PlayerGate

    /** The book can play, and it has been asked to load. */
    data object Open : PlayerGate

    /** The book is not downloaded and the device is offline, so nothing was loaded. */
    data object Offline : PlayerGate
}

/**
 * What the player screen shows. Plain values only, so the content composable and its tests build it by hand.
 *
 * @property leave Set once when the controller drops the book the player showed. The screen closes itself on it.
 * @property title The controller's title once it is known, else the library's, else empty.
 * @property isPreparing True until the controller holds this book and its load has settled.
 * @property isBuffering True while playback waits for data, and not while preparing.
 * @property spanStartSeconds The current chapter's start in book time, or 0 without chapters.
 * @property spanLengthSeconds The current chapter's length, or the book's without chapters.
 * @property spanPositionSeconds The position within the span, in book time.
 * @property durationSeconds The whole book's length.
 * @property canPreviousChapter Whether previous chapter has somewhere to go.
 * @property canNextChapter Whether next chapter has somewhere to go.
 */
data class PlayerUIState(
    val gate: PlayerGate = PlayerGate.Checking,
    val leave: Boolean = false,
    val cover: CoverIdentity = CoverIdentity(uuid = "", title = "", hasCover = false),
    val title: String = "",
    val author: String? = null,
    val hasEbook: Boolean = false,
    val chapterTitle: String? = null,
    val hasChapters: Boolean = false,
    val spanStartSeconds: Double = 0.0,
    val spanLengthSeconds: Double = 0.0,
    val spanPositionSeconds: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val rate: Double = 1.0,
    val canPreviousChapter: Boolean = false,
    val canNextChapter: Boolean = false,
    val isPlaying: Boolean = false,
    val isPreparing: Boolean = true,
    val isBuffering: Boolean = false,
    val error: PlaybackError? = null,
    val sleepTimer: SleepTimerState? = null,
)

/**
 * Maps the controller's [playback] state to what the player shows for [bookUuid]. While the controller holds another
 * book or none, the playback fields stay at their defaults and the player is preparing. The title and author come from
 * [libraryBook] until the controller knows them.
 */
internal fun playerUIState(
    playback: PlaybackState,
    bookUuid: String,
    libraryBook: Book?,
    gate: PlayerGate,
    leave: Boolean,
): PlayerUIState {
    val book = playback.book?.takeIf { it.bookUuid == bookUuid }
    val title = book?.title?.takeIf { it.isNotBlank() } ?: libraryBook?.displayTitle.orEmpty()
    val author = if (book != null && book.title.isNotBlank()) {
        book.author
    } else {
        libraryBook?.creators?.firstOrNull()?.name
    }
    val cover = libraryBook?.coverIdentity()
        ?: CoverIdentity(uuid = bookUuid, title = title, creator = author, hasCover = true)
    if (book == null) {
        return PlayerUIState(
            gate = gate,
            leave = leave,
            cover = cover,
            title = title,
            author = author,
            isPreparing = true,
        )
    }
    return PlayerUIState(
        gate = gate,
        leave = leave,
        cover = cover,
        title = title,
        author = author,
        hasEbook = book.hasEbook,
        chapterTitle = playback.currentChapter?.title,
        hasChapters = playback.hasChapters,
        spanStartSeconds = playback.spanStartSeconds,
        spanLengthSeconds = playback.spanLengthSeconds,
        spanPositionSeconds = playback.spanPositionSeconds,
        durationSeconds = playback.durationSeconds,
        rate = playback.rate,
        canPreviousChapter = previousChapterTarget(playback.chapters, playback.positionSeconds) != null,
        canNextChapter = nextChapterTarget(playback.chapters, playback.positionSeconds) != null,
        isPlaying = playback.isPlaying,
        isPreparing = playback.isLoading,
        isBuffering = playback.isBuffering && !playback.isLoading,
        error = playback.error,
        sleepTimer = playback.sleepTimer,
    )
}

/**
 * Owns the player's state for one book. The screen reads [uiState] and sends the user's intents back to the
 * controller. The gate and the load run once per ViewModel, so a rotation doesn't reload.
 *
 * @property bookUuid The book the player opens.
 */
@HiltViewModel(assistedFactory = PlayerViewModel.Factory::class)
class PlayerViewModel @AssistedInject constructor(
    @Assisted("bookUuid") val bookUuid: String,
    @Assisted("bookFileId") private val bookFileId: Long?,
    @Assisted("positionSeconds") private val positionSeconds: Double?,
    private val controller: AudiobookController,
    private val bookAvailability: BookAvailability,
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUIState())

    /** The player's state, recomputed on every controller change. */
    val uiState: StateFlow<PlayerUIState> = _uiState.asStateFlow()

    private var gate: PlayerGate = PlayerGate.Checking
    private var libraryBook: Book? = null
    private var seenOurBook = false
    private var leave = false

    init {
        // Watches the controller. Once it has shown this book, a later state without it means the book was unloaded.
        viewModelScope.launch {
            controller.state.collect { state ->
                if (state.book?.bookUuid == bookUuid) {
                    seenOurBook = true
                } else if (seenOurBook) {
                    leave = true
                }
                publish()
            }
        }

        viewModelScope.launch {
            val result = try {
                libraryRepository.getBookFlow(bookUuid).first()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            val book = (result as? BookResult.Success)?.book ?: return@launch
            libraryBook = book
            publish()
        }

        viewModelScope.launch {
            // Only a book the controller doesn't already hold needs the offline check (P-SCR-2 edge case).
            val holdsBook = controller.state.value.book?.bookUuid == bookUuid
            if (!holdsBook && !bookAvailability.canOpen(bookUuid, ProgressFormat.Listening)) {
                gate = PlayerGate.Offline
                publish()
                return@launch
            }
            gate = PlayerGate.Open
            publish()
            controller.load(bookUuid, bookFileId, positionSeconds, autoplay = true)
        }
    }

    /** Plays when paused, pauses when playing. */
    fun onPlayPauseToggled() {
        controller.togglePlayPause()
    }

    /** Skips back 15 seconds. */
    fun onSkipBack() {
        controller.skipBack()
    }

    /** Skips forward 30 seconds. */
    fun onSkipForward() {
        controller.skipForward()
    }

    /** Goes to the previous chapter, or restarts the current one. */
    fun onPreviousChapter() {
        controller.previousChapter()
    }

    /** Goes to the next chapter. */
    fun onNextChapter() {
        controller.nextChapter()
    }

    /** Seeks to [bookSeconds] in book time. */
    fun onSeek(bookSeconds: Double) {
        controller.seekTo(bookSeconds)
    }

    /** Tries playback again after an error. */
    fun onRetry() {
        controller.play()
    }

    private fun publish() {
        _uiState.update { playerUIState(controller.state.value, bookUuid, libraryBook, gate, leave) }
    }

    /** Creates a [PlayerViewModel] for one open of the player. */
    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("bookUuid") bookUuid: String,
            @Assisted("bookFileId") bookFileId: Long?,
            @Assisted("positionSeconds") positionSeconds: Double?,
        ): PlayerViewModel
    }
}
