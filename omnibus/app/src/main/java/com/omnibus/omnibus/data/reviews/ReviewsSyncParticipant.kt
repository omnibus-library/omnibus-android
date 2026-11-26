package com.omnibus.omnibus.data.reviews

import com.omnibus.omnibus.data.libraries.LibrarySyncParticipant
import javax.inject.Inject

/** Reports and drains the review writes in flight, so a library switch waits for them (C10). Nothing else is queued, so nothing else is sent. */
class ReviewsSyncParticipant @Inject constructor(
    private val repository: DefaultReviewsRepository,
) : LibrarySyncParticipant {

    override suspend fun hasPending(): Boolean = repository.inFlightWrites.hasPending()

    override suspend fun sendPending(): Boolean = repository.inFlightWrites.awaitAll()
}
