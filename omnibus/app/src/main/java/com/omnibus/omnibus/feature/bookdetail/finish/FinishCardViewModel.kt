package com.omnibus.omnibus.feature.bookdetail.finish

import com.omnibus.omnibus.data.readstatus.ReadStatusState
import com.omnibus.omnibus.data.reviews.ReviewsState
import com.omnibus.omnibus.feature.bookdetail.progress.ReadingSession

/**
 * What the card knows. [sessions] is null until a read succeeds; [finishedMembers] holds the series members known to
 * be finished.
 */
data class FinishCardUIState(
    val status: ReadStatusState = ReadStatusState.Unknown,
    val sessions: List<ReadingSession>? = null,
    val highlightCount: Int = 0,
    val reviews: ReviewsState = ReviewsState.Loading,
    val finishedMembers: Set<String> = emptySet(),
) {
    /** Whether the book is known to be Finished. */
    val finished: Boolean get() = status.isFinished()

    /** When the book was finished, or null. */
    val finishedAtMillis: Long? get() = (status as? ReadStatusState.Known)?.record?.finishedAtMillis

    /** Whether the row may point at the next book, so the series members are worth reading. */
    val wantsSeries: Boolean get() = reviews.isRated()
}

/** How many times the card has risen and the burst has been thrown on this page. 0 means never. */
data class FinishCelebration(val rises: Int = 0, val bursts: Int = 0)
