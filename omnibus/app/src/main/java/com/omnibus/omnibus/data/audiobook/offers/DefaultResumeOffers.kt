package com.omnibus.omnibus.data.audiobook.offers

import com.omnibus.omnibus.data.audiobook.crossformat.CrossFormatAudioResult
import com.omnibus.omnibus.data.audiobook.crossformat.CrossFormatRepository
import com.omnibus.omnibus.data.audiobook.crossformat.CrossFormatRules
import com.omnibus.omnibus.data.audiobook.playback.AudiobookController
import com.omnibus.omnibus.data.audiobook.playback.PlaybackListener
import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.PlayingBook
import com.omnibus.omnibus.data.audiobook.playback.UnloadReason
import com.omnibus.omnibus.data.audiobook.position.LatePosition
import com.omnibus.omnibus.data.audiobook.position.ListeningPositionSync
import com.omnibus.omnibus.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the resume offers of the loaded book (#166). Its memory is per load: [onSettled] and [onUnload] reset it, and a
 * same-book reload has no unload, so its settle resets it instead. The ebook answer is fetched once per settle in the
 * background and dropped when another load has settled, the book was unloaded, or the controller no longer holds the
 * book's settled file (the epoch rule).
 *
 * The controller is a [dagger.Lazy]: it injects the listener set, and this class is in it. Nothing touches it during
 * construction; it is read only in callbacks, commands and [getOffersFlow], as [DefaultListeningPositionSync] does.
 *
 * Commands run on the main thread. Their saves and store writes run on [scope] (the application scope), so closing the
 * player mid-save does not cancel them. A seek always comes before its save, because seeking updates the state the
 * save reads (I-SAVE-3).
 */
@Singleton
class DefaultResumeOffers @Inject constructor(
    private val controller: dagger.Lazy<AudiobookController>,
    private val sync: ListeningPositionSync,
    private val repository: CrossFormatRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) : ResumeOffers, PlaybackListener {

    private val memory = MutableStateFlow(OfferMemory())

    /** Changes at each settle and each unload, so an answer from an earlier load is dropped. */
    private var epoch = 0

    private var fetchJob: Job? = null

    override fun getOffersFlow(): Flow<VisibleOffers> =
        combine(controller.get().state, sync.lateServerPosition, memory, ::visibleOffers).distinctUntilChanged()

    override fun onSettled(state: PlaybackState, startSeconds: Double) {
        val load = ++epoch
        memory.value = OfferMemory()
        fetchJob?.cancel()
        val book = state.book ?: return
        fetchJob = scope.launch { fetch(load, book) }
    }

    override suspend fun onUnload(state: PlaybackState, reason: UnloadReason) {
        epoch++
        fetchJob?.cancel()
        memory.value = OfferMemory()
    }

    /**
     * Reads the ebook candidate for [book] and offers it, or follows it (R4 to R8). A follow candidate is taken at once
     * with no banner, no stored clock and no save (R5, iOS).
     */
    private suspend fun fetch(load: Int, book: PlayingBook) {
        val result = repository.audioResume(book.bookUuid)
        if (!isCurrent(load, book)) return
        if (result !is CrossFormatAudioResult.Candidate) return
        if (!CrossFormatRules.fileMatches(book.bookFileId, result.bookFileId)) return
        if (result.follow) {
            controller.get().seekTo(result.positionSeconds)
            return
        }
        val dismissed = repository.dismissedClock(book.bookUuid)
        if (!isCurrent(load, book)) return
        if (!CrossFormatRules.shouldOffer(result.sourceClock, dismissed)) return
        memory.update {
            it.copy(
                ebook = EbookOffer(
                    bookUuid = book.bookUuid,
                    bookFileId = book.bookFileId,
                    positionSeconds = result.positionSeconds,
                    sourceAhead = result.sourceAhead,
                    sourceClock = result.sourceClock,
                ),
            )
        }
    }

    /** The load [load] is still current and the controller still holds [book]'s file, settled. */
    private fun isCurrent(load: Int, book: PlayingBook): Boolean = load == epoch && holds(book.bookUuid, book.bookFileId)

    /** Whether the controller holds [bookUuid] at [bookFileId] and is not loading. */
    private fun holds(bookUuid: String, bookFileId: Long): Boolean {
        val state = controller.get().state.value
        val book = state.book ?: return false
        return !state.isLoading && book.bookUuid == bookUuid && book.bookFileId == bookFileId
    }

    override fun acceptDeviceOffer(offer: LatePosition) {
        memory.update { it.copy(dismissedDevice = offer, ebook = null) }
        if (holds(offer.bookUuid, offer.bookFileId)) {
            controller.get().seekTo(offer.positionSeconds)
            scope.launch { sync.forceSave() }
        }
    }

    override fun dismissDeviceOffer(offer: LatePosition) {
        memory.update { it.copy(dismissedDevice = offer) }
    }

    override fun acceptEbookOffer(offer: EbookOffer) {
        clearEbook(offer)
        if (holds(offer.bookUuid, offer.bookFileId)) {
            controller.get().seekTo(offer.positionSeconds)
            scope.launch { sync.forceSave() }
        }
        scope.launch { repository.storeDismissedClock(offer.bookUuid, offer.sourceClock) }
    }

    override fun dismissEbookOffer(offer: EbookOffer) {
        clearEbook(offer)
        scope.launch { repository.storeDismissedClock(offer.bookUuid, offer.sourceClock) }
    }

    private fun clearEbook(offer: EbookOffer) {
        memory.update { if (it.ebook == offer) it.copy(ebook = null) else it }
    }
}
