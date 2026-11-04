package com.omnibus.omnibus.data.audiobook.playback

/**
 * When the now-playing cover may be fetched again after a failure, a port of iOS `ArtworkRetrySchedule` on millisecond
 * timestamps (I-SYS-4). A failure waits 15 s, then doubles up to 240 s. Play lifts the wait, and a returning connection
 * lifts only a first failure's wait. A new book starts clean.
 */
internal class ArtworkRetrySchedule {

    private var uuid: String? = null
    private var failures = 0
    private var retryAtMillis: Long? = null

    /** Notes the book now shown. A different book starts from zero failures. */
    fun noteCurrent(bookUuid: String) {
        if (bookUuid == uuid) return
        uuid = bookUuid
        failures = 0
        retryAtMillis = null
    }

    /** Playback resumed: the next fetch may happen at once. */
    fun noteResumed() {
        retryAtMillis = null
    }

    /** True when a fetch may happen at [nowMillis]. */
    fun fetchIsDue(nowMillis: Long): Boolean = retryAtMillis?.let { nowMillis >= it } ?: true

    /** Records a failed fetch at [nowMillis], which sets the next wait. */
    fun noteFailure(nowMillis: Long) {
        failures++
        retryAtMillis = nowMillis + delayMillis(failures)
    }

    /** The connection came back: a first failure's wait is lifted, a repeated one keeps waiting. */
    fun noteConnectionReturned() {
        if (failures == 1) retryAtMillis = null
    }

    companion object {
        /** The wait after the first failure. */
        const val FIRST_DELAY_MILLIS = 15_000L

        /** The longest wait between fetches. */
        const val LONGEST_DELAY_MILLIS = 240_000L

        /** The wait after [failures] failures: 15 s, doubling, capped at 240 s. */
        fun delayMillis(failures: Int): Long =
            minOf(FIRST_DELAY_MILLIS * (1L shl minOf(failures - 1, 8)), LONGEST_DELAY_MILLIS)
    }
}
