package com.omnibus.omnibus.data.progress

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Pure rules for choosing between a stored position and the server's. Port of iOS `PositionSync`. */
object PositionSync {

    /**
     * How long an open waits on the server before showing the local position. A bound, not a
     * timeout: the read carries on.
     */
    val OPEN_DEADLINE: Duration = 1200.milliseconds

    /** Whether [value] is an EPUB CFI, once trimmed. */
    fun isEpubCfi(value: String?): Boolean = value?.trim()?.startsWith("epubcfi(") == true

    /** Whether a record with [cfi] and [percent] is a position at all: an EPUB CFI, or a percent above zero. */
    fun carriesPosition(cfi: String?, percent: Int?): Boolean = isEpubCfi(cfi) || (percent ?: 0) > 0

    /**
     * Whether [remote] is a position newer than [local]: it carries a position and its ordering clock
     * is strictly greater than the local one (0 when there is no local position).
     */
    fun isNewer(remote: ServerReadingPosition, local: ReadingPosition?): Boolean =
        carriesPosition(remote.cfi, remote.percent) && remote.orderingClock > (local?.clientUpdatedAt ?: 0L)
}
