package com.omnibus.omnibus.feature.bookdetail.finish

import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.readstatus.ReadStatus
import com.omnibus.omnibus.data.readstatus.ReadStatusState
import com.omnibus.omnibus.data.reviews.ReviewsState
import com.omnibus.omnibus.data.series.SeriesMember
import com.omnibus.omnibus.feature.bookdetail.progress.ReadingSession

/** One part of the totals line. */
sealed interface FinishTotal {
    /** The days from the first session to the finish, at least one. */
    data class Days(val count: Int) : FinishTotal

    /** The reading sessions. */
    data class Sessions(val count: Int) : FinishTotal

    /** The highlights. */
    data class Highlights(val count: Int) : FinishTotal
}

/** The card's one way onward. */
sealed interface FinishAction {
    /** Leave a review: the reader has not rated the book. */
    data object Review : FinishAction

    /** Open the next unfinished book in the series. */
    data class UpNext(val member: SeriesMember, val seriesName: String) : FinishAction
}

/** What the card shows: the book's title, the totals (empty = no line) and the row, if any. */
data class FinishCardModel(val title: String, val totals: List<FinishTotal>, val action: FinishAction?)

/** One day in milliseconds. */
private const val DAY_MILLIS = 86_400_000L

/** Whether the card can show for [book]: it has an ebook or an audiobook. */
internal fun showsFinishCard(book: Book): Boolean = book.hasEbook || book.hasAudiobook

/** Whether this state is a known Finished status. */
internal fun ReadStatusState.isFinished(): Boolean =
    this is ReadStatusState.Known && record.status == ReadStatus.Finished

/** Whether the reviews have loaded and the reader's own review has a rating above zero. */
internal fun ReviewsState.isRated(): Boolean =
    this is ReviewsState.Loaded && (reviews.mine?.rating ?: 0f) > 0f

/** Whether a read-status emission is the book becoming Finished: the previous one was known and not Finished. */
internal fun celebratesOnChange(previous: ReadStatusState?, next: ReadStatusState): Boolean =
    previous is ReadStatusState.Known && previous.record.status != ReadStatus.Finished && next.isFinished()

/**
 * The next book in [members] (series order) after [currentUuid]: the first unfinished one after it, else the earliest
 * unfinished one. A uuid not in [finished] is unfinished. Null when [currentUuid] is not a member or nothing is left.
 */
internal fun nextBook(members: List<SeriesMember>, currentUuid: String, finished: Set<String>): SeriesMember? {
    val at = members.indexOfFirst { it.uuid == currentUuid }
    if (at < 0) return null
    val unfinished = { m: SeriesMember -> m.uuid != currentUuid && m.uuid !in finished }
    return members.drop(at + 1).firstOrNull(unfinished) ?: members.firstOrNull(unfinished)
}

/** The row for [book]: none while [reviews] is unknown, Review when unrated, else the next book in its series. */
internal fun finishAction(
    reviews: ReviewsState,
    book: Book,
    members: List<SeriesMember>?,
    finished: Set<String>,
): FinishAction? {
    if (reviews !is ReviewsState.Loaded) return null
    if (!reviews.isRated()) return FinishAction.Review
    val seriesName = book.series?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (book.seriesId == null || members == null) return null
    return nextBook(members, book.uuid, finished)?.let { FinishAction.UpNext(it, seriesName) }
}

/**
 * The totals parts. Nothing without [finishedAtMillis]. Days (at least one, from the earliest session start) and
 * sessions need at least one session; highlights need a count above zero.
 */
internal fun finishTotals(
    finishedAtMillis: Long?,
    sessions: List<ReadingSession>?,
    highlightCount: Int,
): List<FinishTotal> {
    if (finishedAtMillis == null) return emptyList()
    return buildList {
        if (!sessions.isNullOrEmpty()) {
            val started = sessions.minOf { it.startedAtMillis }
            add(FinishTotal.Days(maxOf(1L, (finishedAtMillis - started) / DAY_MILLIS).toInt()))
            add(FinishTotal.Sessions(sessions.size))
        }
        if (highlightCount > 0) add(FinishTotal.Highlights(highlightCount))
    }
}

/** The card for [book] from the ViewModel's [state] and the series [members] (null while unknown). */
internal fun finishCardModel(book: Book, state: FinishCardUIState, members: List<SeriesMember>?): FinishCardModel =
    FinishCardModel(
        title = book.displayTitle,
        totals = finishTotals(state.finishedAtMillis, state.sessions, state.highlightCount),
        action = finishAction(state.reviews, book, members, state.finishedMembers),
    )

/** Hands out each play number once, so recomposition or a return to the page never replays it. */
class PlayLatch {

    /** The last number handed out. */
    private var played = 0

    /** Whether [number] has not been played yet. */
    fun isPending(number: Int): Boolean = number > played

    /** Marks [number] played; true only the first time for a number above the last one. */
    fun claim(number: Int): Boolean {
        if (number <= played) return false
        played = number
        return true
    }
}
