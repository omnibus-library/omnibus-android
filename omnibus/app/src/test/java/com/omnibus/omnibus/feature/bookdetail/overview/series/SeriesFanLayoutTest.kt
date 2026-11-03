package com.omnibus.omnibus.feature.bookdetail.overview.series

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class SeriesFanLayoutTest : DescribeSpec({

    /** Section widths for 320, 411 and 600 dp screens, less 32 dp of panel padding, plus a wide case. */
    val gatheredWidths = listOf(288f, 379f, 568f, 1000f)
    val counts = 2..40
    val tolerance = 0.001f

    fun List<Float>.shouldBeNear(expected: List<Float>) {
        size shouldBe expected.size
        zip(expected).forEach { (actual, want) -> actual shouldBe (want plusOrMinus tolerance) }
    }

    describe("gathered layout") {

        it("#104 TC-22: gathered x spans the width for any length") {
            for (width in gatheredWidths) for (n in counts) {
                val xs = (0 until n).map { fanPose(it, n, null, width, spread = false).xDp }
                val step = (width - 104f) / (n - 1)

                xs.first() shouldBe (0f plusOrMinus tolerance)
                xs.last() shouldBe (width - 104f plusOrMinus tolerance)
                for (i in 0 until n) xs[i] shouldBe (i * step plusOrMinus tolerance)
                xs.zipWithNext { a, b -> (b >= a) shouldBe true }
                xs.all { it >= -tolerance && it <= width - 104f + tolerance } shouldBe true
            }
        }

        it("#104 TC-23: gathered rotation totals at most 20 degrees") {
            for (n in counts) {
                val rot = (0 until n).map { fanPose(it, n, null, 288f, spread = false).rotationDeg }
                val perStep = min(5f, 20f / (n - 1))

                rot.zipWithNext { a, b -> (b - a) shouldBe (perStep plusOrMinus tolerance) }
                val total = rot.last() - rot.first()
                total shouldBe (min(5f * (n - 1), 20f) plusOrMinus tolerance)
                (total <= 20f + tolerance) shouldBe true
                for (i in 0 until n) rot[i] shouldBe (-rot[n - 1 - i] plusOrMinus tolerance)
                if (n % 2 == 1) rot[n / 2] shouldBe (0f plusOrMinus tolerance)
            }
        }

        it("#104 TC-24: known poses match the reference") {
            val two = (0..1).map { fanPose(it, 2, null, 288f, spread = false) }
            two.map { it.xDp }.shouldBeNear(listOf(0f, 184f))
            two.map { it.rotationDeg }.shouldBeNear(listOf(-2.5f, 2.5f))

            val three = (0..2).map { fanPose(it, 3, null, 288f, spread = false) }
            three.map { it.xDp }.shouldBeNear(listOf(0f, 92f, 184f))
            three.map { it.rotationDeg }.shouldBeNear(listOf(-5f, 0f, 5f))

            val five = (0..4).map { fanPose(it, 5, null, 288f, spread = false) }
            five.map { it.rotationDeg }.shouldBeNear(listOf(-10f, -5f, 0f, 5f, 10f))

            val forty = fanPose(0, 40, null, 288f, spread = false)
            val last = fanPose(39, 40, null, 288f, spread = false)
            forty.rotationDeg shouldBe (-10f plusOrMinus tolerance)
            last.rotationDeg shouldBe (10f plusOrMinus tolerance)
            last.xDp shouldBe (184f plusOrMinus tolerance)
        }

        it("#104 TC-25: only this book is raised") {
            for (current in listOf(0, 2, 4)) {
                val gathered = (0..4).map { fanPose(it, 5, current, 288f, spread = false).yDp }
                gathered.forEachIndexed { i, y ->
                    y shouldBe (if (i == current) -10f plusOrMinus tolerance else 0f plusOrMinus tolerance)
                }
                (0..4).map { fanPose(it, 5, current, 288f, spread = true).yDp }.forEach {
                    it shouldBe (0f plusOrMinus tolerance)
                }
            }
        }

        it("#104 TC-26: this book is on top and z is the same in both states") {
            for (n in counts) for (current in 0 until n) {
                for (i in 0 until n) {
                    val z = fanPose(i, n, current, 288f, spread = false).z
                    z shouldBe (n - abs(i - current))
                    fanPose(i, n, current, 288f, spread = true).z shouldBe z
                }
                fanPose(current, n, current, 288f, spread = false).z shouldBe n
            }
        }

        it("#104 TC-27: an absent book raises nothing") {
            for (n in counts) {
                for (i in 0 until n) {
                    val absent = fanPose(i, n, null, 288f, spread = false)
                    val asFirst = fanPose(i, n, 0, 288f, spread = false)
                    absent.yDp shouldBe (0f plusOrMinus tolerance)
                    absent.z shouldBe (n - i)
                    absent.xDp shouldBe (asFirst.xDp plusOrMinus tolerance)
                    absent.rotationDeg shouldBe (asFirst.rotationDeg plusOrMinus tolerance)
                }
            }
        }
    }

    describe("spread layout") {

        it("#104 TC-28: spread cards sit 120 dp apart unrotated") {
            for (n in counts) for (i in 0 until n) {
                val narrow = fanPose(i, n, 0, 288f, spread = true)
                val wide = fanPose(i, n, 0, 1000f, spread = true)
                narrow.xDp shouldBe (i * 120f plusOrMinus tolerance)
                narrow.rotationDeg shouldBe (0f plusOrMinus tolerance)
                narrow.yDp shouldBe (0f plusOrMinus tolerance)
                narrow shouldBe wide
            }
        }

        it("#104 TC-30: interpolation runs from gathered to spread") {
            for (n in listOf(5, 40)) {
                val current = 2
                for (i in 0 until n) {
                    val from = fanPose(i, n, current, 288f, spread = false)
                    val to = fanPose(i, n, current, 288f, spread = true)
                    for (t in listOf(0f, 0.25f, 0.5f, 1f)) {
                        val p = lerpPose(from, to, t)
                        p.xDp shouldBe ((from.xDp + (to.xDp - from.xDp) * t) plusOrMinus tolerance)
                        p.yDp shouldBe ((from.yDp + (to.yDp - from.yDp) * t) plusOrMinus tolerance)
                        p.rotationDeg shouldBe ((from.rotationDeg + (to.rotationDeg - from.rotationDeg) * t) plusOrMinus tolerance)
                        p.z shouldBe from.z
                    }
                    lerpPose(from, to, 0f).xDp shouldBe (from.xDp plusOrMinus tolerance)
                    lerpPose(from, to, 1f).xDp shouldBe (to.xDp plusOrMinus tolerance)
                    lerpPose(from, to, 1f).yDp shouldBe (to.yDp plusOrMinus tolerance)
                    lerpPose(from, to, 1f).rotationDeg shouldBe (to.rotationDeg plusOrMinus tolerance)
                }
            }
        }

        it("#104 TC-31: the spread width is n times 120 minus 16") {
            spreadWidthDp(2) shouldBe (224f plusOrMinus tolerance)
            spreadWidthDp(40) shouldBe (4784f plusOrMinus tolerance)
            for (n in counts) spreadWidthDp(n) shouldBe (n * 120f - 16f plusOrMinus tolerance)
        }

        it("#104 TC-32: the scroll target centres this book within range") {
            val viewports = listOf(320f, 411f, 600f)
            for (n in counts) for (viewport in viewports) {
                val content = spreadWidthDp(n) + 32f
                val maxScroll = max(0f, content - viewport)

                spreadScrollTargetDp(0, n, viewport) shouldBe (0f plusOrMinus tolerance)
                for (c in 0 until n) {
                    val got = spreadScrollTargetDp(c, n, viewport)
                    (got >= -tolerance && got <= maxScroll + tolerance) shouldBe true
                    val centred = 16f + 120f * c + 52f - viewport / 2
                    if (centred in 0f..maxScroll) got shouldBe (centred plusOrMinus tolerance)
                }
                val lastCentred = 16f + 120f * (n - 1) + 52f - viewport / 2
                if (lastCentred >= maxScroll) {
                    spreadScrollTargetDp(n - 1, n, viewport) shouldBe (maxScroll plusOrMinus tolerance)
                }
            }
            spreadScrollTargetDp(20, 40, 320f) shouldBe (2308f plusOrMinus tolerance)
            spreadScrollTargetDp(39, 40, 320f) shouldBe (4496f plusOrMinus tolerance)
            spreadScrollTargetDp(1, 2, 600f) shouldBe 0f
            spreadScrollTargetDp(null, 40, 320f) shouldBe 0f
        }

        it("#104 TC-33: motion constants match the design") {
            SeriesFanLayout.CARD_WIDTH_DP shouldBe 104f
            SeriesFanLayout.COVER_HEIGHT_DP shouldBe 152f
            SeriesFanLayout.SPREAD_STEP_DP shouldBe 120f
            SeriesFanLayout.GATHERED_HEIGHT_DP shouldBe 176f
            SeriesFanLayout.SPREAD_HEIGHT_DP shouldBe 236f
            SeriesFanLayout.RAISE_DP shouldBe 10f
            SeriesFanLayout.MAX_TURN_DEG shouldBe 5f
            SeriesFanLayout.TOTAL_TURN_DEG shouldBe 20f
            SeriesFanLayout.EDGE_DP shouldBe 16f
            SeriesFanLayout.ANIMATION_MILLIS shouldBe 620
            SeriesFanLayout.TEXT_FADE_MILLIS shouldBe 260

            FanEasing.transform(0f) shouldBe (0f plusOrMinus tolerance)
            FanEasing.transform(1f) shouldBe (1f plusOrMinus tolerance)
            (0..20).map { FanEasing.transform(it / 20f) }.zipWithNext { a, b -> (b >= a - 1e-6f) shouldBe true }
            (FanEasing.transform(0.25f) > 0.5f) shouldBe true
        }
    }

    describe("degenerate input") {

        it("#104 TC-29: a narrow or single-card hand never divides by zero") {
            for (width in listOf(104f, 60f, 0f)) for (n in counts) {
                for (i in 0 until n) {
                    val pose = fanPose(i, n, null, width, spread = false)
                    pose.xDp shouldBe 0f
                    pose.rotationDeg.isNaN() shouldBe false
                }
            }
            val single = fanPose(0, 1, 0, 288f, spread = false)
            single.xDp shouldBe (0f plusOrMinus tolerance)
            single.rotationDeg shouldBe (0f plusOrMinus tolerance)
        }
    }
})
