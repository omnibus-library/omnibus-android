package com.omnibus.omnibus.feature.bookdetail.overview.series

import com.omnibus.omnibus.testing.randomDouble
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import io.kotest.property.exhaustive.of
import kotlin.math.max

private const val TOLERANCE = 0.001f
private const val CARD_WIDTH = 104f

private fun randomWidth(): Float = randomDouble(CARD_WIDTH.toDouble(), 2_000.0).toFloat()

class SeriesFanLayoutTest : DescribeSpec({
    fun gathered(count: Int, width: Float, current: Int? = null) =
        (0 until count).map { fanPose(it, count, current, width, spread = false) }

    fun spread(count: Int, width: Float, current: Int? = null) =
        (0 until count).map { fanPose(it, count, current, width, spread = true) }

    describe("the gathered hand") {
        context("when the section is wider than the hand needs") {
            it("#153 TC-1: caps the step at 63.5 dp, for any wide section with two or three books") {
                checkAll(Exhaustive.of(568f, 1000f, 2000f), Exhaustive.of(2, 3)) { width, count ->
                    gathered(count, width).forEachIndexed { i, pose -> pose.xDp shouldBe (i * 63.5f plusOrMinus TOLERANCE) }
                }
            }
        }

        context("when the books do not fit at the cap") {
            it("#153 TC-5: compresses them to end at the section's edge, for any narrow section with many books") {
                checkAll(Exhaustive.of(288f to 10, 379f to 40)) { (width, count) ->
                    gathered(count, width).last().xDp shouldBe (width - CARD_WIDTH plusOrMinus TOLERANCE)
                }
            }
        }

        context("when the section is no wider than a card") {
            it("#104 TC-29: stacks the cards at the start, for any such width or count") {
                checkAll(Exhaustive.of(104f, 60f, 0f), Exhaustive.of(2, 40)) { width, count ->
                    gathered(count, width).forEach { it.xDp shouldBe 0f }
                }
            }
        }

        context("when there is a single card") {
            it("#104 TC-29: sits at the start, unturned") {
                val pose = gathered(1, randomWidth(), current = 0).single()

                pose.xDp shouldBe 0f
                pose.rotationDeg shouldBe 0f
            }
        }

        it("#104 TC-23: turns symmetrically by at most 20 degrees in total, for any count") {
            checkAll(Exhaustive.collection((2..40).toList())) { count ->
                val turns = gathered(count, randomWidth()).map { it.rotationDeg }

                (turns.last() - turns.first() <= 20f + TOLERANCE) shouldBe true
                turns.first() shouldBe (-turns.last() plusOrMinus TOLERANCE)
            }
        }

        it("#104 TC-25: raises only this book") {
            val raised = gathered(5, randomWidth(), current = 2).map { it.yDp }

            raised shouldContainExactly listOf(0f, 0f, -10f, 0f, 0f)
        }
    }

    describe("the stacking order") {
        it("#104 TC-26: puts this book on top, for any position in the series") {
            checkAll(Exhaustive.collection((0..4).toList())) { current ->
                val stack = gathered(5, randomWidth(), current).map { it.z }

                stack.indexOf(stack.max()) shouldBe current
                stack.count { it == stack.max() } shouldBe 1
            }
        }
    }

    describe("the spread row") {
        it("#104 TC-28: sits the cards 120 dp apart, unturned, for any section width") {
            checkAll(Exhaustive.of(288f, 1000f)) { width ->
                spread(3, width, current = 0) shouldContainExactly listOf(
                    FanPose(0f, 0f, 0f, 3),
                    FanPose(120f, 0f, 0f, 2),
                    FanPose(240f, 0f, 0f, 1),
                )
            }
        }
    }

    describe("moving from the hand to the row") {
        it("#104 TC-30: interpolates the pose, keeping the stacking order") {
            lerpPose(FanPose(10f, -10f, -5f, 3), FanPose(130f, 0f, 0f, 7), 0.25f) shouldBe FanPose(40f, -7.5f, -3.75f, 3)
        }
    }

    describe("scrolling the row to this book") {
        it("#104 TC-32: centres this book in the viewport") {
            spreadScrollTargetDp(20, 40, 320f) shouldBe (2308f plusOrMinus TOLERANCE)
        }

        it("#104 TC-32: stays within the scroll range, for the first or last book at any viewport or count") {
            checkAll(Exhaustive.of(320f, 411f, 600f), Exhaustive.of(2, 5, 40)) { viewport, count ->
                val maxScroll = max(0f, spreadWidthDp(count) + 32f - viewport)

                spreadScrollTargetDp(0, count, viewport) shouldBe 0f
                (spreadScrollTargetDp(count - 1, count, viewport) in 0f..maxScroll + TOLERANCE) shouldBe true
            }
        }

        context("when this book is not in the series") {
            it("#104 TC-32: stays at the start") {
                spreadScrollTargetDp(null, 40, 320f) shouldBe 0f
            }
        }
    }
})
