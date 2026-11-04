package com.omnibus.omnibus.feature.audiobook.player.offers

import com.omnibus.omnibus.data.audiobook.playback.PlaybackError

/** The one banner the player's slot shows at a time (P-SYNC-8). */
sealed interface PlayerBanner {

    /** The playback error, with Retry. */
    data class Error(val error: PlaybackError) : PlayerBanner

    /** The device offer, at [seconds] in book time. */
    data class DeviceOffer(val seconds: Double) : PlayerBanner

    /** The ebook offer, at [seconds] in book time. [sourceAhead] is false for a re-read. */
    data class EbookOffer(val seconds: Double, val sourceAhead: Boolean) : PlayerBanner
}

/**
 * The banner to show (P-SYNC-8): the error first, then the device offer, then the ebook offer, else nothing. A lower
 * banner is held back, and shows once the higher one goes.
 */
internal fun playerBanner(error: PlaybackError?, offers: ResumeOffersUIState): PlayerBanner? = when {
    error != null -> PlayerBanner.Error(error)
    offers.deviceSeconds != null -> PlayerBanner.DeviceOffer(offers.deviceSeconds)
    offers.ebook != null -> PlayerBanner.EbookOffer(offers.ebook.seconds, offers.ebook.sourceAhead)
    else -> null
}
