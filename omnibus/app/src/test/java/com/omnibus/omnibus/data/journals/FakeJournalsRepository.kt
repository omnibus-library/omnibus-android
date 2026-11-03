package com.omnibus.omnibus.data.journals

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [JournalsRepository] for consumers' tests. It follows [DefaultJournalsRepository]'s rules: a loaded list
 * never regresses, and a refresh from Failed shows Loading while it runs. There is no HTTP.
 *
 * Unlike production, collecting [getJournalsFlow] does not fetch. Call [seed] or [refresh] first.
 */
class FakeJournalsRepository : JournalsRepository {

    private val states = mutableMapOf<String, MutableStateFlow<JournalsState>>()

    private fun stateFor(uuid: String): MutableStateFlow<JournalsState> =
        states.getOrPut(uuid) { MutableStateFlow(JournalsState.Loading) }

    /** The next refresh outcomes, applied in order. When empty, a refresh leaves the state as it was. */
    val refreshResults = ArrayDeque<JournalsState>()

    /** When non-empty, the next refresh waits on the head after showing Loading, before its outcome applies. */
    val refreshGates = ArrayDeque<CompletableDeferred<Unit>>()

    /** The uuid of every refresh, in call order. */
    val refreshCalls = mutableListOf<String>()

    /** Sets [bookUuid]'s state as if the server had answered it. */
    fun seed(bookUuid: String, state: JournalsState) {
        stateFor(bookUuid).value = state
    }

    override fun getJournalsFlow(bookUuid: String): Flow<JournalsState> = stateFor(bookUuid)

    override suspend fun refresh(bookUuid: String) {
        refreshCalls += bookUuid
        val state = stateFor(bookUuid)
        val before = state.value
        if (before is JournalsState.Failed) state.value = JournalsState.Loading
        refreshGates.removeFirstOrNull()?.await()
        val outcome = refreshResults.removeFirstOrNull() ?: before
        val current = state.value
        state.value = when {
            outcome is JournalsState.Loaded -> outcome
            current is JournalsState.Loaded -> current
            outcome is JournalsState.Failed -> JournalsState.Failed
            else -> before
        }
    }
}
