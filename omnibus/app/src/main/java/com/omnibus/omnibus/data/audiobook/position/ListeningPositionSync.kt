package com.omnibus.omnibus.data.audiobook.position

import kotlinx.coroutines.flow.StateFlow

/**
 * C8: the position sync that other features read. It offers a late server position (I-RES-6) and forces a save of the
 * current position (I-SAVE-3). Implemented by [DefaultListeningPositionSync].
 */
interface ListeningPositionSync {

    /**
     * A server position that arrived after the load settled, is strictly newer than the local candidate, matches the
     * loaded file and is more than 1 s ahead of the settled start. Null otherwise. The next load and an unload clear it.
     */
    val lateServerPosition: StateFlow<LatePosition?>

    /** Saves the current position now: locally, then pushed. Returns after the local save. Never throws. */
    suspend fun forceSave()
}

/** A server position for [bookUuid] at file [bookFileId], offered after the load settled (C8). */
data class LatePosition(val bookUuid: String, val bookFileId: Long, val positionSeconds: Double)
