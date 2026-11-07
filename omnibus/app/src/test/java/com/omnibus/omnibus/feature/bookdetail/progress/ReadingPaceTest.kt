package com.omnibus.omnibus.feature.bookdetail.progress

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

private const val HOUR = 3600L

private fun session(seconds: Long, start: Float?, end: Float?) = ReadingSession(0L, seconds, start, end)

class ReadingPaceTest : DescribeSpec({

    describe("the time left") {
        it("TC-18: comes from the fraction at the pace") {
            timeLeftSeconds(.62f, 20f) shouldBe 6840L
        }

        it("TC-18: is missing for each unusable input") {
            checkAll(
                Exhaustive.of(
                    .62f to 0f,
                    .62f to -1f,
                    .62f to Float.NaN,
                    .62f to Float.POSITIVE_INFINITY,
                    0f to 20f,
                    1f to 20f,
                    1.2f to 20f,
                    Float.NaN to 20f,
                ),
            ) { (fraction, pace) ->
                timeLeftSeconds(fraction, pace) shouldBe null
            }
        }
    }

    describe("the pace") {
        context("with fractions on the sessions") {
            it("TC-20: averages each kind of session, a backwards one adding nothing") {
                checkAll(
                    Exhaustive.of(
                        listOf(session(HOUR, .1f, .3f), session(HOUR, .3f, .5f), session(HOUR, null, null)) to 20f,
                        listOf(session(HOUR, .1f, .3f), session(HOUR, .5f, .4f)) to 10f,
                    ),
                ) { (sessions, pace) ->
                    pacePerHour(sessions)!! shouldBe (pace plusOrMinus 1e-4f)
                }
            }

            it("TC-20: is missing for each list with no measurable progress") {
                checkAll(
                    Exhaustive.of(
                        emptyList(),
                        listOf(session(HOUR, null, null)),
                        listOf(session(0, .1f, .3f)),
                        listOf(session(HOUR, .1f, .1f)),
                    ),
                ) { sessions ->
                    pacePerHour(sessions) shouldBe null
                }
            }
        }

        context("with a fraction") {
            it("TC-22: is the measured pace, else the fraction over the total time") {
                val measured = listOf(session(HOUR, .1f, .3f))

                pacePerHour(listOf(session(5 * HOUR, null, null)), .5f)!! shouldBe (10f plusOrMinus 1e-4f)
                pacePerHour(measured, .5f) shouldBe pacePerHour(measured)
            }

            it("TC-22: is missing for each unusable input") {
                checkAll(
                    Exhaustive.of(
                        emptyList<ReadingSession>() to .5f,
                        listOf(session(0, null, null)) to .5f,
                        listOf(session(HOUR, null, null)) to 0f,
                        listOf(session(HOUR, null, null)) to -.1f,
                        listOf(session(HOUR, null, null)) to Float.NaN,
                    ),
                ) { (sessions, fraction) ->
                    pacePerHour(sessions, fraction) shouldBe null
                }
            }
        }
    }
})
