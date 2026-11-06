package com.omnibus.omnibus.data.quotecard

import java.io.File

/**
 * One gradient the server offers for a book's cover.
 *
 * @property fromArgb The first stop, opaque ARGB.
 * @property toArgb The second stop, opaque ARGB.
 * @property lightInk Whether light ink keeps its contrast over both stops. False means dark ink.
 */
data class CoverPair(val fromArgb: Int, val toArgb: Int, val lightInk: Boolean)

/** Where a quote card gets what it needs from outside the screen. */
interface QuoteCardRepository {

    /** Writes [png] into a fresh export folder. Null when it can't be written. Never throws. */
    suspend fun saveCard(png: ByteArray): File?
}
