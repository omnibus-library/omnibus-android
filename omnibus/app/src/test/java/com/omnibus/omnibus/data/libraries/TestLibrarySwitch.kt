package com.omnibus.omnibus.data.libraries

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A [LibrarySwitch] that records its calls. [commitGate], when set, holds [commit] until it is completed. */
class TestLibrarySwitch : LibrarySwitch {

    /** The (target id, kind) of each commit, in call order. */
    val commits = mutableListOf<Pair<String, SwitchKind>>()

    /** When non-null, [commit] waits for it before it records anything. */
    var commitGate: CompletableDeferred<Unit>? = null

    /** The pending opening, as the flow reports it. */
    val pending = MutableStateFlow<PendingOpening?>(null)

    /** How many times [clearPendingOpening] was called. */
    var clears = 0

    override suspend fun commit(targetLibraryId: String, kind: SwitchKind) {
        commitGate?.await()
        commits += targetLibraryId to kind
    }

    override fun getPendingOpeningFlow(): Flow<PendingOpening?> = pending

    override suspend fun clearPendingOpening() {
        clears++
        pending.value = null
    }
}
