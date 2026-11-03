package com.omnibus.omnibus.data.reviews

import kotlinx.coroutines.flow.Flow

/**
 * One reader's review of a book. [userId] is 0 and [displayName] is blank for the signed-in reader's own review,
 * because the server does not attribute it.
 *
 * @property rating Stars from 0.5 to 5, or null when the reader gave no rating.
 * @property text The review's Markdown source, or null when it is blank.
 * @property privateNotes The signed-in reader's private notes, or null. Always null for other readers.
 * @property isSpoiler Whether [text] gives away the story.
 * @property updatedAtMillis When the review last changed, in epoch milliseconds, or null when unknown.
 */
data class Review(
    val userId: Long,
    val displayName: String,
    val hasAvatar: Boolean,
    val rating: Float?,
    val text: String?,
    val privateNotes: String?,
    val isSpoiler: Boolean,
    val updatedAtMillis: Long?,
)

/**
 * A book's reviews for the household.
 *
 * @property mine The signed-in reader's own review, or null when they have none.
 * @property others The other readers' reviews, in the server's order.
 * @property summary The server's rating summary, or null until at least one reader has rated the book.
 */
data class BookReviews(val mine: Review?, val others: List<Review>, val summary: ReviewSummary?)

/** The server's average of the household's ratings, and how many readers rated the book. */
data class ReviewSummary(val average: Float, val count: Int)

/** What the app knows about one book's reviews. */
sealed interface ReviewsState {
    /** No answer from the server yet. */
    data object Loading : ReviewsState

    /** The request failed because the device has no connection. */
    data object Offline : ReviewsState

    /** The request failed for another reason, such as a server error or no saved server. */
    data object Failed : ReviewsState

    /** The server's reviews for the book. */
    data class Loaded(val reviews: BookReviews) : ReviewsState
}

/**
 * Reads a book's household reviews. Each book has one shared state, which every collector sees. Every failure is a
 * state, so nothing throws except cancellation.
 */
interface ReviewsRepository {

    /**
     * The shared state for [bookUuid], which replays its current value. Collecting it when the state is not
     * [ReviewsState.Loaded] starts a fetch, and a device that comes back online refetches an [ReviewsState.Offline]
     * state that is still being collected.
     */
    fun getReviewsFlow(bookUuid: String): Flow<ReviewsState>

    /** Fetches the reviews again, unless a fetch for [bookUuid] is already in flight, which it joins. */
    suspend fun refresh(bookUuid: String)

    /**
     * Applies [patch] to the signed-in reader's review of [bookUuid]. The shared state moves at once, then the server's
     * review replaces it on success, or the state rolls back to the last server-confirmed answer on failure. A patch
     * that would change nothing makes no request.
     */
    suspend fun updateMyReview(bookUuid: String, patch: ReviewPatch): ReviewWriteResult

    /**
     * Deletes the signed-in reader's review of [bookUuid], with its stars, text and private notes. The shared state
     * moves at once, and rolls back on failure.
     */
    suspend fun deleteMyReview(bookUuid: String): ReviewWriteResult
}

/** The outcome of [ReviewsRepository.updateMyReview] or [ReviewsRepository.deleteMyReview]. */
sealed interface ReviewWriteResult {
    /** The server accepted the write. [mine] is the server's review after it, and it replaces the optimistic value. */
    data class Saved(val mine: Review?) : ReviewWriteResult

    /** The patch would change nothing, so no request was made. */
    data object Unchanged : ReviewWriteResult

    /** The write failed and the shared state was rolled back. [error] says why. */
    data class Failed(val error: ReviewError) : ReviewWriteResult
}

/** Why a review write failed. */
sealed interface ReviewError {
    /** No server is configured, so no request was made. */
    data object NoServer : ReviewError

    /** The server could not be reached (an IOException). */
    data object Unreachable : ReviewError

    /** The server answered 401, so the session has expired. */
    data object SessionExpired : ReviewError

    /** The server answered 404, or the book uuid was blank and no request was made. */
    data object BookNotFound : ReviewError

    /** The server answered another non-2xx code. */
    data class Http(val code: Int) : ReviewError

    /** A 2xx body that does not decode, or any other unexpected failure. */
    data object UnexpectedResponse : ReviewError

    /** The shared state was not Loaded, so no request was made. */
    data object NotLoaded : ReviewError
}
