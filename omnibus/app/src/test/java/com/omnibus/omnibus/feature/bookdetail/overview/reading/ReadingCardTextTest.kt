package com.omnibus.omnibus.feature.bookdetail.overview.reading

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

class ReadingCardTextTest : DescribeSpec({

    val utc = TimeZone.getTimeZone("UTC")
    val wording = ReadingWording(
        noReading = "No reading recorded yet. Time, sessions and pace appear after you open the book.",
        wishlist = "Add an ebook or an audiobook to start tracking your reading stats for this book.",
        historyFailed = "Your reading history couldn't be loaded.",
        paperCopy = "Stats come from reading in the app. A paper copy is tracked by its read status.",
        syncPending = "Sittings of a minute or more show here once they sync.",
        readingSince = { "Reading since $it" },
        finished = { "Finished $it" },
        labels = mapOf(
            ReadingRowKind.Started to "Started",
            ReadingRowKind.TimeRead to "Time read",
            ReadingRowKind.Pace to "Pace",
            ReadingRowKind.LastRead to "Last read",
            ReadingRowKind.LongestSitting to "Longest sitting",
            ReadingRowKind.TimeLeft to "Time left",
        ),
        timeRead = { duration, count -> "$duration · ${if (count == 1) "1 session" else "$count sessions"}" },
        pace = { "About $it% an hour" },
        longest = { duration, date -> "$duration, $date" },
        timeLeft = { "About $it" },
        rowDescription = { label, value -> "$label, $value" },
        today = "Today",
        yesterday = "Yesterday",
        underAMinute = "under a minute",
    )

    fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0, zone: TimeZone = utc): Long =
        GregorianCalendar(zone).apply { clear(); set(year, month - 1, day, hour, minute) }.timeInMillis

    val now = at(2026, 11, 2)

    fun line(line: ReadingLine, nowMillis: Long = now, zone: TimeZone = utc) =
        line.text(wording, nowMillis, Locale.US, zone)

    fun value(row: ReadingRow, nowMillis: Long = now, zone: TimeZone = utc) =
        row.value(wording, nowMillis, Locale.US, zone)

    describe("the card text") {

        it("TC-20: the fixed lines read exactly as written") {
            line(ReadingLine.NoReading) shouldBe
                "No reading recorded yet. Time, sessions and pace appear after you open the book."
            line(ReadingLine.Wishlist) shouldBe
                "Add an ebook or an audiobook to start tracking your reading stats for this book."
            line(ReadingLine.HistoryFailed) shouldBe "Your reading history couldn't be loaded."
            line(ReadingLine.PaperCopy) shouldBe
                "Stats come from reading in the app. A paper copy is tracked by its read status."
            line(ReadingLine.SyncPending) shouldBe "Sittings of a minute or more show here once they sync."
        }

        it("TC-21: the dated lines carry a long date, with the year for another year") {
            line(ReadingLine.ReadingSince(at(2026, 10, 12))) shouldBe "Reading since October 12"
            line(ReadingLine.Finished(at(2026, 10, 29))) shouldBe "Finished October 29"
            line(ReadingLine.Finished(at(2025, 10, 29))) shouldBe "Finished October 29, 2025"
        }

        it("TC-22: started is a long date, never a day word") {
            wording.labels.getValue(ReadingRowKind.Started) shouldBe "Started"
            value(ReadingRow.Started(at(2026, 10, 12))) shouldBe "October 12"
            value(ReadingRow.Started(at(2026, 11, 1, 9))) shouldBe "November 1"
        }

        it("TC-23: time read is the duration and the session count") {
            wording.labels.getValue(ReadingRowKind.TimeRead) shouldBe "Time read"
            value(ReadingRow.TimeRead(11_040L, 11)) shouldBe "3 h 04 m · 11 sessions"
            value(ReadingRow.TimeRead(2_880L, 1)) shouldBe "48 m · 1 session"
            value(ReadingRow.TimeRead(40L, 1)) shouldBe "under a minute · 1 session"
        }

        it("TC-24: pace reads as a whole percent an hour") {
            wording.labels.getValue(ReadingRowKind.Pace) shouldBe "Pace"
            value(ReadingRow.Pace(20)) shouldBe "About 20% an hour"
        }

        it("TC-25: last read is relative for the last two days, and a long date otherwise") {
            wording.labels.getValue(ReadingRowKind.LastRead) shouldBe "Last read"
            value(ReadingRow.LastRead(at(2026, 11, 1, 22, 14))) shouldBe "Yesterday, 22:14"
            value(ReadingRow.LastRead(at(2026, 11, 2, 8, 2))) shouldBe "Today, 08:02"
            value(ReadingRow.LastRead(at(2026, 10, 29, 22, 14))) shouldBe "October 29, 22:14"
            value(ReadingRow.LastRead(at(2025, 10, 29, 22, 14))) shouldBe "October 29, 2025, 22:14"
        }

        it("TC-26: longest sitting and time left read as the ticket's samples") {
            wording.labels.getValue(ReadingRowKind.LongestSitting) shouldBe "Longest sitting"
            value(ReadingRow.LongestSitting(2_880L, at(2026, 10, 19))) shouldBe "48 m, October 19"
            wording.labels.getValue(ReadingRowKind.TimeLeft) shouldBe "Time left"
            value(ReadingRow.TimeLeft(6_780L)) shouldBe "About 1 h 53 m"
        }

        it("TC-27: dates follow the time zone of the card") {
            val sydney = TimeZone.getTimeZone("Australia/Sydney")
            val nowInSydney = at(2026, 11, 2, 0, 30, sydney)

            value(ReadingRow.LastRead(at(2026, 11, 1, 23, 50, sydney)), nowInSydney, sydney) shouldBe "Yesterday, 23:50"
            value(ReadingRow.Started(at(2026, 11, 1, 8, 0, sydney)), nowInSydney, sydney) shouldBe "November 1"
        }

        it("TC-28: every row reads its label, then its value") {
            val rows = listOf(
                ReadingRow.Started(at(2026, 10, 12)),
                ReadingRow.TimeRead(11_040L, 11),
                ReadingRow.Pace(20),
                ReadingRow.LastRead(at(2026, 11, 1, 22, 14)),
                ReadingRow.LongestSitting(2_880L, at(2026, 10, 19)),
                ReadingRow.TimeLeft(6_780L),
            )
            for (row in rows) {
                row.contentDescription(wording, now, Locale.US, utc) shouldBe "${row.label(wording)}, ${value(row)}"
            }
            ReadingRow.Pace(20).contentDescription(wording, now, Locale.US, utc) shouldBe "Pace, About 20% an hour"
        }
    }
})
