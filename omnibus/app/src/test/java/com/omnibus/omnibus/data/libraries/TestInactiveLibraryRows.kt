package com.omnibus.omnibus.data.libraries

import com.omnibus.omnibus.data.audiobook.position.AudioPosition
import com.omnibus.omnibus.data.audiobook.position.ListeningSessionReport

/** An [InactiveLibraryRows] over in-memory rows, keyed by library id. */
class TestInactiveLibraryRows : InactiveLibraryRows {

    /** The pending positions of each library. */
    val positions = mutableMapOf<String, MutableList<AudioPosition>>()

    /** The queued sessions of each library, in queue order. */
    val sessions = mutableMapOf<String, MutableList<ListeningSessionReport>>()

    /** The pending rates of each library, by book uuid. */
    val rates = mutableMapOf<String, MutableMap<String, Double>>()

    /** When non-null, every read throws this. */
    var readFailure: Throwable? = null

    override suspend fun pendingPositions(libraryId: String): List<AudioPosition> {
        readFailure?.let { throw it }
        return positions[libraryId].orEmpty().toList()
    }

    override suspend fun sessions(libraryId: String): List<ListeningSessionReport> {
        readFailure?.let { throw it }
        return sessions[libraryId].orEmpty().toList()
    }

    override suspend fun pendingRates(libraryId: String): Map<String, Double> {
        readFailure?.let { throw it }
        return rates[libraryId].orEmpty().toMap()
    }

    override suspend fun clearPosition(libraryId: String, position: AudioPosition) {
        positions[libraryId]?.remove(position)
    }

    override suspend fun removeSessions(libraryId: String, clientIds: Set<String>) {
        sessions[libraryId]?.removeAll { it.clientId in clientIds }
    }

    override suspend fun clearRate(libraryId: String, bookUuid: String, rate: Double) {
        val pending = rates[libraryId] ?: return
        if (pending[bookUuid] == rate) pending.remove(bookUuid)
    }
}
