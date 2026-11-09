package com.omnibus.omnibus.data.library.local

import com.omnibus.omnibus.data.library.LibrarySortPreference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * In-memory [LibrarySortPreferenceStore] for tests. Every [save] is recorded in [saves], and every
 * [saveGroupBySeries] in [groupSaves].
 *
 * @param initial The preference present before any [save] call.
 * @param initialGroupBySeries The grouping setting present before any [saveGroupBySeries] call.
 */
class TestLibrarySortPreferenceStore(
    initial: LibrarySortPreference = LibrarySortPreference(),
    initialGroupBySeries: Boolean = false,
) : LibrarySortPreferenceStore {

    private val state = MutableStateFlow(initial)

    private val groupState = MutableStateFlow(initialGroupBySeries)

    /** Every preference passed to [save], in call order. */
    val saves = mutableListOf<LibrarySortPreference>()

    /** Every value passed to [saveGroupBySeries], in call order. */
    val groupSaves = mutableListOf<Boolean>()

    /** When non-null, collecting [preference] waits for this before emitting, to hold a restore in flight. */
    var holdFirstEmission: CompletableDeferred<Unit>? = null

    override val preference: Flow<LibrarySortPreference> = flow {
        holdFirstEmission?.await()
        emitAll(state)
    }

    override suspend fun save(preference: LibrarySortPreference) {
        saves += preference
        state.value = preference
    }

    override val groupBySeries: Flow<Boolean> = groupState

    override suspend fun saveGroupBySeries(enabled: Boolean) {
        groupSaves += enabled
        groupState.value = enabled
    }
}
