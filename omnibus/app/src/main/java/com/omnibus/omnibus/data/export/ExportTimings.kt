package com.omnibus.omnibus.data.export

/**
 * Pacing for the Kindle send poll and the KEPUB conversion retries. The defaults match iOS: poll every
 * 700 ms for at most 150 s, and make up to three KEPUB requests while the server is still converting.
 *
 * @property pollIntervalMillis The delay before each send-status poll.
 * @property pollBudgetMillis The longest a send is polled before it is reported as unconfirmed.
 * @property kepubAttempts The most KEPUB requests made when the response times out before its headers.
 */
data class ExportTimings(
    val pollIntervalMillis: Long = 700,
    val pollBudgetMillis: Long = 150_000,
    val kepubAttempts: Int = 3,
)
