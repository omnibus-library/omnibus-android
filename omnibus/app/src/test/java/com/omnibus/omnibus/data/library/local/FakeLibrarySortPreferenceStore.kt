package com.omnibus.omnibus.data.library.local

import com.omnibus.omnibus.data.library.LibrarySortPreference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * In-memory [LibrarySortPreferenceStore] for tests. Every [save] is recorded in [saves].
 *
 * @param initial The preference present before any [save] call.
 */
class FakeLibrarySortPreferenceStore(initial: LibrarySortPreference = LibrarySortPreference()) :
    LibrarySortPreferenceStore {

    private val state = MutableStateFlow(initial)

    /** Every preference passed to [save], in call order. */
    val saves = mutableListOf<LibrarySortPreference>()

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
}
