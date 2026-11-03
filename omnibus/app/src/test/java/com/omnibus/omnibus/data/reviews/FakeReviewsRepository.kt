package com.omnibus.omnibus.data.reviews

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [ReviewsRepository] for consumers' tests. Unlike production, collecting [getReviewsFlow] does not fetch,
 * so call [seed] first. A [refresh] records its uuid and, when [refreshResults] has an entry, moves to that state.
 */
class FakeReviewsRepository : ReviewsRepository {

    private val states = mutableMapOf<String, MutableStateFlow<ReviewsState>>()

    private fun state(uuid: String): MutableStateFlow<ReviewsState> =
        states.getOrPut(uuid) { MutableStateFlow(ReviewsState.Loading) }

    /** The next refresh's outcomes, in order. When empty, a refresh leaves the state as it is. */
    val refreshResults = ArrayDeque<ReviewsState>()

    /** The uuid of every refresh, in call order. */
    val refreshCalls = mutableListOf<String>()

    /** Sets [bookUuid]'s state as if the server had answered it. */
    fun seed(bookUuid: String, state: ReviewsState) {
        state(bookUuid).value = state
    }

    override fun getReviewsFlow(bookUuid: String): Flow<ReviewsState> = state(bookUuid)

    override suspend fun refresh(bookUuid: String) {
        refreshCalls += bookUuid
        refreshResults.removeFirstOrNull()?.let { seed(bookUuid, it) }
    }
}
