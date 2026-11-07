package com.omnibus.omnibus.feature.bookdetail.overview.activity

import com.omnibus.omnibus.data.household.HouseholdReader
import com.omnibus.omnibus.data.household.randomHouseholdReader
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

private val utc = TimeZone.getTimeZone("UTC")

private fun at(month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
    GregorianCalendar(utc).apply { clear(); set(2026, month - 1, day, hour, minute) }.timeInMillis

class HouseholdActivityLineTest : DescribeSpec({
    fun sut(reader: HouseholdReader, timeZone: TimeZone = utc): String = activityLine(
        reader,
        nowMillis = at(11, 2),
        Locale.US,
        timeZone,
        ActivityWording(lastRead = { "Last read $it" }, sessions = { "$it sessions" }, underAMinute = randomString()),
    )

    describe("reading a household member's activity") {
        it("TC-1: reads the last read day, the time spent, then the sessions") {
            val reader = randomHouseholdReader().copy(seconds = 22_320L, sessions = 4, lastSessionAtMillis = at(10, 29))

            sut(reader) shouldBe "Last read Oct 29 · 6 h 12 m · 4 sessions"
        }

        context("when the server reports no last sitting") {
            it("TC-4: leaves out the last read day") {
                sut(randomHouseholdReader().copy(seconds = 3_600L, sessions = 2, lastSessionAtMillis = null)) shouldBe
                    "1 h 00 m · 2 sessions"
            }
        }

        context("when the reader has no sessions") {
            it("TC-4: leaves out the session count") {
                sut(randomHouseholdReader().copy(seconds = 3_600L, sessions = 0, lastSessionAtMillis = at(10, 29))) shouldBe
                    "Last read Oct 29 · 1 h 00 m"
            }
        }

        context("when the last sitting falls on another day in the reader's time zone") {
            it("TC-6: reads the day in that time zone") {
                val reader = randomHouseholdReader().copy(seconds = 3_600L, sessions = 1, lastSessionAtMillis = at(10, 28, 23, 30))

                sut(reader, TimeZone.getTimeZone("Asia/Tokyo")) shouldBe "Last read Oct 29 · 1 h 00 m · 1 sessions"
            }
        }
    }
})
