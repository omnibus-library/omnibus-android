package com.omnibus.omnibus.feature.reader.ui.curl

/**
 * Whether the snapshots still show the page. Mirrors iOS `pageChanged(byRelocate:)`: a relocate to the position the
 * snapshots were taken at does not stale them, unless a repaint came first.
 */
internal class CurlStaleness {
    var stale = true
        private set
    private var capturedCfi: String? = null
    private var acceptSamePosition = false

    /** Returns true when the snapshots are now stale and a recapture should be scheduled. */
    fun pageChanged(byRelocate: Boolean, cfi: String?, busy: Boolean): Boolean {
        if (busy && byRelocate) return false // the host's own turn landing
        if (!byRelocate) {
            acceptSamePosition = true
        } else if (!acceptSamePosition && !stale && cfi == capturedCfi) {
            return false
        } else {
            acceptSamePosition = false
        }
        stale = true
        return true
    }

    /** A capture that started at [cfiAtStart] finished with the page at [cfiNow]. */
    fun captured(cfiAtStart: String?, cfiNow: String?) {
        stale = cfiAtStart != cfiNow
        capturedCfi = cfiNow
    }

    fun markStale() {
        stale = true
    }
}
