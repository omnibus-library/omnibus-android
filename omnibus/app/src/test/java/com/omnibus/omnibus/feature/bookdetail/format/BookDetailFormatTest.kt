package com.omnibus.omnibus.feature.bookdetail.format

import com.omnibus.omnibus.testing.TEST_RANDOM
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import io.kotest.property.exhaustive.of
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

private val UTC: TimeZone = TimeZone.getTimeZone("UTC")
private val NEW_YORK: TimeZone = TimeZone.getTimeZone("America/New_York")

/** Epoch millis of a wall-clock time in [zone]. */
private fun at(
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
private fun utcAt(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0, second: Int = 0, millisecond: Int = 0) =
    at(UTC, year, month, day, hour, minute, second, millisecond)

private val NOW_2025 = utcAt(2025, 11, 15, 12, 0)

private fun relativeUtc(millis: Long, now: Long = NOW_2025) = BookDetailFormat.relativeDateTime(millis, now, Locale.US, UTC)

private fun relativeNewYork(millis: Long, now: Long) = BookDetailFormat.relativeDateTime(millis, now, Locale.US, NEW_YORK)

class BookDetailFormatTest : DescribeSpec({

    describe("the short date") {

        context("when the date is in the current year") {
            it("TC-55: omits the year, through the first and last day") {
                BookDetailFormat.shortDate(utcAt(2025, 1, 1, 12), NOW_2025, Locale.US, UTC) shouldBe "Jan 1"
                BookDetailFormat.shortDate(utcAt(2025, 12, 31, 12), NOW_2025, Locale.US, UTC) shouldBe "Dec 31"
            }
        }

        context("when the date is in another year") {
            it("TC-56: includes the year, past or future") {
                BookDetailFormat.shortDate(utcAt(2024, 10, 29, 12), NOW_2025, Locale.US, UTC) shouldBe "Oct 29, 2024"
                BookDetailFormat.shortDate(utcAt(2027, 3, 4, 12), NOW_2025, Locale.US, UTC) shouldBe "Mar 4, 2027"
            }
        }

        context("when the zone moves the date across a year") {
            it("TC-57: applies the year rule in the supplied zone") {
                // 2025-12-31 23:30 in New York is 2026-01-01 in UTC; "now" is in 2026.
                val millis = at(NEW_YORK, 2025, 12, 31, 23, 30)
                val now = utcAt(2026, 6, 1, 12)

                BookDetailFormat.shortDate(millis, now, Locale.US, NEW_YORK) shouldBe "Dec 31, 2025"
                BookDetailFormat.shortDate(millis, now, Locale.US, UTC) shouldBe "Jan 1"
            }
        }
    }

    describe("the long date") {

        context("when the date is in another year") {
            it("TC-60: includes the year") {
                BookDetailFormat.longDate(utcAt(2024, 10, 29, 12), NOW_2025, Locale.US, UTC) shouldBe "October 29, 2024"
            }
        }
    }

    describe("the relative date and time") {

        context("when the time is on the same calendar day") {
            it("TC-61: is Today, 24-hour and zero padded") {
                relativeUtc(utcAt(2025, 11, 15, 22, 14), now = utcAt(2025, 11, 15, 23, 30)) shouldBe "Today, 22:14"
                relativeUtc(utcAt(2025, 11, 15, 0, 5), now = utcAt(2025, 11, 15, 23, 30)) shouldBe "Today, 00:05"
            }
        }

        context("when the time is one calendar day before") {
            it("TC-62: is Yesterday") {
                relativeUtc(utcAt(2025, 11, 14, 9, 30)) shouldBe "Yesterday, 09:30"
            }
        }

        context("when the time is on a future day") {
            it("TC-63: uses the long date, never Today") {
                relativeUtc(utcAt(2025, 11, 16, 10, 0)) shouldBe "November 16, 10:00"
            }
        }

        context("when the time is on a midnight boundary") {
            it("TC-64: belongs to its own day") {
                relativeUtc(utcAt(2025, 11, 15, 0, 0, 0, 0)) shouldBe "Today, 00:00"
                relativeUtc(utcAt(2025, 11, 14, 23, 59, 59, 999)) shouldBe "Yesterday, 23:59"
                relativeUtc(utcAt(2025, 11, 14, 0, 0, 0, 0)) shouldBe "Yesterday, 00:00"
                relativeUtc(utcAt(2025, 11, 13, 23, 59, 59, 999)) shouldBe "November 13, 23:59"
            }
        }

        context("when the clocks have changed") {
            it("TC-65: compares calendar days, not 24-hour spans") {
                // America/New_York springs forward on 2026-03-08.
                val now = at(NEW_YORK, 2026, 3, 8, 12)

                relativeNewYork(at(NEW_YORK, 2026, 3, 7, 23, 30), now) shouldBe "Yesterday, 23:30"
                relativeNewYork(at(NEW_YORK, 2026, 3, 8, 0, 30), now) shouldBe "Today, 00:30"
                // It falls back on 2026-11-01, read from the day after.
                relativeNewYork(at(NEW_YORK, 2026, 10, 31, 23, 30), at(NEW_YORK, 2026, 11, 2, 0, 30)) shouldBe "October 31, 23:30"
            }
        }

        context("when yesterday was in the previous year") {
            it("TC-66: is still Yesterday") {
                relativeUtc(utcAt(2025, 12, 31, 18, 0), now = utcAt(2026, 1, 1, 9, 0)) shouldBe "Yesterday, 18:00"
            }
        }

        context("when other words are supplied") {
            it("TC-67: uses them") {
                BookDetailFormat.relativeDateTime(
                    utcAt(2025, 11, 14, 9, 30), NOW_2025, Locale.US, UTC, today = "Hoy", yesterday = "Ayer",
                ) shouldBe "Ayer, 09:30"
            }
        }
    }

    describe("hours and minutes") {

        it("TC-68: floors each duration to its text") {
            checkAll(
                Exhaustive.of(
                    59L to "under a minute",
                    60L to "1 m",
                    119L to "1 m",
                    3_599L to "59 m",
                    3_600L to "1 h 00 m",
                    11_040L to "3 h 04 m",
                    432_000L to "120 h 00 m",
                ),
            ) { (seconds, expected) ->
                BookDetailFormat.hoursMinutes(seconds) shouldBe expected
            }
        }

        context("when the duration is negative") {
            it("TC-69: clamps to the under-a-minute text") {
                checkAll(Exhaustive.of(-1L, Long.MIN_VALUE)) { seconds ->
                    BookDetailFormat.hoursMinutes(seconds) shouldBe "under a minute"
                }
            }
        }
    }

    describe("reading a server timestamp") {

        val base = 1_761_775_445_000L // 2025-10-29T22:04:05Z

        context("when the input is blank") {
            it("TC-70: gives null, for null, empty or spaces") {
                checkAll(Exhaustive.of(null, "", "   ")) { input ->
                    BookDetailFormat.parseServerTimestamp(input) shouldBe null
                }
            }
        }

        context("when the input is epoch seconds") {
            it("TC-71: reads trimmed integers, negatives, decimals") {
                checkAll(
                    Exhaustive.of("  1761775445  " to base, "-1" to -1_000L, "1761775445.5" to base + 500L),
                ) { (input, expected) ->
                    BookDetailFormat.parseServerTimestamp(input) shouldBe expected
                }
            }
        }

        context("when the input is ISO with no zone") {
            it("TC-72: reads it as UTC, truncating fractions to millis, in every form") {
                checkAll(
                    Exhaustive.of(
                        "2025-10-29" to 1_761_696_000_000L,
                        "2025-10-29T22:04" to base - 5_000L,
                        "2025-10-29T22:04:05" to base,
                        "2025-10-29T22:04:05.1" to base + 100L,
                        "2025-10-29T22:04:05.123456789" to base + 123L,
                        "2025-10-29t22:04:05" to base,
                        "2025-10-29 22:04:05" to base,
                    ),
                ) { (input, expected) ->
                    BookDetailFormat.parseServerTimestamp(input) shouldBe expected
                }
            }
        }

        context("when the input is ISO with a zone") {
            it("TC-73: applies Z, lower-case z and each offset form") {
                checkAll(
                    Exhaustive.of(
                        "2025-10-29T22:04:05Z" to base,
                        "2025-10-29T22:04:05z" to base,
                        "2025-10-29T22:04:05+05:30" to base - 19_800_000L,
                        "2025-10-29T22:04:05-0800" to base + 28_800_000L,
                        "2025-10-29T22:04:05+02" to base - 7_200_000L,
                    ),
                ) { (input, expected) ->
                    BookDetailFormat.parseServerTimestamp(input) shouldBe expected
                }
            }
        }

        context("when the input is invalid") {
            it("TC-75: gives null, for junk, out-of-range values and trailing text") {
                checkAll(
                    Exhaustive.of(
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
                        "9223372036854775807",
                    ),
                ) { input ->
                    BookDetailFormat.parseServerTimestamp(input) shouldBe null
                }
            }
        }

        context("when the input is fuzzed") {
            it("TC-76: never throws, at any length") {
                val pool = "0123456789-+:.TtZz ٣abcXYZ\u0000\n"
                val generated = List(1_000) { (1..TEST_RANDOM.nextInt(1, 40)).map { pool.random(TEST_RANDOM) }.joinToString("") }

                checkAll(Exhaustive.collection(generated + listOf("٣٣٣٣", "9".repeat(10_000), "2025-10-29T"))) { input ->
                    shouldNotThrowAny { BookDetailFormat.parseServerTimestamp(input) }
                }
            }
        }
    }
})
