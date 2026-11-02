package com.omnibus.omnibus.feature.bookdetail.format

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

/**
 * Date, duration and timestamp text for the Book Detail page. Pure JVM: every helper takes the locale, time zone
 * and "now" it needs, so nothing reads the device defaults. Instants are epoch milliseconds.
 */
object BookDetailFormat {

    private val EPOCH = Regex("""^(-?)(\d{1,19})(?:\.(\d+))?$""")
    private val ISO = Regex("""^(\d{4})-(\d{2})-(\d{2})(?:[Tt ](\d{2}):(\d{2})(?::(\d{2})(?:\.(\d+))?)?)?([Zz]|[+-]\d{2}(?::?\d{2})?)?$""")

    /**
     * The month and day, such as "Oct 29". The year is added when [millis] falls in another calendar year than
     * [nowMillis], both read in [timeZone].
     */
    fun shortDate(millis: Long, nowMillis: Long, locale: Locale, timeZone: TimeZone): String =
        format(millis, locale, timeZone, if (sameYear(millis, nowMillis, timeZone)) "MMM d" else "MMM d, yyyy")

    /** [shortDate] with the full month name, such as "October 29". */
    fun longDate(millis: Long, nowMillis: Long, locale: Locale, timeZone: TimeZone): String =
        format(millis, locale, timeZone, if (sameYear(millis, nowMillis, timeZone)) "MMMM d" else "MMMM d, yyyy")

    /**
     * A 24-hour time, with a day word before it: [today] when [millis] is on the same calendar day as [nowMillis],
     * [yesterday] for the day before, otherwise the [longDate]. Days are compared by calendar fields, so a DST day
     * of 23 or 25 hours and midnight boundaries are handled.
     */
    fun relativeDateTime(
        millis: Long,
        nowMillis: Long,
        locale: Locale,
        timeZone: TimeZone,
        today: String = "Today",
        yesterday: String = "Yesterday",
    ): String {
        val then = GregorianCalendar(timeZone, locale).apply { timeInMillis = millis }
        val now = GregorianCalendar(timeZone, locale).apply { timeInMillis = nowMillis }
        val dayBefore = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -1) }
        val time = format(millis, locale, timeZone, "HH:mm")
        return when (dayKey(then)) {
            dayKey(now) -> "$today, $time"
            dayKey(dayBefore) -> "$yesterday, $time"
            else -> "${longDate(millis, nowMillis, locale, timeZone)}, $time"
        }
    }

    /**
     * A duration as "3 h 04 m", "48 m" or [underAMinute]. Negative values count as zero, and whole minutes are
     * floored. Digits are ASCII and the output has no grouping.
     */
    fun hoursMinutes(seconds: Long, underAMinute: String = "under a minute"): String {
        val total = seconds.coerceAtLeast(0)
        val hours = total / 3_600
        val minutes = (total % 3_600) / 60
        return when {
            hours > 0 -> "$hours h ${minutes.toString().padStart(2, '0')} m"
            minutes > 0 -> "$minutes m"
            else -> underAMinute
        }
    }

    /**
     * The epoch milliseconds of a server timestamp, or null. Accepts integer or decimal epoch seconds (the server's
     * `updated_at` and `finished_at`) and ISO-8601 dates and times: `YYYY-MM-DD`, `YYYY-MM-DDTHH:MM[:SS[.f]]` with
     * `T`, `t` or a space, and an optional `Z`, `±HH:MM`, `±HHMM` or `±HH` zone. A missing zone is UTC.
     *
     * Blank input and anything malformed or out of range give null. Years 0000 and above 9999 are rejected, and
     * the function never throws.
     */
    fun parseServerTimestamp(raw: String?): Long? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        return try {
            parseEpoch(text) ?: parseIso(text)
        } catch (e: ArithmeticException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun format(millis: Long, locale: Locale, timeZone: TimeZone, pattern: String): String =
        SimpleDateFormat(pattern, locale).apply { this.timeZone = timeZone }.format(Date(millis))

    private fun sameYear(millis: Long, nowMillis: Long, timeZone: TimeZone): Boolean =
        yearOf(millis, timeZone) == yearOf(nowMillis, timeZone)

    private fun yearOf(millis: Long, timeZone: TimeZone): Int =
        GregorianCalendar(timeZone).apply { timeInMillis = millis }.get(Calendar.YEAR)

    /** Year and day of year together, so two calendars on different days never compare equal. */
    private fun dayKey(calendar: Calendar): Int =
        calendar.get(Calendar.YEAR) * 1_000 + calendar.get(Calendar.DAY_OF_YEAR)

    private fun parseEpoch(text: String): Long? {
        val match = EPOCH.matchEntire(text) ?: return null
        val sign = if (match.groupValues[1] == "-") -1L else 1L
        // At most 19 digits; a larger value throws NumberFormatException, which is caught as null.
        val seconds = match.groupValues[2].toLong()
        val fraction = match.groupValues[3].take(3).padEnd(3, '0').toLong()
        return sign * Math.addExact(Math.multiplyExact(seconds, 1_000L), fraction)
    }

    private fun parseIso(text: String): Long? {
        val match = ISO.matchEntire(text) ?: return null
        val groups = match.groupValues
        val calendar = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
            gregorianChange = Date(Long.MIN_VALUE) // proleptic Gregorian, with no 1582 cutover
            isLenient = false
            clear()
            set(
                groups[1].toInt(),
                groups[2].toInt() - 1,
                groups[3].toInt(),
                groups[4].ifEmpty { "0" }.toInt(),
                groups[5].ifEmpty { "0" }.toInt(),
                groups[6].ifEmpty { "0" }.toInt(),
            )
        }
        // Out-of-range fields (month 13, Feb 30, hour 24, minute 60) throw IllegalArgumentException here.
        val base = calendar.timeInMillis
        val fraction = groups[7].take(3).padEnd(3, '0').toLong()
        return base + fraction - offsetMillis(groups[8])
    }

    /** The offset of a zone suffix such as `+05:30`, `-0800` or `Z`, in milliseconds east of UTC. */
    private fun offsetMillis(zone: String): Long {
        if (zone.isEmpty() || zone == "Z" || zone == "z") return 0L
        val sign = if (zone[0] == '-') -1L else 1L
        val digits = zone.substring(1).replace(":", "")
        val hours = digits.substring(0, 2).toLong()
        val minutes = if (digits.length > 2) digits.substring(2).toLong() else 0L
        require(minutes < 60 && hours * 60 + minutes <= 18 * 60) { "Zone offset out of range: $zone" }
        return sign * (hours * 3_600_000L + minutes * 60_000L)
    }
}
