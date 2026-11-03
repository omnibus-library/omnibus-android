package com.omnibus.omnibus.feature.bookdetail.highlights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.bookprogress.BookChaptersResult
import com.omnibus.omnibus.data.bookprogress.BookProgressRepository
import com.omnibus.omnibus.data.highlights.Highlight
import com.omnibus.omnibus.data.highlights.HighlightsRepository
import com.omnibus.omnibus.data.highlights.HighlightsState
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.feature.bookdetail.progress.BookChapter
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the Highlights list shows. [nowMillis] dates the footers and is read when the state is built.
 */
data class HighlightsUIState(
    val content: HighlightsUIContent = HighlightsUIContent.Loading,
    val nowMillis: Long = 0L,
)

/** The list's content: loading, failed, or the loaded rows in server order. */
sealed interface HighlightsUIContent {
    /** Nothing loaded yet, or a fetch is in flight. */
    data object Loading : HighlightsUIContent

    /** The highlights could not be read and no list was ever loaded. */
    data object Failed : HighlightsUIContent

    /** The loaded rows. The list is empty when the book has no highlights. */
    data class Loaded(val rows: List<HighlightRowModel>) : HighlightsUIContent
}

/**
 * Owns the Highlights list for one book. Highlights come from [HighlightsRepository], and chapter numbers from the
 * book's chapters, which are read once after a success and are never required: without them the footer shows the date.
 *
 * @property bookUuid The book this list shows.
 */
@HiltViewModel(assistedFactory = HighlightsViewModel.Factory::class)
class HighlightsViewModel @AssistedInject constructor(
    @Assisted private val bookUuid: String,
    private val highlightsRepository: HighlightsRepository,
    private val progressRepository: BookProgressRepository,
    private val timeSource: TimeSource,
) : ViewModel() {

    /** Creates a [HighlightsViewModel] for the book with [bookUuid]. */
    @AssistedFactory
    interface Factory {
        fun create(bookUuid: String): HighlightsViewModel
    }

    private val chapters = MutableStateFlow<List<BookChapter>?>(null)

    /** The reload key of the last load, so re-entering composition with the same key does nothing. */
    private var lastLoadedKey: Int? = null
    private var chaptersJob: Job? = null

    /** The list's state. It is kept while something collects it. */
    val uiState: StateFlow<HighlightsUIState> = combine(
        highlightsRepository.getHighlightsFlow(bookUuid),
        chapters,
    ) { state, loadedChapters ->
        HighlightsUIState(
            content = when (state) {
                HighlightsState.Loading -> HighlightsUIContent.Loading
                HighlightsState.Failed -> HighlightsUIContent.Failed
                is HighlightsState.Loaded -> HighlightsUIContent.Loaded(
                    state.highlights.map { it.toRowModel(loadedChapters.orEmpty()) },
                )
            },
            nowMillis = timeSource.nowMillis(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HighlightsUIState())

    /** Refreshes for [reloadKey] once. A new key refreshes, and loads the chapters if they have not loaded yet. */
    fun reload(reloadKey: Int) {
        if (reloadKey == lastLoadedKey) return
        lastLoadedKey = reloadKey
        refreshAndLoadChapters()
    }

    /** Refreshes again after a failure, and loads the chapters if they have not loaded yet. */
    fun retry() = refreshAndLoadChapters()

    private fun refreshAndLoadChapters() {
        viewModelScope.launch { highlightsRepository.refresh(bookUuid) }
        if (chapters.value != null || chaptersJob?.isActive == true) return
        chaptersJob = viewModelScope.launch {
            (progressRepository.getChapters(bookUuid) as? BookChaptersResult.Success)?.let {
                chapters.value = it.ebookChapters
            }
        }
    }
}

/** This highlight as a row. Blank text and note read as absent, and the chapter is found from [chapters]. */
private fun Highlight.toRowModel(chapters: List<BookChapter>) = HighlightRowModel(
    id = id,
    text = text?.takeIf { it.isNotBlank() },
    note = note?.takeIf { it.isNotBlank() },
    color = color,
    chapterNumber = highlightChapterNumber(this, chapters),
    createdAtMillis = createdAtMillis,
)
