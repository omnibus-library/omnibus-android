package com.omnibus.omnibus.data.highlights

import com.omnibus.omnibus.testing.unscripted
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [HighlightsRepository] for consumers' tests. It follows [DefaultHighlightsRepository]'s rules: a loaded list
 * never regresses, and a refresh from Failed shows Loading while it runs. A saved create is appended to a loaded list, as
 * production does. A deleted row leaves a loaded list, as production does. A confirmed recolour changes the row's colour in a
 * loaded list, as production does. A confirmed note write changes the row's note in a loaded list, as production does.
 * There is no HTTP.
 *
 * Unlike production, collecting [getHighlightsFlow] does not fetch. Call [seed] or [refresh] first. A create is answered by
 * [onAdd], which a test replaces to script its result. A delete is answered by [onDelete]. A recolour is answered by
 * [onRecolor]. A note write is answered by [onNote].
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

    /** The id of every delete, in call order. */
    val deletes = mutableListOf<Long>()

    /** Answers a delete. */
    var onDelete: suspend (bookUuid: String, id: Long) -> HighlightDeleteResult = { _, _ -> unscripted("deleteHighlight") }

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

    override suspend fun deleteHighlight(bookUuid: String, id: Long): HighlightDeleteResult {
        deletes += id
        val result = onDelete(bookUuid, id)
        if (result is HighlightDeleteResult.Deleted) {
            val state = stateFor(bookUuid)
            (state.value as? HighlightsState.Loaded)?.let { loaded ->
                state.value = HighlightsState.Loaded(loaded.highlights.filterNot { it.id == id })
            }
        }
        return result
    }

    /** Every recolour, as (id, colour), in call order. Recorded before [onRecolor] runs, so a gated call is listed at once. */
    val recolors = mutableListOf<Pair<Long, HighlightColor>>()

    /** Answers a recolour. */
    var onRecolor: suspend (bookUuid: String, id: Long, color: HighlightColor) -> HighlightRecolorResult =
        { _, _, _ -> unscripted("recolorHighlight") }

    override suspend fun recolorHighlight(bookUuid: String, id: Long, color: HighlightColor): HighlightRecolorResult {
        recolors += id to color
        val result = onRecolor(bookUuid, id, color)
        if (result is HighlightRecolorResult.Recolored) {
            val state = stateFor(bookUuid)
            (state.value as? HighlightsState.Loaded)?.let { loaded ->
                state.value = HighlightsState.Loaded(
                    loaded.highlights.map { if (it.id == id) it.copy(color = color) else it },
                )
            }
        }
        return result
    }

    /** Every note write, as (id, note), in call order. Recorded before [onNote] runs, so a gated call is listed at once. */
    val notes = mutableListOf<Pair<Long, String?>>()

    /** Answers a note write. */
    var onNote: suspend (bookUuid: String, id: Long, note: String?) -> HighlightNoteResult =
        { _, _, _ -> unscripted("updateHighlightNote") }

    override suspend fun updateHighlightNote(bookUuid: String, id: Long, note: String?): HighlightNoteResult {
        notes += id to note
        val result = onNote(bookUuid, id, note)
        if (result is HighlightNoteResult.Saved) {
            val state = stateFor(bookUuid)
            (state.value as? HighlightsState.Loaded)?.let { loaded ->
                state.value = HighlightsState.Loaded(
                    loaded.highlights.map { if (it.id == id) it.copy(note = note) else it },
                )
            }
        }
        return result
    }
}
