package com.omnibus.omnibus.feature.bookdetail.reviews.mine

import com.omnibus.omnibus.data.reviews.Review
import com.omnibus.omnibus.data.reviews.ReviewsState

/** Why the reader's own review is not known yet. */
enum class UnknownReason { Loading, Offline, Failed }

/**
 * What the Your review card shows. [Unknown] while the review is not known, so the card shows no review and writes
 * nothing. [Known] once it is, with null when the reader has no review.
 */
sealed interface MyReviewModel {
    /** The review is not known, for [reason]. */
    data class Unknown(val reason: UnknownReason) : MyReviewModel

    /** The reader's review, or null when they have none. */
    data class Known(val review: Review?) : MyReviewModel
}

/** The card's model for a reviews state. */
internal fun myReviewModel(state: ReviewsState): MyReviewModel = when (state) {
    ReviewsState.Loading -> MyReviewModel.Unknown(UnknownReason.Loading)
    ReviewsState.Offline -> MyReviewModel.Unknown(UnknownReason.Offline)
    ReviewsState.Failed -> MyReviewModel.Unknown(UnknownReason.Failed)
    is ReviewsState.Loaded -> MyReviewModel.Known(state.reviews.mine)
}

/** The stars the reader gave, or null when unrated or not known. */
internal val MyReviewModel.rating: Float?
    get() = (this as? MyReviewModel.Known)?.review?.rating

/** Whether the reader has a review, which is what the trash can deletes. */
internal val MyReviewModel.hasReview: Boolean
    get() = (this as? MyReviewModel.Known)?.review != null

/** Whether the reader has written text or private notes, which makes the pencil say "Edit review". */
internal val MyReviewModel.hasWriting: Boolean
    get() = (this as? MyReviewModel.Known)?.review?.let { it.text != null || it.privateNotes != null } ?: false

/** Whether the prompt to write a review shows: the review is known and has no text. */
internal val MyReviewModel.showsPrompt: Boolean
    get() = this is MyReviewModel.Known && review?.text == null

/** The note under the stars. [Rated] carries the rating to show. */
internal sealed interface MyReviewNote {
    /** The reader has not rated the book. */
    data object NotRated : MyReviewNote

    /** The reader's rating, which the note reads out. */
    data class Rated(val value: Float) : MyReviewNote

    /** The review is loading. */
    data object Loading : MyReviewNote

    /** The device has no connection, so the review cannot load. */
    data object Offline : MyReviewNote

    /** The review could not load for another reason. */
    data object Failed : MyReviewNote
}

/** The note for the card: the rating, "not rated", or why the review is not known. */
internal fun MyReviewModel.note(): MyReviewNote = when (this) {
    is MyReviewModel.Known -> review?.rating?.let { MyReviewNote.Rated(it) } ?: MyReviewNote.NotRated
    is MyReviewModel.Unknown -> when (reason) {
        UnknownReason.Loading -> MyReviewNote.Loading
        UnknownReason.Offline -> MyReviewNote.Offline
        UnknownReason.Failed -> MyReviewNote.Failed
    }
}
