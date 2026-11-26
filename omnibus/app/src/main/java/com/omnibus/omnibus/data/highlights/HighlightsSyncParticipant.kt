package com.omnibus.omnibus.data.highlights

import com.omnibus.omnibus.data.libraries.LibrarySyncParticipant
import javax.inject.Inject

/** Reports and drains the highlight writes in flight, so a library switch waits for them (C10). Nothing else is queued, so nothing else is sent. */
class HighlightsSyncParticipant @Inject constructor(
    private val repository: DefaultHighlightsRepository,
) : LibrarySyncParticipant {

    override suspend fun hasPending(): Boolean = repository.inFlightWrites.hasPending()

    override suspend fun sendPending(): Boolean = repository.inFlightWrites.awaitAll()
}
