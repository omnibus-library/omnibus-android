package com.omnibus.omnibus.feature.audiobook.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.audiobook.playback.AudiobookController
import com.omnibus.omnibus.data.bookmarks.BookmarksRepository
import com.omnibus.omnibus.data.bookmarks.BookmarksState
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.feature.bookdetail.more.bookmarks.BookmarkRowModel
import com.omnibus.omnibus.feature.bookdetail.more.bookmarks.BookmarksContent
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the player's bookmarks sheet shows. [nowMillis] dates the rows and is read when the state is built.
 */
data class PlayerBookmarksUIState(
    val content: BookmarksContent = BookmarksContent.Loading,
    val nowMillis: Long = 0L,
)

/**
 * Owns the player's bookmark intents for one book: the Bookmark cell's add, the sheet's rows, removal, and a listening
 * row's seek (P-BMK-1, P-BMK-2). It is scoped to the player's route entry, so the cell and the sheet share one instance.
 * Writes go through [BookmarksRepository], which keeps them on its own scope.
 *
 * @property bookUuid The book the player is showing.
 */
@HiltViewModel(assistedFactory = PlayerBookmarksViewModel.Factory::class)
class PlayerBookmarksViewModel @AssistedInject constructor(
    @Assisted private val bookUuid: String,
    private val controller: AudiobookController,
    private val bookmarksRepository: BookmarksRepository,
    private val timeSource: TimeSource,
) : ViewModel() {

    /** Whether the Bookmark cell adds a bookmark now: this book is loaded and its load has settled. */
    val canAddBookmark: StateFlow<Boolean> = controller.state
        .map { audioBookmarkDraft(bookUuid, it) != null }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            audioBookmarkDraft(bookUuid, controller.state.value) != null,
        )

    /**
     * The sheet's state. The flow wrapper defers the read until the sheet collects, so the player's open never reads the
     * bookmarks.
     */
    val sheetState: StateFlow<PlayerBookmarksUIState> = flow { emitAll(bookmarksRepository.getBookmarksFlow(bookUuid)) }
        .map { state ->
            PlayerBookmarksUIState(
                content = when (state) {
                    BookmarksState.Loading -> BookmarksContent.Loading
                    BookmarksState.Failed -> BookmarksContent.Failed
                    is BookmarksState.Loaded -> BookmarksContent.Loaded(playerBookmarkRows(bookUuid, state.bookmarks))
                },
                nowMillis = timeSource.nowMillis(),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerBookmarksUIState())

    /** Adds a bookmark at the current second. Does nothing unless this book is loaded and settled (P-BMK-1). */
    fun onAddBookmark() {
        val draft = audioBookmarkDraft(bookUuid, controller.state.value) ?: return
        viewModelScope.launch { bookmarksRepository.addBookmark(draft) }
    }

    /** Removes [row]'s bookmark. It leaves the sheet at once and comes back if the removal fails (P-BMK-2). */
    fun onBookmarkRemoved(row: BookmarkRowModel) {
        viewModelScope.launch { bookmarksRepository.removeBookmark(row.bookmark) }
    }

    /** Seeks to [seconds] and plays, when this book is the one loaded (P-BMK-2). */
    fun onListeningBookmarkChosen(seconds: Double) {
        val state = controller.state.value
        if (state.book?.bookUuid != bookUuid) return
        controller.seekTo(seconds)
        if (!state.isPlaying) controller.play()
    }

    /** Reads the bookmarks again after a failure. */
    fun onRetry() {
        viewModelScope.launch { bookmarksRepository.refresh(bookUuid) }
    }

    /** Creates a [PlayerBookmarksViewModel] for the book with [bookUuid]. */
    @AssistedFactory
    interface Factory {
        fun create(bookUuid: String): PlayerBookmarksViewModel
    }
}
