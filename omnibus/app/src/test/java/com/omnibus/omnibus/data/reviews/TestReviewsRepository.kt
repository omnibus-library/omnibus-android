package com.omnibus.omnibus.data.reviews

import com.omnibus.omnibus.testing.unscripted
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [ReviewsRepository] for consumers' tests. Unlike production, collecting [getReviewsFlow] does not fetch,
 * so call [seed] first. A [refresh] records its uuid and, when [refreshResults] has an entry, moves to that state.
 *
 * The writes mirror [DefaultReviewsRepository]'s optimistic, rollback and superseded-write rules, so a test that passes
 * here behaves the same against production. A write runs in the caller, with no HTTP and no application scope, and a
 * save does not start a refetch.
 */
class TestReviewsRepository : ReviewsRepository {

    /** One book's state, with the same bookkeeping as production's entry. */
    private class Entry {
        val state = MutableStateFlow<ReviewsState>(ReviewsState.Loading)
        var confirmed: ReviewsState = ReviewsState.Loading
        var confirmedSeq = 0L
        var writeSeq = 0L
        var pendingWrites = 0
    }

    private val entries = mutableMapOf<String, Entry>()

    private fun entry(uuid: String): Entry = entries.getOrPut(uuid) { Entry() }

    /** The next refresh's outcomes, in order. When empty, a refresh leaves the state as it is. */
    val refreshResults = ArrayDeque<ReviewsState>()

    /** The uuid of every refresh, in call order. */
    val refreshCalls = mutableListOf<String>()

    /** The next write's outcomes, in order. */
    val writeResults = ArrayDeque<ReviewWriteResult>()

    /** When non-empty, the next write waits on the head after its optimistic apply, before taking its result. */
    val writeGates = ArrayDeque<CompletableDeferred<Unit>>()

    /** Every update that would make a request, in call order. A no-op patch is not recorded. */
    val updateCalls = mutableListOf<Pair<String, ReviewPatch>>()

    /** The uuid of every delete that would make a request, in call order. */
    val deleteCalls = mutableListOf<String>()

    /** Sets [bookUuid]'s state as if the server had answered it. */
    fun seed(bookUuid: String, state: ReviewsState) {
        val entry = entry(bookUuid)
        entry.state.value = state
        entry.confirmed = state
    }

    override fun getReviewsFlow(bookUuid: String): Flow<ReviewsState> = entry(bookUuid).state

    override suspend fun refresh(bookUuid: String) {
        refreshCalls += bookUuid
        refreshResults.removeFirstOrNull()?.let { seed(bookUuid, it) }
    }

    override suspend fun updateMyReview(bookUuid: String, patch: ReviewPatch): ReviewWriteResult {
        if (bookUuid.isBlank()) return ReviewWriteResult.Failed(ReviewError.BookNotFound)
        val current = entry(bookUuid).state.value as? ReviewsState.Loaded
            ?: return ReviewWriteResult.Failed(ReviewError.NotLoaded)
        if (patch.isNoOpFor(current.reviews.mine)) return ReviewWriteResult.Unchanged

        updateCalls += bookUuid to patch
        return write(bookUuid, optimistic = current.reviews.mine.applying(patch))
    }

    override suspend fun deleteMyReview(bookUuid: String): ReviewWriteResult {
        if (bookUuid.isBlank()) return ReviewWriteResult.Failed(ReviewError.BookNotFound)
        if (entry(bookUuid).state.value !is ReviewsState.Loaded) return ReviewWriteResult.Failed(ReviewError.NotLoaded)

        deleteCalls += bookUuid
        return write(bookUuid, optimistic = null)
    }

    /**
     * Applies [optimistic] at once, then takes the scripted result. The bookkeeping is production's: a result replaces
     * the confirmed value, and only the latest write sets the visible state.
     */
    private suspend fun write(bookUuid: String, optimistic: Review?): ReviewWriteResult {
        val entry = entry(bookUuid)
        val current = entry.state.value as ReviewsState.Loaded
        entry.pendingWrites++
        val seq = ++entry.writeSeq
        entry.state.value = ReviewsState.Loaded(current.reviews.copy(mine = optimistic))

        writeGates.removeFirstOrNull()?.await()
        val result = writeResults.removeFirstOrNull() ?: unscripted("a review write")

        entry.pendingWrites--
        val confirmed = entry.confirmed
        if (result is ReviewWriteResult.Saved && seq >= entry.confirmedSeq && confirmed is ReviewsState.Loaded) {
            entry.confirmed = ReviewsState.Loaded(confirmed.reviews.copy(mine = result.mine))
            entry.confirmedSeq = seq
        }
        if (seq == entry.writeSeq) entry.state.value = entry.confirmed
        return result
    }
}
