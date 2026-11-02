package com.omnibus.omnibus.data.progress

/**
 * This device's reading position in one book. It is stored locally and pushed to the server.
 *
 * @property cfi the EPUB CFI of the position. Null only for a percent-only position.
 * @property percent the whole percentage through the book, 0 to 100, or null when unknown.
 * @property clientUpdatedAt when the position was made, in unix seconds on the device clock. It is
 *   the ordering clock, matching the wire.
 */
data class ReadingPosition(
    val bookUuid: String,
    val cfi: String?,
    val percent: Int?,
    val clientUpdatedAt: Long,
)
