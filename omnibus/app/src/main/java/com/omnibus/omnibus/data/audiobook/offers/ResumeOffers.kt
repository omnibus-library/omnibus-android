package com.omnibus.omnibus.data.audiobook.offers

import com.omnibus.omnibus.data.audiobook.position.LatePosition
import kotlinx.coroutines.flow.Flow

/**
 * The resume offers for the loaded book (#166): a device offer (a newer position on another device, I-RES-6) and an
 * ebook offer (a newer position in the ebook, I-RES-7). Each load starts with none. The commands run on the main
 * thread, do not suspend, and never throw.
 */
interface ResumeOffers {

    /** The offers that show now. The controller is read only when this is called, so the holder can be built early. */
    fun getOffersFlow(): Flow<VisibleOffers>

    /** Seeks to a device offer's position and forces a save (I-SAVE-3). The device offer and this load's ebook offer go. */
    fun acceptDeviceOffer(offer: LatePosition)

    /** Hides a device offer for the rest of this load (spec §3). */
    fun dismissDeviceOffer(offer: LatePosition)

    /** Seeks to an ebook offer's position, forces a save and remembers the offer as dismissed (P-SYNC-7, R7). */
    fun acceptEbookOffer(offer: EbookOffer)

    /** Remembers an ebook offer as dismissed for its book, until its source clock changes (R8). */
    fun dismissEbookOffer(offer: EbookOffer)
}

/** An ebook position offered for [bookUuid] at file [bookFileId]. [sourceClock] is the ebook position's clock (A2). */
data class EbookOffer(
    val bookUuid: String,
    val bookFileId: Long,
    val positionSeconds: Double,
    val sourceAhead: Boolean,
    val sourceClock: Long,
)

/** What a load remembers: the ebook offer it was given, and the device offer the listener dismissed. */
data class OfferMemory(
    val ebook: EbookOffer? = null,
    val dismissedDevice: LatePosition? = null,
)

/** The offers that show now. Priority between them, and the error, are the UI's job (P-SYNC-8). */
data class VisibleOffers(
    val device: LatePosition? = null,
    val ebook: EbookOffer? = null,
)
