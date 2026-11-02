package com.omnibus.omnibus.feature.bookdetail.format

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random

class BookDetailFormatTest : DescribeSpec({

    val utc = TimeZone.getTimeZone("UTC")
    val newYork = TimeZone.getTimeZone("America/New_York")
    val london = TimeZone.getTimeZone("Europe/London")

    /** Epoch millis of a wall-clock time in [zone]. */
    fun at(
        zone: TimeZone,
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 0,
        minute: Int = 0,
        second: Int = 0,
        millisecond: Int = 0,
    ): Long = GregorianCalendar(zone).apply {
        clear()
        set(year, month - 1, day, hour, minute, second)
        set(GregorianCalendar.MILLISECOND, millisecond)
    }.timeInMillis

    /** A UTC instant, the common case in these tests. */
    fun utcAt(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0, second: Int = 0, millisecond: Int = 0) =
        at(utc, year, month, day, hour, minute, second, millisecond)

    val now2025 = utcAt(2025, 11, 15, 12, 0)

    describe("shortDate") {

        it("TC-55: omits the year in the current year, including the first and last day") {
            BookDetailFormat.shortDate(utcAt(2025, 10, 29, 12), now2025, Locale.US, utc) shouldBe "Oct 29"
            BookDetailFormat.shortDate(utcAt(2025, 1, 1, 12), now2025, Locale.US, utc) shouldBe "Jan 1"
            BookDetailFormat.shortDate(utcAt(2025, 12, 31, 12), now2025, Locale.US, utc) shouldBe "Dec 31"
        }

        it("TC-56: includes the year for past and future dates in another year") {
            BookDetailFormat.shortDate(utcAt(2024, 10, 29, 12), now2025, Locale.US, utc) shouldBe "Oct 29, 2024"
            BookDetailFormat.shortDate(utcAt(2027, 3, 4, 12), now2025, Locale.US, utc) shouldBe "Mar 4, 2027"
        }

        it("TC-57: the year rule uses the supplied time zone, not the JVM default") {
            // 2025-12-31 23:30 in New York is 2026-01-01 in UTC; "now" is in 2026.
            val millis = at(newYork, 2025, 12, 31, 23, 30)
            val now = utcAt(2026, 6, 1, 12)

            BookDetailFormat.shortDate(millis, now, Locale.US, newYork) shouldBe "Dec 31, 2025"
            BookDetailFormat.shortDate(millis, now, Locale.US, utc) shouldBe "Jan 1"
        }

        it("TC-58: month names come from the supplied locale") {
            val millis = utcAt(2024, 10, 29, 12)
            BookDetailFormat.shortDate(millis, now2025, Locale.FRANCE, utc) shouldContain "oct"
            BookDetailFormat.shortDate(millis, now2025, Locale.GERMANY, utc) shouldContain "Okt"
            BookDetailFormat.shortDate(millis, now2025, Locale.US, utc) shouldContain "Oct"
        }

        it("TC-59: pre-1970 and zero millis format normally") {
            BookDetailFormat.shortDate(-1L, now2025, Locale.US, utc) shouldBe "Dec 31, 1969"
            BookDetailFormat.shortDate(0L, now2025, Locale.US, utc) shouldBe "Jan 1, 1970"
        }
    }

    describe("longDate") {

        it("TC-60: full month name, with the year only in another year, in the supplied locale") {
            BookDetailFormat.longDate(utcAt(2025, 10, 29, 12), now2025, Locale.US, utc) shouldBe "October 29"
            BookDetailFormat.longDate(utcAt(2024, 10, 29, 12), now2025, Locale.US, utc) shouldBe "October 29, 2024"
            BookDetailFormat.longDate(utcAt(2025, 10, 29, 12), now2025, Locale.FRANCE, utc) shouldContain "octobre"
        }
    }

    describe("relativeDateTime") {

        it("TC-61: same calendar day is Today, 24-hour and zero padded, earlier or later in the day") {
            BookDetailFormat.relativeDateTime(utcAt(2025, 11, 15, 22, 14), utcAt(2025, 11, 15, 23, 30), Locale.US, utc) shouldBe
                "Today, 22:14"
            BookDetailFormat.relativeDateTime(utcAt(2025, 11, 15, 0, 5), utcAt(2025, 11, 15, 23, 30), Locale.US, utc) shouldBe
                "Today, 00:05"
        }

        it("TC-62: one calendar day before is Yesterday") {
            BookDetailFormat.relativeDateTime(utcAt(2025, 11, 14, 9, 30), now2025, Locale.US, utc) shouldBe
                "Yesterday, 09:30"
        }

        it("TC-63: two or more days ago, last year, and a future day use the long date and never Today") {
            BookDetailFormat.relativeDateTime(utcAt(2025, 11, 13, 9, 30), now2025, Locale.US, utc) shouldBe
                "November 13, 09:30"
            BookDetailFormat.relativeDateTime(utcAt(2024, 12, 31, 10, 0), now2025, Locale.US, utc) shouldBe
                "December 31, 2024, 10:00"
            BookDetailFormat.relativeDateTime(utcAt(2025, 11, 16, 10, 0), now2025, Locale.US, utc) shouldBe
                "November 16, 10:00"
        }

        it("TC-64: midnight boundaries belong to their own day") {
            BookDetailFormat.relativeDateTime(utcAt(2025, 11, 15, 0, 0, 0, 0), now2025, Locale.US, utc) shouldBe
                "Today, 00:00"
            BookDetailFormat.relativeDateTime(utcAt(2025, 11, 14, 23, 59, 59, 999), now2025, Locale.US, utc) shouldBe
                "Yesterday, 23:59"
            BookDetailFormat.relativeDateTime(utcAt(2025, 11, 14, 0, 0, 0, 0), now2025, Locale.US, utc) shouldBe
                "Yesterday, 00:00"
            BookDetailFormat.relativeDateTime(utcAt(2025, 11, 13, 23, 59, 59, 999), now2025, Locale.US, utc) shouldBe
                "November 13, 23:59"
        }

        it("TC-65: day comparison is by calendar field across DST changes, not 24-hour subtraction") {
            // America/New_York spring forward on 2026-03-08 (a 23-hour day).
            val springNow = at(newYork, 2026, 3, 8, 12)
            BookDetailFormat.relativeDateTime(at(newYork, 2026, 3, 7, 23, 30), springNow, Locale.US, newYork) shouldBe
                "Yesterday, 23:30"
            BookDetailFormat.relativeDateTime(at(newYork, 2026, 3, 8, 0, 30), springNow, Locale.US, newYork) shouldBe
                "Today, 00:30"
            // Fall back on 2026-11-01 (a 25-hour day), read from the day after.
            val fallNow = at(newYork, 2026, 11, 2, 0, 30)
            BookDetailFormat.relativeDateTime(at(newYork, 2026, 11, 1, 23, 30), fallNow, Locale.US, newYork) shouldBe
                "Yesterday, 23:30"
            BookDetailFormat.relativeDateTime(at(newYork, 2026, 11, 1, 0, 30), fallNow, Locale.US, newYork) shouldBe
                "Yesterday, 00:30"
            BookDetailFormat.relativeDateTime(at(newYork, 2026, 10, 31, 23, 30), fallNow, Locale.US, newYork) shouldBe
                "October 31, 23:30"
            // Europe/London spring forward on 2026-03-29.
            val londonNow = at(london, 2026, 3, 29, 12)
            BookDetailFormat.relativeDateTime(at(london, 2026, 3, 28, 23), londonNow, Locale.US, london) shouldBe
                "Yesterday, 23:00"
        }

        it("TC-66: Jan 1 with a Dec 31 time is Yesterday across the year boundary") {
            BookDetailFormat.relativeDateTime(utcAt(2025, 12, 31, 18, 0), utcAt(2026, 1, 1, 9, 0), Locale.US, utc) shouldBe
                "Yesterday, 18:00"
        }

        it("TC-67: uses the supplied today and yesterday words, and English by default") {
            BookDetailFormat.relativeDateTime(
                utcAt(2025, 11, 15, 22, 14), now2025, Locale.US, utc, today = "Hoy", yesterday = "Ayer",
            ) shouldBe "Hoy, 22:14"
            BookDetailFormat.relativeDateTime(
                utcAt(2025, 11, 14, 9, 30), now2025, Locale.US, utc, today = "Hoy", yesterday = "Ayer",
            ) shouldBe "Ayer, 09:30"
            BookDetailFormat.relativeDateTime(utcAt(2025, 11, 14, 9, 30), now2025, Locale.US, utc) shouldBe
                "Yesterday, 09:30"
        }
    }

    describe("hoursMinutes") {

        it("TC-68: formats and floors the table of durations") {
            val table = listOf(
                0L to "under a minute",
                59L to "under a minute",
                60L to "1 m",
                119L to "1 m",
                120L to "2 m",
                2_880L to "48 m",
                3_599L to "59 m",
                3_600L to "1 h 00 m",
                11_040L to "3 h 04 m",
                432_000L to "120 h 00 m",
            )
            table.forEach { (seconds, expected) ->
                BookDetailFormat.hoursMinutes(seconds) shouldBe expected
            }
        }

        it("TC-69: negatives clamp to the under-a-minute text, extremes do not throw, custom text is used") {
            BookDetailFormat.hoursMinutes(-1L) shouldBe "under a minute"
            BookDetailFormat.hoursMinutes(-3_600L) shouldBe "under a minute"
            BookDetailFormat.hoursMinutes(Long.MIN_VALUE) shouldBe "under a minute"
            BookDetailFormat.hoursMinutes(Long.MAX_VALUE) shouldContain " h "
            BookDetailFormat.hoursMinutes(0L, underAMinute = "moins d'une minute") shouldBe "moins d'une minute"
        }
    }

    describe("parseServerTimestamp") {

        it("TC-70: null and blank input give null, and surrounding whitespace is trimmed") {
            BookDetailFormat.parseServerTimestamp(null) shouldBe null
            BookDetailFormat.parseServerTimestamp("") shouldBe null
            BookDetailFormat.parseServerTimestamp("   ") shouldBe null
            BookDetailFormat.parseServerTimestamp("  1761775445  ") shouldBe 1_761_775_445_000L
        }

        it("TC-71: accepts integer and decimal epoch seconds and rejects overflow") {
            BookDetailFormat.parseServerTimestamp("1761775445") shouldBe 1_761_775_445_000L
            BookDetailFormat.parseServerTimestamp("0") shouldBe 0L
            BookDetailFormat.parseServerTimestamp("-1") shouldBe -1_000L
            BookDetailFormat.parseServerTimestamp("1761775445.5") shouldBe 1_761_775_445_500L
            BookDetailFormat.parseServerTimestamp("9223372036854775807") shouldBe null
        }

        it("TC-72: accepts the ISO forms, truncates fractions to millis, and reads no zone as UTC") {
            val base = 1_761_775_445_000L
            BookDetailFormat.parseServerTimestamp("2025-10-29") shouldBe 1_761_696_000_000L
            BookDetailFormat.parseServerTimestamp("2025-10-29T22:04") shouldBe base - 5_000L
            BookDetailFormat.parseServerTimestamp("2025-10-29T22:04:05") shouldBe base
            BookDetailFormat.parseServerTimestamp("2025-10-29T22:04:05.1") shouldBe base + 100L
            BookDetailFormat.parseServerTimestamp("2025-10-29T22:04:05.123456789") shouldBe base + 123L
            BookDetailFormat.parseServerTimestamp("2025-10-29t22:04:05") shouldBe base
            BookDetailFormat.parseServerTimestamp("2025-10-29 22:04:05") shouldBe base
        }

        it("TC-73: applies Z, lower-case z, and each offset form") {
            val base = 1_761_775_445_000L
            BookDetailFormat.parseServerTimestamp("2025-10-29T22:04:05Z") shouldBe base
            BookDetailFormat.parseServerTimestamp("2025-10-29T22:04:05z") shouldBe base
            BookDetailFormat.parseServerTimestamp("2025-10-29T22:04:05+05:30") shouldBe base - 19_800_000L
            BookDetailFormat.parseServerTimestamp("2025-10-29T22:04:05-0800") shouldBe base + 28_800_000L
            BookDetailFormat.parseServerTimestamp("2025-10-29T22:04:05+02") shouldBe base - 7_200_000L
            BookDetailFormat.parseServerTimestamp("2025-10-29T22:04:05-18:00") shouldBe base + 64_800_000L
        }

        it("TC-74: the ISO and epoch forms of one instant agree") {
            BookDetailFormat.parseServerTimestamp("2025-10-29T22:04:05Z") shouldBe
                BookDetailFormat.parseServerTimestamp("1761775445")
        }

        it("TC-75: rejects invalid, out-of-range, and trailing-junk input") {
            listOf(
                "abc",
                "2025-13-01",
                "2025-02-30",
                "2025-10-29T24:00",
                "2025-10-29T22:60",
                "2025-10-29T22:04:05+19:00",
                "2025-10-29T22:04:05Z junk",
                "1761775445abc",
                "+292277026596-12-04T15:30:07Z",
                "10000-01-01",
                "1e9",
                "0x10",
            ).forEach { input ->
                BookDetailFormat.parseServerTimestamp(input) shouldBe null
            }
        }

        it("TC-76: never throws on fuzzed or very long input") {
            val fixed = listOf("", "-", "+", "٣", "٣٣٣٣", "-+", "T", "9".repeat(10_000), "".padEnd(10_000, '9'), "2025-10-29T")
            val pool = "0123456789-+:.TtZz ٣abcXYZ\u0000\n"
            val random = Random(42)
            val generated = List(1_000) { (1..random.nextInt(1, 40)).map { pool.random(random) }.joinToString("") }
            (fixed + generated).forEach { input ->
                BookDetailFormat.parseServerTimestamp(input)
            }
        }
    }
})
