package com.omnibus.omnibus.data.audiobook.position

import com.omnibus.omnibus.data.audiobook.position.local.AudioPositionStore
import com.omnibus.omnibus.data.libraries.LibrarySyncParticipant
import javax.inject.Inject

/**
 * Sends the listening positions and sessions waiting in the store (C10). Nothing pending is a success, even offline;
 * a row that is kept or rejected is a failure, and it stays in the store under its library.
 */
class ListeningSyncParticipant @Inject constructor(
    private val repository: DefaultListeningProgressRepository,
    private val store: AudioPositionStore,
) : LibrarySyncParticipant {

    override suspend fun hasPending(): Boolean = store.allPending().isNotEmpty() || store.sessions().isNotEmpty()

    override suspend fun sendPending(): Boolean = !hasPending() || repository.flush()
}
