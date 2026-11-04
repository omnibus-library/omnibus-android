package com.omnibus.omnibus.data.audiobook.offers

import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.position.LatePosition

/** How far ahead (s) a device offer must be of the live position to show (I-RES-6, iOS `seconds > position + 1`). */
internal const val DEVICE_OFFER_LEAD_SECONDS = 1.0

/**
 * Which offers show for [playback] (I-RES-6, P-SYNC-6, P-SYNC-7). Nothing shows while the controller is loading or holds
 * no book. The device offer is checked against the live position, so playing or seeking past it hides it. The error is
 * not considered here: priority is the UI's job (P-SYNC-8).
 */
internal fun visibleOffers(playback: PlaybackState, late: LatePosition?, memory: OfferMemory): VisibleOffers {
    val book = playback.book ?: return VisibleOffers()
    if (playback.isLoading) return VisibleOffers()
    val device = late?.takeIf {
        it.bookUuid == book.bookUuid && it.bookFileId == book.bookFileId &&
            it != memory.dismissedDevice &&
            it.positionSeconds > playback.positionSeconds + DEVICE_OFFER_LEAD_SECONDS
    }
    val ebook = memory.ebook?.takeIf { it.bookUuid == book.bookUuid && it.bookFileId == book.bookFileId }
    return VisibleOffers(device = device, ebook = ebook)
}
