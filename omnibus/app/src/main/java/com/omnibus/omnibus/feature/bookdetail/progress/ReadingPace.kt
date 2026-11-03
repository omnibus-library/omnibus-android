package com.omnibus.omnibus.feature.bookdetail.progress

import kotlin.math.roundToLong

/**
 * One reading or listening session of a book.
 *
 * @property startedAtMillis When the session started, in epoch milliseconds.
 * @property durationSeconds How long the session lasted, in seconds.
 * @property startFraction The place the session started, 0 to 1, or null when the server does not report it.
 * @property endFraction The place the session ended, 0 to 1, or null when the server does not report it.
 */
data class ReadingSession(
    val startedAtMillis: Long,
    val durationSeconds: Long,
    val startFraction: Float?,
    val endFraction: Float?,
)

/**
 * The pace from sessions that carry both fractions: percent of the book per hour of session time, or null when no
 * session has both fractions, the time or the progress is zero, or the total is not positive. A backwards session
 * adds nothing. The server does not send fractions yet, so this is null for today's data.
 */
fun pacePerHour(sessions: List<ReadingSession>): Float? {
    val measured = sessions.filter { it.startFraction != null && it.endFraction != null }
    val progress = measured.sumOf { (it.endFraction!! - it.startFraction!!).coerceAtLeast(0f).toDouble() }
    val hours = measured.sumOf { it.durationSeconds } / 3600.0
    if (measured.isEmpty() || progress <= 0.0 || hours <= 0.0) return null
    return (progress * 100.0 / hours).toFloat()
}

/**
 * The pace in percent of the book per hour: [pacePerHour] of [sessions] when it has one, else [fraction] spread over the
 * total session time of all [sessions]. Null when that total is not positive, or [fraction] is not a positive finite
 * number. This is the overload the progress line uses.
 */
fun pacePerHour(sessions: List<ReadingSession>, fraction: Float): Float? {
    pacePerHour(sessions)?.let { return it }
    if (!fraction.isFinite() || fraction <= 0f) return null
    val hours = sessions.sumOf { it.durationSeconds } / 3600.0
    return if (hours <= 0.0) null else (fraction * 100.0 / hours).toFloat()
}

/**
 * Seconds left to read at [pacePerHour] (percent of the book per hour) from [fraction], rounded to the second. Null when
 * the pace is not a positive finite number, or the fraction is not strictly between 0 and 1: a book at either end has
 * no time left to give.
 */
fun timeLeftSeconds(fraction: Float, pacePerHour: Float): Long? {
    if (!pacePerHour.isFinite() || pacePerHour <= 0f) return null
    if (!fraction.isFinite() || fraction <= 0f || fraction >= 1f) return null
    return ((1.0 - fraction) * 100.0 / pacePerHour * 3600.0).roundToLong()
}
