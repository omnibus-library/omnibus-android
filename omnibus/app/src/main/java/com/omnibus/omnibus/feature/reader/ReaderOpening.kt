package com.omnibus.omnibus.feature.reader

/** Where the page boots. */
sealed interface ReaderStart {
    /** The start of the book. */
    data object Beginning : ReaderStart

    /** The position with EPUB CFI [cfi]. */
    data class AtCfi(val cfi: String) : ReaderStart
}

/** The CFI the page boots at, or null for the beginning. */
val ReaderStart.bootCfi: String?
    get() = when (this) {
        ReaderStart.Beginning -> null
        is ReaderStart.AtCfi -> cfi
    }

/** Whether the opening position is known yet. The page waits for it, so it boots at the right place. */
sealed interface ReaderOpening {
    /** The stored position is still being read. */
    data object Resolving : ReaderOpening

    /** The position the page boots at is [start]. */
    data class Resolved(val start: ReaderStart) : ReaderOpening
}
