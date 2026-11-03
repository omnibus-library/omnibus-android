package com.omnibus.omnibus.feature.bookdetail.reviews.mine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.reviews.Patch
import com.omnibus.omnibus.data.reviews.ReviewError
import com.omnibus.omnibus.data.reviews.ReviewPatch
import com.omnibus.omnibus.data.reviews.ReviewWriteResult
import com.omnibus.omnibus.data.reviews.ReviewsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A save that did not reach the server, as the card words it. [offline] is true when the device had no connection, so
 * the message says so.
 */
data class MyReviewSaveFailure(val kind: Kind, val offline: Boolean) {
    /** Which change failed: a rating, or the delete. */
    enum class Kind { Rate, Delete }
}

/**
 * What the Your review card shows. [card] is the reader's review or why it is unknown. [saveFailure] is the one-shot
 * failure to post, or null.
 */
data class MyReviewUIState(
    val card: MyReviewModel = MyReviewModel.Unknown(UnknownReason.Loading),
    val saveFailure: MyReviewSaveFailure? = null,
)

/**
 * Owns the reader's review for one book on the Reviews tab. The repository holds the shared state and does the
 * optimistic write and rollback. This ViewModel maps the state, forwards a rating or a delete, counts a saved rating for
 * the stage's pulse, and turns a failed save into a one-shot message. It changes nothing while the review is unknown.
 *
 * @property bookUuid The book this section shows.
 */
@HiltViewModel(assistedFactory = MyReviewViewModel.Factory::class)
class MyReviewViewModel @AssistedInject constructor(
    @Assisted private val bookUuid: String,
    private val reviewsRepository: ReviewsRepository,
    private val reviewSavePulse: ReviewSavePulse,
) : ViewModel() {

    /** Creates a [MyReviewViewModel] for the book with [bookUuid]. */
    @AssistedFactory
    interface Factory {
        fun create(bookUuid: String): MyReviewViewModel
    }

    private val saveFailure = MutableStateFlow<MyReviewSaveFailure?>(null)

    /** The section's state. It is kept while something collects it. */
    val uiState: StateFlow<MyReviewUIState> = combine(
        reviewsRepository.getReviewsFlow(bookUuid),
        saveFailure,
    ) { state, failure ->
        MyReviewUIState(card = myReviewModel(state), saveFailure = failure)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyReviewUIState())

    /** The reload key of the last refresh, so re-entering composition with the same key does nothing. */
    private var lastLoadedKey: Int? = null

    /** Refreshes the reviews once for each new [reloadKey]. */
    fun reload(reloadKey: Int) {
        if (reloadKey == lastLoadedKey) return
        lastLoadedKey = reloadKey
        viewModelScope.launch { reviewsRepository.refresh(bookUuid) }
    }

    /** Saves [rating] as the reader's stars, or clears them for null. Nothing happens while the review is unknown. */
    fun rate(rating: Float?) {
        val card = uiState.value.card as? MyReviewModel.Known ?: return
        if (card.review?.rating == rating) return
        viewModelScope.launch {
            when (val result = reviewsRepository.updateMyReview(bookUuid, ReviewPatch(rating = Patch.Set(rating)))) {
                is ReviewWriteResult.Saved -> reviewSavePulse.bump(bookUuid)
                is ReviewWriteResult.Failed -> fail(MyReviewSaveFailure.Kind.Rate, result.error)
                ReviewWriteResult.Unchanged -> Unit
            }
        }
    }

    /** Deletes the reader's whole review. Nothing happens unless there is one to delete. */
    fun delete() {
        val card = uiState.value.card as? MyReviewModel.Known ?: return
        if (card.review == null) return
        viewModelScope.launch {
            val result = reviewsRepository.deleteMyReview(bookUuid)
            if (result is ReviewWriteResult.Failed) fail(MyReviewSaveFailure.Kind.Delete, result.error)
        }
    }

    /** Clears the failure after its message has been shown. A later failure shows again. */
    fun saveFailureShown() {
        saveFailure.value = null
    }

    /** Posts a failure for [error], unless nothing was sent because the state was not loaded. */
    private fun fail(kind: MyReviewSaveFailure.Kind, error: ReviewError) {
        if (error == ReviewError.NotLoaded) return
        saveFailure.value = MyReviewSaveFailure(
            kind = kind,
            offline = error == ReviewError.NoServer || error == ReviewError.Unreachable,
        )
    }
}
