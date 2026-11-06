package com.omnibus.omnibus.data.quotecard

/**
 * One gradient the server offers for a book's cover.
 *
 * @property fromArgb The first stop, opaque ARGB.
 * @property toArgb The second stop, opaque ARGB.
 * @property lightInk Whether light ink keeps its contrast over both stops. False means dark ink.
 */
data class CoverPair(val fromArgb: Int, val toArgb: Int, val lightInk: Boolean)
