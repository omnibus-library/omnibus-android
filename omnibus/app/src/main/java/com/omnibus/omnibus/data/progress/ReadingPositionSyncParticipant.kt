package com.omnibus.omnibus.data.progress

import com.omnibus.omnibus.data.libraries.LibrarySyncParticipant
import javax.inject.Inject

/** Reports and drains the reading position push in flight (a failed push is never retried), so a library switch waits for them (C10). Nothing else is queued, so nothing else is sent. */
class ReadingPositionSyncParticipant @Inject constructor(
    private val repository: DefaultProgressRepository,
) : LibrarySyncParticipant {

    override suspend fun hasPending(): Boolean = repository.inFlightWrites.hasPending()

    override suspend fun sendPending(): Boolean = repository.inFlightWrites.awaitAll()
}
