package com.omnibus.omnibus.data.libraries.local

import com.omnibus.omnibus.data.libraries.PendingOpening
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** An in-memory [PendingOpeningStore]. [saves] records each save in call order. */
class TestPendingOpeningStore : PendingOpeningStore {

    /** The stored opening. */
    val opening = MutableStateFlow<PendingOpening?>(null)

    /** Every opening passed to [save], in call order. */
    val saves = mutableListOf<PendingOpening>()

    /** How many times [clear] was called. */
    var clears = 0

    override fun getFlow(): Flow<PendingOpening?> = opening

    override suspend fun save(opening: PendingOpening) {
        saves += opening
        this.opening.value = opening
    }

    override suspend fun clear() {
        clears++
        opening.value = null
    }
}
