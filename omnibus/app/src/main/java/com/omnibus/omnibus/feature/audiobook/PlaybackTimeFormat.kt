package com.omnibus.omnibus.feature.audiobook

import java.util.Locale
import kotlin.math.floor

/**
 * Formats the times the audiobook UI shows. Seconds are floored, and negative or non-finite input counts as 0. Digits
 * follow the locale.
 */
object PlaybackTimeFormat {

    /**
     * A clock time: `m:ss`, or `h:mm:ss` from an hour, or always with hours when [alwaysHours] is set.
     */
    fun clock(seconds: Double, alwaysHours: Boolean = false, locale: Locale = Locale.getDefault()): String {
        val total = wholeSeconds(seconds)
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val secs = total % 60
        return if (hours > 0 || alwaysHours) {
            String.format(locale, "%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(locale, "%d:%02d", minutes, secs)
        }
    }

    /**
     * A duration in its two largest non-zero units, such as "20h 25m", "20h", "25m" or "45s". Zero is "0m".
     */
    fun hoursMinutes(seconds: Double, locale: Locale = Locale.getDefault()): String {
        val total = wholeSeconds(seconds)
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val secs = total % 60
        return when {
            hours > 0 && minutes > 0 -> String.format(locale, "%dh %dm", hours, minutes)
            hours > 0 -> String.format(locale, "%dh", hours)
            minutes > 0 -> String.format(locale, "%dm", minutes)
            secs > 0 -> String.format(locale, "%ds", secs)
            else -> String.format(locale, "%dm", 0)
        }
    }

    private fun wholeSeconds(seconds: Double): Long =
        if (seconds.isFinite() && seconds > 0) floor(seconds).toLong() else 0L
}
