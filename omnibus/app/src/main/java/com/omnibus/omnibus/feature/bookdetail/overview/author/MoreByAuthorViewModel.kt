package com.omnibus.omnibus.feature.bookdetail.overview.author

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.authors.AuthorResult
import com.omnibus.omnibus.data.authors.AuthorsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the shelf shows. [books] is empty while loading, after a failed read that has nothing to fall back on, and for an author with no other books. */
data class MoreByAuthorUIState(val books: List<AuthorShelfBook> = emptyList())

/**
 * Owns the More by this author shelf for one book. A failed read leaves whatever the shelf already shows, so a flaky
 * refresh never empties it.
 *
 * @property authorId The author whose books to read.
 * @property bookId The book the page is for, which the shelf leaves out.
 */
@HiltViewModel(assistedFactory = MoreByAuthorViewModel.Factory::class)
class MoreByAuthorViewModel @AssistedInject constructor(
    @Assisted("authorId") private val authorId: Long,
    @Assisted("bookId") private val bookId: Long,
    private val authorsRepository: AuthorsRepository,
) : ViewModel() {

    /** Creates a [MoreByAuthorViewModel] for the book [bookId] by the author [authorId]. */
    @AssistedFactory
    interface Factory {
        fun create(@Assisted("authorId") authorId: Long, @Assisted("bookId") bookId: Long): MoreByAuthorViewModel
    }

    private val _uiState = MutableStateFlow(MoreByAuthorUIState())

    /** The shelf's state. */
    val uiState: StateFlow<MoreByAuthorUIState> = _uiState.asStateFlow()

    private var lastLoadedKey: Int? = null
    private var loadJob: Job? = null

    /** Reads the author's books once for each new [reloadKey]. A read still in flight is superseded. */
    fun reload(reloadKey: Int) {
        if (reloadKey == lastLoadedKey) return
        lastLoadedKey = reloadKey
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            when (val result = authorsRepository.getAuthor(authorId)) {
                is AuthorResult.Success ->
                    _uiState.value = MoreByAuthorUIState(moreByAuthor(result.author.books, bookId))
                else -> Unit
            }
        }
    }
}
