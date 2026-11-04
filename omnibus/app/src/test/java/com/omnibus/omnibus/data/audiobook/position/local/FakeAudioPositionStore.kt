package com.omnibus.omnibus.data.audiobook.position.local

import com.omnibus.omnibus.data.audiobook.position.AudioPosition
import com.omnibus.omnibus.data.audiobook.position.ListeningSessionReport
import kotlinx.coroutines.CompletableDeferred

/**
 * An in-memory [AudioPositionStore]. Every method runs under one lock, because a MockWebServer thread can write
 * to it while a test reads. [saveGate], when set, holds the first [save] until it is completed.
 */
class FakeAudioPositionStore(
    cached: Map<String, AudioPosition> = emptyMap(),
    pending: Map<String, AudioPosition> = emptyMap(),
) : AudioPositionStore {

    private val lock = Any()
    private val cachedMap = cached.toMutableMap()
    private val pendingMap = pending.toMutableMap()
    private val sessionList = mutableListOf<ListeningSessionReport>()

    /** Holds the first [save] before it writes, until completed. Consumed by that save. */
    var saveGate: CompletableDeferred<Unit>? = null

    /** The cached positions, by book uuid. */
    val cachedPositions: Map<String, AudioPosition>
        get() = synchronized(lock) { cachedMap.toMap() }

    /** The pending writes, by book uuid. */
    val pendingPositions: Map<String, AudioPosition>
        get() = synchronized(lock) { pendingMap.toMap() }

    override suspend fun cached(bookUuid: String): AudioPosition? = synchronized(lock) { cachedMap[bookUuid] }

    override suspend fun pending(bookUuid: String): AudioPosition? = synchronized(lock) { pendingMap[bookUuid] }

    override suspend fun allPending(): List<AudioPosition> = synchronized(lock) { pendingMap.values.toList() }

    override suspend fun save(position: AudioPosition) {
        val gate = synchronized(lock) { saveGate.also { saveGate = null } }
        gate?.await()
        synchronized(lock) {
            cachedMap[position.bookUuid] = position
            pendingMap[position.bookUuid] = position
        }
    }

    override suspend fun clearPendingIf(position: AudioPosition) {
        synchronized(lock) {
            if (pendingMap[position.bookUuid] == position) pendingMap.remove(position.bookUuid)
        }
    }

    override suspend fun queueSessions(reports: List<ListeningSessionReport>) {
        synchronized(lock) { sessionList += reports }
    }

    override suspend fun sessions(): List<ListeningSessionReport> = synchronized(lock) { sessionList.toList() }

    override suspend fun removeSessions(clientIds: Set<String>) {
        synchronized(lock) { sessionList.removeAll { it.clientId in clientIds } }
    }
}
