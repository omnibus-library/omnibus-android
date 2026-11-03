package com.omnibus.omnibus.feature.bookdetail.progress

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe

private const val HOUR = 3600L

private fun session(seconds: Long, start: Float?, end: Float?) = ReadingSession(0L, seconds, start, end)

class ReadingPaceTest : DescribeSpec({

    describe("time left") {

        it("TC-18: time left comes from the fraction and the pace") {
            timeLeftSeconds(.62f, 20f) shouldBe 6840L
            listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach { pace ->
                timeLeftSeconds(.62f, pace) shouldBe null
            }
            listOf(0f, 1f, 1.2f, Float.NaN).forEach { fraction ->
                timeLeftSeconds(fraction, 20f) shouldBe null
            }
        }
    }

    describe("pace from sessions with fractions") {

        it("TC-20: C8 pace comes from sessions with fractions") {
            pacePerHour(
                listOf(
                    session(HOUR, .1f, .3f),
                    session(HOUR, .3f, .5f),
                    session(HOUR, null, null),
                ),
            ) shouldBe (20f plusOrMinus 1e-4f)
            pacePerHour(listOf(session(HOUR, .1f, .3f), session(HOUR, .5f, .4f))) shouldBe (10f plusOrMinus 1e-4f)
            pacePerHour(emptyList()) shouldBe null
            pacePerHour(listOf(session(HOUR, null, null))) shouldBe null
            pacePerHour(listOf(session(0, .1f, .3f))) shouldBe null
            pacePerHour(listOf(session(HOUR, .1f, .1f))) shouldBe null
        }
    }

    describe("pace with a fraction") {

        it("TC-22: pace falls back to the fraction over total time") {
            pacePerHour(listOf(session(5 * HOUR, null, null)), .5f) shouldBe (10f plusOrMinus 1e-4f)
            pacePerHour(emptyList(), .5f) shouldBe null
            pacePerHour(listOf(session(0, null, null)), .5f) shouldBe null
            pacePerHour(listOf(session(HOUR, null, null)), 0f) shouldBe null
            pacePerHour(listOf(session(HOUR, null, null)), -.1f) shouldBe null
            pacePerHour(listOf(session(HOUR, null, null)), Float.NaN) shouldBe null

            val measured = listOf(session(HOUR, .1f, .3f))
            pacePerHour(measured, .5f) shouldBe pacePerHour(measured)
        }
    }
})
