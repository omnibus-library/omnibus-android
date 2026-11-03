package com.omnibus.omnibus.feature.bookdetail.more.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.bookmarks.Bookmark
import com.omnibus.omnibus.data.bookmarks.BookmarksRepository
import com.omnibus.omnibus.data.bookmarks.BookmarksState
import com.omnibus.omnibus.data.bookprogress.BookChaptersResult
import com.omnibus.omnibus.data.bookprogress.BookProgressRepository
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the Bookmarks sheet shows. [nowMillis] dates the rows and is read when the state is built.
 *
 * @property sheetOpen Whether the sheet is open. The bookmarks are read only while it is.
 */
data class BookmarksUIState(
    val sheetOpen: Boolean = false,
    val content: BookmarksContent = BookmarksContent.Loading,
    val nowMillis: Long = 0L,
)

/** The sheet's content: loading, failed, or the loaded rows in repository order. */
sealed interface BookmarksContent {
    /** Nothing loaded yet, or a fetch is in flight. */
    data object Loading : BookmarksContent

    /** The bookmarks could not be read and no list was ever loaded. */
    data object Failed : BookmarksContent

    /** The loaded rows. The list is empty when the book has no bookmarks. */
    data class Loaded(val rows: List<BookmarkRowModel>) : BookmarksContent
}

/**
 * One bookmark as the sheet shows it.
 *
 * @property key A unique key for the row, from the bookmark's id, else its index.
 * @property listeningSeconds The audio time, or null for a text bookmark.
 * @property createdAtMillis When the bookmark was made, or null when the server gave no date.
 * @property destination Where a tap on the row leads.
 */
data class BookmarkRowModel(
    val key: String,
    val bookmark: Bookmark,
    val listeningSeconds: Double?,
    val placement: BookmarkPlacement,
    val createdAtMillis: Long?,
    val destination: BookDetailDestination,
) {
    /** Whether the row is an audio bookmark. */
    val isAudio: Boolean get() = listeningSeconds != null
}

/** Maps [bookmarks] to rows in their order. Keys are unique, from the id or else the index. */
internal fun bookmarkRows(
    bookUuid: String,
    bookmarks: List<Bookmark>,
    chapters: BookChaptersResult.Success?,
): List<BookmarkRowModel> {
    val seen = HashSet<String>()
    return bookmarks.mapIndexed { index, bookmark ->
        BookmarkRowModel(
            key = bookmark.key?.takeIf { seen.add(it) } ?: "index:$index",
            bookmark = bookmark,
            listeningSeconds = listeningSeconds(bookmark.position),
            placement = bookmarkPlacement(bookmark, chapters),
            createdAtMillis = bookmark.createdAtSeconds?.takeIf { it > 0 }?.times(1_000L),
            destination = bookmarkDestination(bookUuid, bookmark),
        )
    }
}

/**
 * Owns the Bookmarks sheet for one book. The bookmarks come from [BookmarksRepository], and each open re-reads them.
 * The chapters come from [BookProgressRepository] and place the rows; they are read once after a success, and a failed
 * read is tried again on the next open or by [retry]. The VM never navigates: a row's destination is computed here, and
 * the sheet sends it on.
 *
 * @property bookUuid The book this sheet shows.
 */
@HiltViewModel(assistedFactory = BookmarksViewModel.Factory::class)
class BookmarksViewModel @AssistedInject constructor(
    @Assisted private val bookUuid: String,
    private val bookmarksRepository: BookmarksRepository,
    private val progressRepository: BookProgressRepository,
    private val timeSource: TimeSource,
) : ViewModel() {

    private val sheetOpen = MutableStateFlow(false)
    private val chapters = MutableStateFlow<BookChaptersResult.Success?>(null)
    private var chaptersJob: Job? = null

    /** The sheet's state. The bookmarks are collected only while the sheet is open, so a menu open never fetches. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<BookmarksUIState> = combine(
        sheetOpen.flatMapLatest { open ->
            if (open) bookmarksRepository.getBookmarksFlow(bookUuid) else flowOf<BookmarksState?>(null)
        },
        chapters,
        sheetOpen,
    ) { state, loaded, open ->
        BookmarksUIState(
            sheetOpen = open,
            content = when (state) {
                null, BookmarksState.Loading -> BookmarksContent.Loading
                BookmarksState.Failed -> BookmarksContent.Failed
                is BookmarksState.Loaded -> BookmarksContent.Loaded(bookmarkRows(bookUuid, state.bookmarks, loaded))
            },
            nowMillis = timeSource.nowMillis(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookmarksUIState())

    /** Opens the sheet, which re-reads the bookmarks, and loads the chapters if they are missing. */
    fun openSheet() {
        sheetOpen.value = true
        loadChaptersIfMissing()
    }

    /** Closes the sheet and stops collecting the bookmarks. */
    fun closeSheet() {
        sheetOpen.value = false
    }

    /** Reads the bookmarks again after a failure, and loads the chapters if they are missing. */
    fun retry() {
        viewModelScope.launch { bookmarksRepository.refresh(bookUuid) }
        loadChaptersIfMissing()
    }

    private fun loadChaptersIfMissing() {
        if (chapters.value != null || chaptersJob?.isActive == true) return
        chaptersJob = viewModelScope.launch {
            (progressRepository.getChapters(bookUuid) as? BookChaptersResult.Success)?.let { chapters.value = it }
        }
    }

    /** Creates a [BookmarksViewModel] for the book with [bookUuid]. */
    @AssistedFactory
    interface Factory {
        fun create(bookUuid: String): BookmarksViewModel
    }
}
