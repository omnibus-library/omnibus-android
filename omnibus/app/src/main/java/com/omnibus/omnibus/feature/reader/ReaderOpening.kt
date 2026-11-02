package com.omnibus.omnibus.feature.reader

import com.omnibus.omnibus.data.progress.PositionSync

/** Where the page boots. */
sealed interface ReaderStart {
    /** The start of the book. */
    data object Beginning : ReaderStart

    /** The position with EPUB CFI [cfi]. */
    data class AtCfi(val cfi: String) : ReaderStart

    /**
     * A percent-only position: [fraction] of the way through the book (0.0 to 1.0). The page boots at
     * [bootCfi] when the server gave one, otherwise at the cover and then seeks to [fraction] once ready.
     */
    data class AtPercent(val fraction: Double, val bootCfi: String?) : ReaderStart
}

/** The CFI the page boots at, or null for the beginning or a percent-only position without one. */
val ReaderStart.bootCfi: String?
    get() = when (this) {
        ReaderStart.Beginning -> null
        is ReaderStart.AtCfi -> cfi
        is ReaderStart.AtPercent -> bootCfi
    }

/**
 * Where a stored or server position opens. Port of iOS `ReaderController.openingCFI` and
 * `storesPositionWithoutCFI`: an own CFI wins, then a percent (booted at [derivedCfi] when it is a
 * CFI), else the beginning.
 */
fun readerStartOf(cfi: String?, derivedCfi: String?, percent: Int?): ReaderStart = when {
    PositionSync.isEpubCfi(cfi) -> ReaderStart.AtCfi(cfi!!.trim())
    (percent ?: 0) > 0 -> ReaderStart.AtPercent(
        fraction = percent!! / 100.0,
        bootCfi = derivedCfi?.trim()?.takeIf { PositionSync.isEpubCfi(it) },
    )
    else -> ReaderStart.Beginning
}

/** A newer position from another device that the reader can move to. Offered, never applied on its own. */
data class SyncOffer(val target: ReaderStart)

/** Whether the opening position is known yet. The page waits for it, so it boots at the right place. */
sealed interface ReaderOpening {
    /** The stored position is still being read. */
    data object Resolving : ReaderOpening

    /** The position the page boots at is [start]. */
    data class Resolved(val start: ReaderStart) : ReaderOpening
}
