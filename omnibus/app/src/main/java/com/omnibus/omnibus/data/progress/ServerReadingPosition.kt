package com.omnibus.omnibus.data.progress

/**
 * The position another device, or this one, last pushed for a book, as the server holds it.
 *
 * @property cfi the EPUB CFI of the position, or null for a percent-only record.
 * @property percent the whole percentage through the book, or null when the server has none.
 * @property derivedCfi the server's CFI for a percent-only record, or null when it has none.
 * @property updatedAt when the server stored the record, in unix seconds. The fallback ordering clock.
 * @property clientUpdatedAt when the pushing device made the position, in unix seconds. Null from
 *   older servers.
 */
data class ServerReadingPosition(
    val cfi: String?,
    val percent: Int?,
    val derivedCfi: String?,
    val updatedAt: Long,
    val clientUpdatedAt: Long?,
) {
    /** The clock positions are ordered by: the device's time when the server has it, else the server's. */
    val orderingClock: Long get() = clientUpdatedAt ?: updatedAt
}
