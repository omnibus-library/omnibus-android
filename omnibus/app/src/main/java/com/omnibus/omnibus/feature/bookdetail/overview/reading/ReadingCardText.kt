package com.omnibus.omnibus.feature.bookdetail.overview.reading

import com.omnibus.omnibus.feature.bookdetail.format.BookDetailFormat
import java.util.Locale
import java.util.TimeZone

/** The words of the card, so the wording is built on the JVM and filled from resources on the device. */
internal data class ReadingWording(
    val noReading: String,
    val wishlist: String,
    val historyFailed: String,
    val paperCopy: String,
    val syncPending: String,
    val readingSince: (date: String) -> String,
    val finished: (date: String) -> String,
    val labels: Map<ReadingRowKind, String>,
    val timeRead: (duration: String, sessions: Int) -> String,
    val pace: (percent: Int) -> String,
    val longest: (duration: String, date: String) -> String,
    val timeLeft: (duration: String) -> String,
    val rowDescription: (label: String, value: String) -> String,
    val today: String,
    val yesterday: String,
    val underAMinute: String,
)

/** The line's text, such as "Reading since October 12". Dates are long dates, relative to [nowMillis]. */
internal fun ReadingLine.text(wording: ReadingWording, nowMillis: Long, locale: Locale, timeZone: TimeZone): String = when (this) {
    ReadingLine.NoReading -> wording.noReading
    ReadingLine.Wishlist -> wording.wishlist
    ReadingLine.HistoryFailed -> wording.historyFailed
    ReadingLine.PaperCopy -> wording.paperCopy
    ReadingLine.SyncPending -> wording.syncPending
    is ReadingLine.ReadingSince -> wording.readingSince(BookDetailFormat.longDate(millis, nowMillis, locale, timeZone))
    is ReadingLine.Finished -> wording.finished(BookDetailFormat.longDate(millis, nowMillis, locale, timeZone))
}

/** The row's label, such as "Pace". */
internal fun ReadingRow.label(wording: ReadingWording): String = wording.labels.getValue(kind)

/** The row's value, such as "About 20% an hour" or "Yesterday, 22:14". */
internal fun ReadingRow.value(wording: ReadingWording, nowMillis: Long, locale: Locale, timeZone: TimeZone): String =
    when (this) {
        is ReadingRow.Started -> BookDetailFormat.longDate(millis, nowMillis, locale, timeZone)
        is ReadingRow.TimeRead -> wording.timeRead(BookDetailFormat.hoursMinutes(seconds, wording.underAMinute), sessions)
        is ReadingRow.Pace -> wording.pace(percentPerHour)
        is ReadingRow.LastRead -> BookDetailFormat.relativeDateTime(
            millis, nowMillis, locale, timeZone, wording.today, wording.yesterday,
        )
        is ReadingRow.LongestSitting -> wording.longest(
            BookDetailFormat.hoursMinutes(seconds, wording.underAMinute),
            BookDetailFormat.longDate(startedAtMillis, nowMillis, locale, timeZone),
        )
        is ReadingRow.TimeLeft -> wording.timeLeft(BookDetailFormat.hoursMinutes(seconds, wording.underAMinute))
    }

/** The row as TalkBack reads it: its label, then its value. */
internal fun ReadingRow.contentDescription(
    wording: ReadingWording,
    nowMillis: Long,
    locale: Locale,
    timeZone: TimeZone,
): String = wording.rowDescription(label(wording), value(wording, nowMillis, locale, timeZone))
