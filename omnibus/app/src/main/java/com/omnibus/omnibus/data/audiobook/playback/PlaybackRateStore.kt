package com.omnibus.omnibus.data.audiobook.playback

/**
 * The playback speed kept for each book. Optional: bind it with `@BindsOptionalOf`. With no binding the rate is 1.0.
 */
interface PlaybackRateStore {

    /**
     * The saved rate for [bookUuid], or null when unknown. Read at each load, and applied when it arrives without holding
     * back the start.
     */
    suspend fun rate(bookUuid: String): Double?

    /** Saves [rate] for [bookUuid]. Never throws. */
    suspend fun save(bookUuid: String, rate: Double)
}
