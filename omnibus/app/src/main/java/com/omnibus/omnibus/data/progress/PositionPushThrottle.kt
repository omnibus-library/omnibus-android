package com.omnibus.omnibus.data.progress

/**
 * Decides when a reading position may be pushed to the server, ported from iOS
 * `PositionPushThrottle`. At most one unforced push goes out per [intervalMillis] window.
 *
 * A forced decision always pushes and re-arms the window. The first decision of an instance is
 * suppressed when it is not forced, and that suppression does not arm the window, so the next
 * unforced decision can push. Any first decision, forced or not, clears the suppression.
 *
 * Not thread-safe: use it from one thread (the main thread) only.
 *
 * @property intervalMillis the window after a push during which unforced pushes are held back.
 */
class PositionPushThrottle(
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    suppressFirst: Boolean = true,
) {
    /** When the last push was armed, or null when none has been. */
    private var lastPushMillis: Long? = null

    private var suppressNext = suppressFirst

    /**
     * Returns whether a push should go out now. A push arms the window from [nowMillis].
     */
    fun shouldPush(force: Boolean, nowMillis: Long): Boolean {
        val wasSuppressed = suppressNext
        suppressNext = false
        if (wasSuppressed && !force) return false
        val last = lastPushMillis
        if (!force && last != null && nowMillis - last <= intervalMillis) return false
        lastPushMillis = nowMillis
        return true
    }

    companion object {
        /** The window iOS uses between unforced pushes. */
        const val DEFAULT_INTERVAL_MILLIS = 4_000L
    }
}
