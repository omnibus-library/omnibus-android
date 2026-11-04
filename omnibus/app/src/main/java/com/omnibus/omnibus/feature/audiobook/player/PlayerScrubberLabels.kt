package com.omnibus.omnibus.feature.audiobook.player

import com.omnibus.omnibus.feature.audiobook.PlaybackTimeFormat

/** The scrubber's three labels: elapsed in the span, the book left (with chapters only), and the span left. */
internal data class ScrubberTimes(val elapsed: String, val bookLeft: String?, val spanLeft: String)

/**
 * The scrubber labels for a span position. Positions are book time and are not divided by the rate; only the "left"
 * values are, so they read as listening time (I-TL-5). The position is clamped to the span, and a bad rate counts as 1x.
 */
internal fun scrubberTimes(
    spanPosition: Double,
    spanLength: Double,
    spanStart: Double,
    duration: Double,
    rate: Double,
    hasChapters: Boolean,
): ScrubberTimes {
    val length = maxOf(0.0, spanLength)
    val position = spanPosition.coerceIn(0.0, length)
    val safeRate = if (rate > 0 && rate.isFinite()) rate else 1.0
    return ScrubberTimes(
        elapsed = PlaybackTimeFormat.clock(position),
        bookLeft = if (hasChapters) {
            PlaybackTimeFormat.hoursMinutes(maxOf(0.0, duration - (spanStart + position)) / safeRate)
        } else {
            null
        },
        spanLeft = PlaybackTimeFormat.clock(maxOf(0.0, length - position) / safeRate),
    )
}
