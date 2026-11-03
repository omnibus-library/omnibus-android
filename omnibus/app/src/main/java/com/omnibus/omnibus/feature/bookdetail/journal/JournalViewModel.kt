package com.omnibus.omnibus.feature.bookdetail.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.auth.AuthRepository
import com.omnibus.omnibus.data.journals.JournalEntry
import com.omnibus.omnibus.data.journals.JournalsRepository
import com.omnibus.omnibus.data.journals.JournalsState
import com.omnibus.omnibus.di.TimeSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the Journal list shows. [nowMillis] dates the bylines and is read when the state is built.
 */
data class JournalUIState(
    val content: JournalUIContent = JournalUIContent.Loading,
    val nowMillis: Long = 0L,
)

/** The list's content: loading, failed, or the loaded cards in server order. */
sealed interface JournalUIContent {
    /** Nothing loaded yet, or a fetch is in flight. */
    data object Loading : JournalUIContent

    /** The entries could not be read and no list was ever loaded. */
    data object Failed : JournalUIContent

    /** The loaded cards. The list is empty when the book has no entries. */
    data class Loaded(val cards: List<JournalCardModel>) : JournalUIContent
}

/**
 * One card: its entry's id, author, ownership, draft flag and date, and its preview runs. The spoilers in [preview] are
 * already masked.
 */
data class JournalCardModel(
    val id: Long,
    val authorName: String,
    val isOwn: Boolean,
    val isDraft: Boolean,
    val createdAtMillis: Long?,
    val preview: List<PreviewRun>,
)

/**
 * Owns the Journal list for one book. Entries come from [JournalsRepository]. Whether an entry is the viewer's own comes
 * from [AuthRepository], so it is re-derived when the viewer becomes known, without a refetch.
 *
 * @property bookUuid The book this list shows.
 */
@HiltViewModel(assistedFactory = JournalViewModel.Factory::class)
class JournalViewModel @AssistedInject constructor(
    @Assisted private val bookUuid: String,
    private val journalsRepository: JournalsRepository,
    authRepository: AuthRepository,
    private val timeSource: TimeSource,
) : ViewModel() {

    /** Creates a [JournalViewModel] for the book with [bookUuid]. */
    @AssistedFactory
    interface Factory {
        fun create(bookUuid: String): JournalViewModel
    }

    /** The viewer's id, or null while it is unknown. A failing session read counts as unknown. */
    private val viewerId: Flow<Long?> = authRepository.currentUser
        .map { it?.id }
        .catch { emit(null) }
        .distinctUntilChanged()

    /** The reload key of the last load, so re-entering composition with the same key does nothing. */
    private var lastLoadedKey: Int? = null

    /** The list's state. It is kept while something collects it. */
    val uiState: StateFlow<JournalUIState> = combine(
        journalsRepository.getJournalsFlow(bookUuid),
        viewerId,
    ) { state, viewer ->
        JournalUIState(
            content = when (state) {
                JournalsState.Loading -> JournalUIContent.Loading
                JournalsState.Failed -> JournalUIContent.Failed
                is JournalsState.Loaded -> JournalUIContent.Loaded(state.entries.map { it.toCardModel(viewer) })
            },
            nowMillis = timeSource.nowMillis(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalUIState())

    /** Refreshes for [reloadKey] once. A new key refreshes, and the same key again does nothing. */
    fun reload(reloadKey: Int) {
        if (reloadKey == lastLoadedKey) return
        lastLoadedKey = reloadKey
        retry()
    }

    /** Refreshes again, after a failure or when the reader asks. */
    fun retry() {
        viewModelScope.launch { journalsRepository.refresh(bookUuid) }
    }
}

/** This entry as a card for [viewerId]. Without a known viewer, every entry reads as another reader's. */
private fun JournalEntry.toCardModel(viewerId: Long?) = JournalCardModel(
    id = id,
    authorName = authorName,
    isOwn = viewerId != null && authorId == viewerId,
    isDraft = isDraft,
    createdAtMillis = createdAtMillis,
    preview = journalPreview(bodyMd),
)
