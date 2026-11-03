package com.omnibus.omnibus.feature.bookdetail.overview.activity

import com.omnibus.omnibus.data.household.HouseholdReader
import com.omnibus.omnibus.feature.bookdetail.format.BookDetailFormat
import java.util.Locale
import java.util.TimeZone

/** The words of an activity line, so the wording is built on the JVM and filled from resources on the device. */
internal data class ActivityWording(
    val lastRead: (day: String) -> String,
    val sessions: (count: Long) -> String,
    val underAMinute: String,
)

/**
 * One reader's line in the Activity list: when they last read, how long they have spent, and how many sittings,
 * such as "Last read Oct 29 · 6 h 12 m · 4 sessions". The server reports time and sittings, not progress, so the
 * line never says how far they are. A part the server didn't report is left out: no "Last read" without a time
 * of the last sitting, and no sessions for a reader with none.
 */
internal fun activityLine(
    reader: HouseholdReader,
    nowMillis: Long,
    locale: Locale,
    timeZone: TimeZone,
    wording: ActivityWording,
): String = listOfNotNull(
    reader.lastSessionAtMillis?.let { wording.lastRead(BookDetailFormat.shortDate(it, nowMillis, locale, timeZone)) },
    BookDetailFormat.hoursMinutes(reader.seconds, wording.underAMinute),
    reader.sessions.takeIf { it > 0 }?.let(wording.sessions),
).joinToString(SEPARATOR)

private const val SEPARATOR = " · "
