package com.omnibus.omnibus.feature.bookdetail.overview.reading

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

private val utc = TimeZone.getTimeZone("UTC")

private val wording = ReadingWording(
    noReading = "no reading",
    wishlist = "wishlist",
    historyFailed = "history failed",
    paperCopy = "paper copy",
    syncPending = "sync pending",
    readingSince = { "Reading since $it" },
    finished = { "Finished $it" },
    labels = ReadingRowKind.entries.associateWith { it.name },
    timeRead = { duration, count -> "$duration · $count sessions" },
    pace = { "About $it% an hour" },
    longest = { duration, date -> "$duration, $date" },
    timeLeft = { "About $it" },
    rowDescription = { label, value -> "$label, $value" },
    today = "Today",
    yesterday = "Yesterday",
    underAMinute = "under a minute",
)

private fun at(month: Int, day: Int, hour: Int = 12, minute: Int = 0, zone: TimeZone = utc): Long =
    GregorianCalendar(zone).apply { clear(); set(2026, month - 1, day, hour, minute) }.timeInMillis

class ReadingCardTextTest : DescribeSpec({
    fun value(row: ReadingRow, now: Long = at(11, 2), zone: TimeZone = utc) = row.value(wording, now, Locale.US, zone)

    describe("the card's line") {
        it("TC-20: reads its own wording") {
            val lines = Exhaustive.of(
                ReadingLine.NoReading to wording.noReading,
                ReadingLine.Wishlist to wording.wishlist,
                ReadingLine.HistoryFailed to wording.historyFailed,
                ReadingLine.PaperCopy to wording.paperCopy,
                ReadingLine.SyncPending to wording.syncPending,
            )

            checkAll(lines) { (line, text) -> line.text(wording, at(11, 2), Locale.US, utc) shouldBe text }
        }

        it("TC-21: carries a long date, for reading since or finished") {
            val lines = Exhaustive.of(
                ReadingLine.ReadingSince(at(10, 12)) to "Reading since October 12",
                ReadingLine.Finished(at(10, 29)) to "Finished October 29",
            )

            checkAll(lines) { (line, text) -> line.text(wording, at(11, 2), Locale.US, utc) shouldBe text }
        }
    }

    describe("a row's value") {
        it("TC-22: reads started as a long date, never a day word") {
            value(ReadingRow.Started(at(11, 1, 9))) shouldBe "November 1"
        }

        it("TC-25: reads last read relative to now") {
            value(ReadingRow.LastRead(at(11, 1, 22, 14))) shouldBe "Yesterday, 22:14"
        }

        it("TC-26: reads the longest sitting's duration, then its day") {
            value(ReadingRow.LongestSitting(2_880L, at(10, 19))) shouldBe "48 m, October 19"
        }

        it("TC-27: follows the time zone of the card") {
            val sydney = TimeZone.getTimeZone("Australia/Sydney")

            value(ReadingRow.LastRead(at(11, 1, 23, 50, sydney)), at(11, 2, 0, 30, sydney), sydney) shouldBe "Yesterday, 23:50"
        }
    }

    describe("a row as TalkBack reads it") {
        it("TC-28: reads its label, then its value") {
            ReadingRow.Pace(20).contentDescription(wording, at(11, 2), Locale.US, utc) shouldBe "Pace, About 20% an hour"
        }
    }
})
