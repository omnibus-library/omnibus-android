package com.omnibus.omnibus.feature.reader.ui.curl

/**
 * Snapshots of the page in front (0) and its neighbours (1 forward, -1 back). Immutable, as iOS `CurlPages`.
 */
internal data class CurlRing<T : Any>(val current: T? = null, val next: T? = null, val previous: T? = null) {
    /** The image in slot [offset], or null when the slot is empty or [offset] is not -1, 0 or 1. */
    operator fun get(offset: Int): T? = when (offset) {
        0 -> current
        1 -> next
        -1 -> previous
        else -> null
    }

    /** A copy with [image] in slot [offset]. */
    fun with(offset: Int, image: T?): CurlRing<T> = when (offset) {
        0 -> copy(current = image)
        1 -> copy(next = image)
        -1 -> copy(previous = image)
        else -> throw IllegalArgumentException("No slot $offset in a ring of three")
    }

    /** Every image held, in front, forward, back order. */
    fun all(): List<T> = listOfNotNull(current, next, previous)

    /** The ring once a turn of [dir] landed on [destination], or on the neighbour in [dir] when there is none. */
    fun landed(dir: Int, destination: T? = null): CurlRing<T> =
        CurlRing<T>().with(0, destination ?: this[dir]).with(-dir, current)

    /** The images this ring holds that [other] does not, compared by identity: the ones to recycle on a switch. */
    fun dropped(other: CurlRing<T>): List<T> = all().filter { mine -> other.all().none { it === mine } }
}
