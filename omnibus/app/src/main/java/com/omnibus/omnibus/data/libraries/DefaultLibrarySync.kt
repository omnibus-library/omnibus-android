package com.omnibus.omnibus.data.libraries

import com.omnibus.omnibus.data.audiobook.position.AudioPosition
import com.omnibus.omnibus.data.audiobook.position.DefaultListeningProgressRepository
import com.omnibus.omnibus.data.audiobook.position.ListeningSessionReport
import com.omnibus.omnibus.data.audiobook.position.local.ListeningPositionDao
import com.omnibus.omnibus.data.audiobook.position.local.ListeningPositionRow
import com.omnibus.omnibus.data.audiobook.position.local.ListeningSessionRow
import com.omnibus.omnibus.data.audiobook.rate.local.PlaybackRateDao
import com.omnibus.omnibus.data.configuration.ServerAddress
import com.omnibus.omnibus.data.libraries.local.LibraryTokenStore
import com.omnibus.omnibus.data.libraries.remote.InactiveLibraryApi
import com.omnibus.omnibus.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** [LibrarySync] over every bound [LibrarySyncParticipant], and over one inactive library's own rows. */
@Singleton
class DefaultLibrarySync @Inject constructor(
    private val participants: Set<@JvmSuppressWildcards LibrarySyncParticipant>,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val libraries: LibrariesRepository,
    private val tokens: LibraryTokenStore,
    private val rows: InactiveLibraryRows,
    private val api: InactiveLibraryApi,
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

    override suspend fun syncInactive(libraryId: String): LibrarySyncResult {
        // As syncActive: the sends run on the application scope, so the limit stops the wait and never a request midway.
        val sent = scope.async {
            try {
                val library = libraries.getLibrariesFlow().first().firstOrNull { it.id == libraryId }
                    ?: return@async false
                val baseUrl = ServerAddress.normalize(library.address)?.toHttpUrlOrNull() ?: return@async false
                val token = tokens.getTokenFlow(libraryId).first()?.takeIf { it.isNotBlank() }
                    ?: return@async false
                sendInactivePending(libraryId, baseUrl, token)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                false
            }
        }
        val allSent = withTimeoutOrNull(SYNC_LIMIT) { sent.await() }
        return if (allSent == true) LibrarySyncResult.Synced else LibrarySyncResult.Failed
    }

    /** Sends the audio rows and the rates of [libraryId] side by side. Each side is guarded, so one never cancels the other. */
    private suspend fun sendInactivePending(libraryId: String, baseUrl: HttpUrl, token: String): Boolean =
        coroutineScope {
            val audio = async { guarded { sendAudio(libraryId, baseUrl, token) } }
            val rates = async { guarded { sendRates(libraryId, baseUrl, token) } }
            val audioSent = audio.await()
            val ratesSent = rates.await()
            audioSent && ratesSent
        }

    /** Positions in turn, then queued sessions in batches, stopping at the first refused batch (as `flush` does). */
    private suspend fun sendAudio(libraryId: String, baseUrl: HttpUrl, token: String): Boolean {
        var allSent = true
        for (position in rows.pendingPositions(libraryId)) {
            if (api.sendPosition(baseUrl, token, position)) {
                rows.clearPosition(libraryId, position)
            } else {
                allSent = false
            }
        }
        for (batch in rows.sessions(libraryId).chunked(DefaultListeningProgressRepository.SESSION_BATCH_CAP)) {
            if (!api.sendSessions(baseUrl, token, batch)) return false
            rows.removeSessions(libraryId, batch.mapTo(mutableSetOf()) { it.clientId })
        }
        return allSent
    }

    private suspend fun sendRates(libraryId: String, baseUrl: HttpUrl, token: String): Boolean {
        var allSent = true
        for ((bookUuid, rate) in rows.pendingRates(libraryId)) {
            if (api.putRate(baseUrl, token, bookUuid, rate)) {
                rows.clearRate(libraryId, bookUuid, rate)
            } else {
                allSent = false
            }
        }
        return allSent
    }

    private suspend fun guarded(send: suspend () -> Boolean): Boolean = try {
        send()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        false
    }
}

/** One library's pending rows, by its id, whether or not it is active (C10 for an inactive library). */
interface InactiveLibraryRows {

    suspend fun pendingPositions(libraryId: String): List<AudioPosition>

    /** In queue order. */
    suspend fun sessions(libraryId: String): List<ListeningSessionReport>

    /** The pending rate of each book, by book uuid. */
    suspend fun pendingRates(libraryId: String): Map<String, Double>

    /** Clears [position]'s pending value only while it still equals [position]. */
    suspend fun clearPosition(libraryId: String, position: AudioPosition)

    suspend fun removeSessions(libraryId: String, clientIds: Set<String>)

    /** Clears the pending rate of [bookUuid] only while it still equals [rate]. */
    suspend fun clearRate(libraryId: String, bookUuid: String, rate: Double)
}

/** [InactiveLibraryRows] over the per-library queries of the existing DAOs. */
class RoomInactiveLibraryRows @Inject constructor(
    private val positions: ListeningPositionDao,
    private val rates: PlaybackRateDao,
) : InactiveLibraryRows {

    override suspend fun pendingPositions(libraryId: String): List<AudioPosition> =
        positions.pendingPositions(libraryId).map { it.toPosition() }

    override suspend fun sessions(libraryId: String): List<ListeningSessionReport> =
        positions.sessions(libraryId).map { it.toReport() }

    override suspend fun pendingRates(libraryId: String): Map<String, Double> =
        rates.pendingRates(libraryId).associate { it.bookUuid to it.pendingRate }

    override suspend fun clearPosition(libraryId: String, position: AudioPosition) {
        positions.clearPendingIf(
            libraryId,
            position.bookUuid,
            position.positionSeconds,
            position.bookFileId,
            position.clientUpdatedAt,
        )
    }

    override suspend fun removeSessions(libraryId: String, clientIds: Set<String>) {
        positions.removeSessions(libraryId, clientIds)
    }

    override suspend fun clearRate(libraryId: String, bookUuid: String, rate: Double) {
        rates.clearPendingIf(libraryId, bookUuid, rate)
    }

    private fun ListeningPositionRow.toPosition() = AudioPosition(bookUuid, positionSeconds, bookFileId, clientUpdatedAt)

    private fun ListeningSessionRow.toReport() = ListeningSessionReport(
        bookUuid = bookUuid,
        startedAt = startedAt,
        endedAt = endedAt,
        progressUnits = progressUnits,
        clientId = clientId,
        utcOffsetMinutes = utcOffsetMinutes,
        timeZone = timeZone,
    )
}
