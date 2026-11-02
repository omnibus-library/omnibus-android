package com.omnibus.omnibus.feature.reader

import com.omnibus.omnibus.feature.reader.ui.RelocateData

/**
 * Keeps reading forward from an opened position from lowering the stored percent (iOS #2699).
 * While the reader is at or after the landing, the percent written is the larger of the opened
 * percent and the page's own. Once they go back behind the landing, the page's percent is written
 * as-is for the rest of the open.
 *
 * @param openedPercent the percent of the position the open started from, or null for no floor.
 */
internal class PercentFloor(private val openedPercent: Int?) {

    /** The page's percent at the landing, set by the first relocate with a known total. */
    private var landing: Int? = null

    private var off = openedPercent == null

    /**
     * The percent to write for [data], or null when the page's locations aren't ready. Call for
     * every admitted relocate, in order.
     */
    fun percentFor(data: RelocateData): Int? {
        if (data.totalPages <= 0) return null
        if (off) return data.pct
        val landingPct = landing ?: data.pct.also { landing = it }
        if (data.pct < landingPct) {
            off = true
            return data.pct
        }
        return maxOf(openedPercent!!, data.pct)
    }
}
