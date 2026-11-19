package com.omnibus.omnibus.data.highlights

import com.omnibus.omnibus.testing.unscripted
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [HighlightsRepository] for consumers' tests. It follows [DefaultHighlightsRepository]'s rules: a loaded list
 * never regresses, and a refresh from Failed shows Loading while it runs. A saved create is appended to a loaded list, as
 * production does. There is no HTTP.
 *
 * Unlike production, collecting [getHighlightsFlow] does not fetch. Call [seed] or [refresh] first. A create is answered by
 * [onAdd], which a test replaces to script its result.
 */
class TestHighlightsRepository : HighlightsRepository {

    private val states = mutableMapOf<String, MutableStateFlow<HighlightsState>>()

    private fun stateFor(uuid: String): MutableStateFlow<HighlightsState> =
        states.getOrPut(uuid) { MutableStateFlow(HighlightsState.Loading) }

    /** The next refresh outcomes, applied in order. When empty, a refresh leaves the state as it was. */
    val refreshResults = ArrayDeque<HighlightsState>()

    /** When non-empty, the next refresh waits on the head after showing Loading, before its outcome applies. */
    val refreshGates = ArrayDeque<CompletableDeferred<Unit>>()

    /** The uuid of every refresh, in call order. */
    val refreshCalls = mutableListOf<String>()

    /** Every draft passed to [addHighlight], in call order. */
    val drafts = mutableListOf<HighlightDraft>()

    /** Answers a create. */
    var onAdd: suspend (HighlightDraft) -> HighlightWriteResult = { unscripted("addHighlight") }

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

    override suspend fun addHighlight(draft: HighlightDraft): HighlightWriteResult {
        drafts += draft
        val result = onAdd(draft)
        if (result is HighlightWriteResult.Saved) {
            val state = stateFor(draft.bookUuid)
            val loaded = state.value as? HighlightsState.Loaded
            if (loaded != null && loaded.highlights.none { it.id == result.highlight.id }) {
                state.value = HighlightsState.Loaded(loaded.highlights + result.highlight)
            }
        }
        return result
    }
}
