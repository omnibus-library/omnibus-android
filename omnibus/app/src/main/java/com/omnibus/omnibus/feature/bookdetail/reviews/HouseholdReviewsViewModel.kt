package com.omnibus.omnibus.feature.bookdetail.reviews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.readstatus.ReadStatusRepository
import com.omnibus.omnibus.data.reviews.ReviewsRepository
import com.omnibus.omnibus.di.TimeSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the stage rating and the Reviews tab show. [nowMillis] is the clock when the state was mapped, for formatting
 * the entries' dates.
 */
internal data class HouseholdReviewsUIState(
    val stage: StageRatingModel = StageRatingModel.Checking,
    val tab: ReviewsTabModel = ReviewsTabModel.Hidden,
    val nowMillis: Long = 0L,
)

/**
 * Owns the household reviews for one book, shared by the stage rating and the Reviews tab. Both sections get this
 * same instance from `householdReviewsViewModel`, so there is one reload counter and one set of revealed spoilers.
 * The repository holds the shared state. This ViewModel maps it, with the reader's status, to what the two show.
 *
 * @property bookUuid The book the reviews are for.
 */
@HiltViewModel(assistedFactory = HouseholdReviewsViewModel.Factory::class)
internal class HouseholdReviewsViewModel @AssistedInject constructor(
    @Assisted private val bookUuid: String,
    private val reviewsRepository: ReviewsRepository,
    readStatusRepository: ReadStatusRepository,
    private val timeSource: TimeSource,
) : ViewModel() {

    /** Creates a [HouseholdReviewsViewModel] for the book with [bookUuid]. */
    @AssistedFactory
    interface Factory {
        fun create(bookUuid: String): HouseholdReviewsViewModel
    }

    /** The user ids whose spoiler the reader has opened. It lives here so switching tabs does not veil them again. */
    private val revealed = MutableStateFlow<Set<Long>>(emptySet())

    /** Both sections' state. It is kept while something collects it. */
    val uiState: StateFlow<HouseholdReviewsUIState> = combine(
        reviewsRepository.getReviewsFlow(bookUuid),
        readStatusRepository.getReadStatusFlow(bookUuid),
        revealed,
    ) { reviews, status, open ->
        HouseholdReviewsUIState(
            stage = stageRatingModel(reviews),
            tab = reviewsTabModel(reviews, isFinished(status), open),
            nowMillis = timeSource.nowMillis(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HouseholdReviewsUIState())

    /** The reload key of the last refresh, so re-entering composition with the same key does nothing. */
    private var lastLoadedKey: Int? = null

    /** Refreshes the reviews once for each new [reloadKey]. */
    fun reload(reloadKey: Int) {
        if (reloadKey == lastLoadedKey) return
        lastLoadedKey = reloadKey
        viewModelScope.launch { reviewsRepository.refresh(bookUuid) }
    }

    /** Opens the spoiler of the review by [userId] for the rest of this page's life. */
    fun reveal(userId: Long) {
        revealed.update { it + userId }
    }

    /** Fetches the reviews again, from the Try again link. */
    fun retry() {
        viewModelScope.launch { reviewsRepository.refresh(bookUuid) }
    }
}
