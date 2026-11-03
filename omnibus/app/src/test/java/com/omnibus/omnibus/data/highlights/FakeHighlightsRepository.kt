package com.omnibus.omnibus.data.highlights

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [HighlightsRepository] for consumers' tests. It follows [DefaultHighlightsRepository]'s rules: a loaded list
 * never regresses, and a refresh from Failed shows Loading while it runs. There is no HTTP.
 *
 * Unlike production, collecting [getHighlightsFlow] does not fetch. Call [seed] or [refresh] first.
 */
class FakeHighlightsRepository : HighlightsRepository {

    private val states = mutableMapOf<String, MutableStateFlow<HighlightsState>>()

    private fun stateFor(uuid: String): MutableStateFlow<HighlightsState> =
        states.getOrPut(uuid) { MutableStateFlow(HighlightsState.Loading) }

    /** The next refresh outcomes, applied in order. When empty, a refresh leaves the state as it was. */
    val refreshResults = ArrayDeque<HighlightsState>()

    /** When non-empty, the next refresh waits on the head after showing Loading, before its outcome applies. */
    val refreshGates = ArrayDeque<CompletableDeferred<Unit>>()

    /** The uuid of every refresh, in call order. */
    val refreshCalls = mutableListOf<String>()

    /** Sets [bookUuid]'s state as if the server had answered it. */
    fun seed(bookUuid: String, state: HighlightsState) {
        stateFor(bookUuid).value = state
    }

    override fun getHighlightsFlow(bookUuid: String): Flow<HighlightsState> = stateFor(bookUuid)

    override suspend fun refresh(bookUuid: String) {
        refreshCalls += bookUuid
        val state = stateFor(bookUuid)
        val before = state.value
        if (before is HighlightsState.Failed) state.value = HighlightsState.Loading
        refreshGates.removeFirstOrNull()?.await()
        val outcome = refreshResults.removeFirstOrNull() ?: before
        val current = state.value
        state.value = when {
            outcome is HighlightsState.Loaded -> outcome
            current is HighlightsState.Loaded -> current
            outcome is HighlightsState.Failed -> HighlightsState.Failed
            else -> before
        }
    }
}
