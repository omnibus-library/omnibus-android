package com.omnibus.omnibus.data.audiobook.rate

import com.omnibus.omnibus.data.audiobook.rate.local.PlaybackRateCache
import com.omnibus.omnibus.data.libraries.LibrarySyncParticipant
import javax.inject.Inject

/**
 * Sends the playback rates waiting in the cache (C10). Nothing pending is a success, even offline; a rate that is kept
 * or rejected is a failure, and it stays pending under its library.
 */
class PlaybackRateSyncParticipant @Inject constructor(
    private val store: DefaultPlaybackRateStore,
    private val cache: PlaybackRateCache,
) : LibrarySyncParticipant {

    override suspend fun hasPending(): Boolean = cache.allPending().isNotEmpty()

    override suspend fun sendPending(): Boolean = !hasPending() || store.flush()
}
