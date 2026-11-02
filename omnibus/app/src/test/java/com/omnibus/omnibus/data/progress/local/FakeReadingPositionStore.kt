package com.omnibus.omnibus.data.progress.local

import com.omnibus.omnibus.data.progress.ReadingPosition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [ReadingPositionStore] for tests. Every [save] is recorded in [saves].
 *
 * @param initial the positions present before any [save] call, by book uuid.
 */
class FakeReadingPositionStore(initial: Map<String, ReadingPosition> = emptyMap()) : ReadingPositionStore {

    private val states = mutableMapOf<String, MutableStateFlow<ReadingPosition?>>()

    init {
        initial.forEach { (uuid, position) -> stateFor(uuid).value = position }
    }

    /** Every position passed to [save], in call order. */
    val saves = mutableListOf<ReadingPosition>()

    override fun getPositionFlow(bookUuid: String): Flow<ReadingPosition?> = stateFor(bookUuid)

    override suspend fun save(position: ReadingPosition) {
        saves += position
        stateFor(position.bookUuid).value = position
    }

    private fun stateFor(bookUuid: String): MutableStateFlow<ReadingPosition?> =
        states.getOrPut(bookUuid) { MutableStateFlow(null) }
}
