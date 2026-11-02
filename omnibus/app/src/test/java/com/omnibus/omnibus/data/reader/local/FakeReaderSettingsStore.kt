package com.omnibus.omnibus.data.reader.local

import com.omnibus.omnibus.data.reader.ReaderSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * In-memory [ReaderSettingsStore] for tests. Every [save] is recorded in [saves].
 *
 * @param initial The settings present before any [save] call.
 */
class FakeReaderSettingsStore(initial: ReaderSettings = ReaderSettings()) : ReaderSettingsStore {

    private val state = MutableStateFlow(initial)

    /** Every value passed to [save], in call order. */
    val saves = mutableListOf<ReaderSettings>()

    /** When non-null, collecting [settings] waits for this before emitting, to hold a read in flight. */
    var holdFirstEmission: CompletableDeferred<Unit>? = null

    /** When non-null, [save] waits for this after recording, to hold a write in flight. */
    var holdSaves: CompletableDeferred<Unit>? = null

    /** When true, [save] records the attempt but stores nothing, like the real store after a swallowed failure. */
    var saveFails = false

    override val settings: Flow<ReaderSettings> = flow {
        holdFirstEmission?.await()
        emitAll(state)
    }

    /** Replaces the stored value, as another writer would. */
    fun emit(settings: ReaderSettings) {
        state.value = settings
    }

    override suspend fun save(settings: ReaderSettings) {
        saves += settings
        holdSaves?.await()
        if (!saveFails) state.value = settings
    }
}
