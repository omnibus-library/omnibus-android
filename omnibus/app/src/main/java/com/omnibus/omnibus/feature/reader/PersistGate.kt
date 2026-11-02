package com.omnibus.omnibus.feature.reader

import com.omnibus.omnibus.feature.reader.ui.RelocateData

/**
 * Holds back persistence while a percent-only position is open, until the reader moves off the
 * landing themselves (iOS `holdsStoredPosition`, #2446). A seek's own landing and the boot landing
 * never release it, and neither does an echo.
 *
 * @param held whether the open starts held, which is the case for a percent-only opening.
 */
internal class PersistGate(held: Boolean) {

    /** Whether nothing may be persisted yet. Only a movement by the reader clears it. */
    var held: Boolean = held
        private set

    private var seekPending = false
    private var previousCfi: String? = null

    /** Called when a seek is sent to the page, so the relocate that settles it is not a movement. */
    fun onSeekSent() {
        seekPending = true
    }

    /** The reader chose to move (an accepted offer), so the hold lifts. */
    fun release() {
        held = false
    }

    /** Whether [data] may be persisted. Call for every relocate, in order. */
    fun admit(data: RelocateData): Boolean {
        if (!held) return true
        val wasSeekPending = seekPending
        if (data.settledSeek != null) seekPending = false
        val moved = data.isMovement && !wasSeekPending && previousCfi != null &&
            data.cfi != null && data.cfi != previousCfi
        previousCfi = data.cfi ?: previousCfi
        if (moved) held = false
        return moved
    }
}
