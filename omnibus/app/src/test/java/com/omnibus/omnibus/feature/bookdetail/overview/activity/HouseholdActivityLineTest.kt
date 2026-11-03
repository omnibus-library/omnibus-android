package com.omnibus.omnibus.feature.bookdetail.overview.activity

import com.omnibus.omnibus.data.household.HouseholdReader
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

class HouseholdActivityLineTest : DescribeSpec({

    val utc = TimeZone.getTimeZone("UTC")
    val wording = ActivityWording(
        lastRead = { "Last read $it" },
        sessions = { if (it == 1L) "1 session" else "$it sessions" },
        underAMinute = "under a minute",
    )

    fun at(year: Int, month: Int, day: Int): Long =
        GregorianCalendar(utc).apply { clear(); set(year, month - 1, day, 12, 0) }.timeInMillis

    val now = at(2026, 11, 2)

    fun reader(seconds: Long, sessions: Long, lastAt: Long?) = HouseholdReader(
        userId = 7, name = "Maren", hasAvatar = false, seconds = seconds, sessions = sessions, lastSessionAtMillis = lastAt,
    )

    fun line(reader: HouseholdReader) = activityLine(reader, now, Locale.US, utc, wording)

    describe("the activity line") {

        it("TC-1: reads last read day, time spent and sessions") {
            line(reader(6 * 3_600L + 12 * 60L, 4, at(2026, 10, 29))) shouldBe "Last read Oct 29 · 6 h 12 m · 4 sessions"
        }

        it("TC-2: a reader still in the book reads the same way, with minutes only") {
            line(reader(2 * 3_600L + 60L, 2, at(2026, 11, 1))) shouldBe "Last read Nov 1 · 2 h 01 m · 2 sessions"
            line(reader(48 * 60L, 1, at(2026, 11, 2))) shouldBe "Last read Nov 2 · 48 m · 1 session"
        }

        it("TC-3: a last read in another year carries the year") {
            line(reader(3_600L, 3, at(2025, 6, 14))) shouldBe "Last read Jun 14, 2025 · 1 h 00 m · 3 sessions"
        }

        it("TC-4: leaves out the parts the server didn't report") {
            line(reader(3_600L, 0, null)) shouldBe "1 h 00 m"
            line(reader(3_600L, 2, null)) shouldBe "1 h 00 m · 2 sessions"
            line(reader(3_600L, 0, at(2026, 10, 29))) shouldBe "Last read Oct 29 · 1 h 00 m"
        }

        it("TC-5: a reader under a minute in the book reads as such") {
            line(reader(30, 0, at(2026, 10, 29))) shouldBe "Last read Oct 29 · under a minute"
        }

        it("TC-6: the day is read in the given time zone") {
            val lateOnThe28th = GregorianCalendar(utc).apply { clear(); set(2026, 9, 28, 23, 30) }.timeInMillis
            val tokyo = TimeZone.getTimeZone("Asia/Tokyo")
            activityLine(reader(3_600L, 1, lateOnThe28th), now, Locale.US, tokyo, wording) shouldBe
                "Last read Oct 29 · 1 h 00 m · 1 session"
        }
    }
})
