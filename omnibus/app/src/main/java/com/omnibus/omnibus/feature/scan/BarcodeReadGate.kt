package com.omnibus.omnibus.feature.scan

import com.omnibus.omnibus.data.scan.isBookEan13

/** How long a barcode must go unseen before it counts as having left the frame. ML Kit drops single frames. */
internal const val GRACE_MILLIS = 1_000L

/** What one barcode in a frame turned into. */
sealed interface GateEvent {
    /** A book barcode that arrived in the frame, to look up. */
    data class Lookup(val isbn: String) : GateEvent

    /** A barcode that isn't a book's ISBN. [announce] is true only the first time this value is seen. */
    data class Rejected(val value: String, val announce: Boolean) : GateEvent
}

/**
 * Turns the barcodes in each analysed frame into lookups and rejections, once per arrival in the frame. A barcode that
 * stays in frame, or drops out for less than [graceMillis], doesn't count as arriving again. Not thread-safe; call from
 * the main thread.
 */
internal class BarcodeReadGate(private val graceMillis: Long = GRACE_MILLIS) {
    private val lastSeen = mutableMapOf<String, Long>()
    private val announced = mutableSetOf<String>()

    /** The events for the barcodes in one frame analysed at [nowMillis]. A barcode missing from the frame keeps its last sighting. */
    fun onFrame(values: List<String>, nowMillis: Long): List<GateEvent> = values.distinct().mapNotNull { value ->
        val last = lastSeen[value]
        lastSeen[value] = nowMillis
        val arrived = last == null || nowMillis - last >= graceMillis
        when {
            !arrived -> null
            isBookEan13(value) -> GateEvent.Lookup(value)
            else -> GateEvent.Rejected(value, announce = announced.add(value))
        }
    }
}
