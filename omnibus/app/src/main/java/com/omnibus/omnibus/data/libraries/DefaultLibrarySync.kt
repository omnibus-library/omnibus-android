package com.omnibus.omnibus.data.libraries

import com.omnibus.omnibus.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withTimeoutOrNull

/** [LibrarySync] over every bound [LibrarySyncParticipant]. */
@Singleton
class DefaultLibrarySync @Inject constructor(
    private val participants: Set<@JvmSuppressWildcards LibrarySyncParticipant>,
    @param:ApplicationScope private val scope: CoroutineScope,
) : LibrarySync {

    override suspend fun hasUnsyncedChanges(): Boolean = participants.any { participant ->
        try {
            participant.hasPending()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            true
        }
    }

    override suspend fun syncActive(): LibrarySyncResult {
        // The sends run on the application scope, so the limit stops the wait and never a request midway.
        val sends = participants.map { participant ->
            scope.async {
                try {
                    participant.sendPending()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    false
                }
            }
        }
        val allSent = withTimeoutOrNull(SYNC_LIMIT) { sends.awaitAll().all { it } }
        return if (allSent == true) LibrarySyncResult.Synced else LibrarySyncResult.Failed
    }
}
