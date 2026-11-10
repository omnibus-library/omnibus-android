package com.omnibus.omnibus.feature.library

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TOLERANCE = 1e-4f

/** Whether [points] equal [expected] vertex by vertex, within [TOLERANCE]. */
private fun List<OutlinePoint>.isCloseTo(expected: List<OutlinePoint>): Boolean = size == expected.size &&
    zip(expected).all { (a, b) -> abs(a.x - b.x) <= TOLERANCE && abs(a.y - b.y) <= TOLERANCE }

/** The stroke and fill polygons of [shape] in [grid], each as its own closed list of points. */
private fun polygonsOf(shape: SeriesOutlineShape, grid: OutlineGrid): List<List<OutlinePoint>> =
    outlineStrokes(shape, grid).map { it.points } + outlineFills(shape, grid)

class SeriesUnfoldMotionTest : DescribeSpec({

    describe("the container and reflow progress") {

        it("#258 TC-9: is 0 at the start of t") {
            unfoldFrame(0f).containerProgress shouldBe 0f
            unfoldFrame(0f).reflowProgress shouldBe 0f
        }

        it("#258 TC-9: is exactly 1 from 70% of t onwards") {
            checkAll(Arb.float(SeriesUnfoldTiming.GROW_END, 1.5f, includeNaNs = false)) { t ->
                unfoldFrame(t).containerProgress shouldBe 1f
                unfoldFrame(t).reflowProgress shouldBe 1f
            }
        }

        it("#258 TC-9: stays within 0 to 1 and never overshoots") {
            checkAll(Arb.float(0f, 1f, includeNaNs = false)) { t ->
                val frame = unfoldFrame(t)

                (frame.containerProgress in 0f..1f) shouldBe true
                (frame.reflowProgress in 0f..1f) shouldBe true
            }
        }

        it("#258 TC-9: rises monotonically with t") {
            checkAll(Arb.float(0f, 1f, includeNaNs = false), Arb.float(0f, 1f, includeNaNs = false)) { a, b ->
                val (earlier, later) = listOf(a, b).sorted()

                (unfoldFrame(later).containerProgress + TOLERANCE >= unfoldFrame(earlier).containerProgress) shouldBe true
                (unfoldFrame(later).reflowProgress + TOLERANCE >= unfoldFrame(earlier).reflowProgress) shouldBe true
            }
        }

        it("#258 TC-9: moves the container and the reflow in step") {
            checkAll(Arb.float(0f, 1f, includeNaNs = false)) { t ->
                unfoldFrame(t).reflowProgress shouldBe unfoldFrame(t).containerProgress
            }
        }

        it("#258 TC-9: is front-loaded, past half way at 35% of t") {
            unfoldFrame(0.35f).containerProgress shouldBeGreaterThan 0.5f
        }
    }

    describe("the timeline windows") {

        it("#258 TC-10: fades the container in over the first 20% of t") {
            unfoldFrame(0f).containerAlpha shouldBe 0f
            unfoldFrame(0.1f).containerAlpha shouldBe (0.5f plusOrMinus TOLERANCE)
            checkAll(Arb.float(0.2f, 1.5f, includeNaNs = false)) { t ->
                unfoldFrame(t).containerAlpha shouldBe 1f
            }
        }

        it("#258 TC-10: fades the stack caption out by 25% of t") {
            unfoldFrame(0f).stackCaptionAlpha shouldBe 1f
            unfoldFrame(0.125f).stackCaptionAlpha shouldBe (0.5f plusOrMinus TOLERANCE)
            checkAll(Arb.float(0.25f, 1.5f, includeNaNs = false)) { t ->
                unfoldFrame(t).stackCaptionAlpha shouldBe 0f
            }
        }

        it("#258 TC-10: fades the Summary in from 30% to 70% of t, rising 6 dp to rest") {
            checkAll(Arb.float(-0.5f, 0.3f, includeNaNs = false)) { t ->
                unfoldFrame(t).summaryAlpha shouldBe 0f
            }
            unfoldFrame(0.3f).summaryRiseDp shouldBe (6f plusOrMinus TOLERANCE)
            checkAll(Arb.float(0.7f, 1.5f, includeNaNs = false)) { t ->
                unfoldFrame(t).summaryAlpha shouldBe (1f plusOrMinus TOLERANCE)
                unfoldFrame(t).summaryRiseDp shouldBe (0f plusOrMinus TOLERANCE)
            }
        }

        it("#258 TC-10: fades the member captions in from 65% of t") {
            checkAll(Arb.float(-0.5f, 0.65f, includeNaNs = false)) { t ->
                unfoldFrame(t).memberCaptionAlpha shouldBe 0f
            }
            unfoldFrame(1f).memberCaptionAlpha shouldBe 1f
        }

        it("#258 TC-10: keeps every channel within its range for any t") {
            checkAll(Arb.float(-0.5f, 1.5f, includeNaNs = false)) { t ->
                val frame = unfoldFrame(t)

                listOf(frame.containerAlpha, frame.summaryAlpha, frame.stackCaptionAlpha, frame.memberCaptionAlpha)
                    .all { it in 0f..1f } shouldBe true
                (frame.summaryRiseDp in 0f..6f) shouldBe true
            }
        }
    }

    describe("the cover stagger") {

        it("#258 TC-11: holds cover 0 at 0 until t reaches 0") {
            checkAll(Arb.float(-0.5f, 0f, includeNaNs = false)) { t ->
                coverProgress(t, 0) shouldBe 0f
            }
        }

        it("#258 TC-11: holds cover 1 at 0 until t reaches 8%") {
            checkAll(Arb.float(-0.5f, 0.08f, includeNaNs = false)) { t ->
                coverProgress(t, 1) shouldBe 0f
            }
        }

        it("#258 TC-11: holds cover 2 at 0 until t reaches 16%") {
            checkAll(Arb.float(-0.5f, 0.16f, includeNaNs = false)) { t ->
                coverProgress(t, 2) shouldBe 0f
            }
        }

        it("#258 TC-11: follows the container's curve, shifted by 8% of t per cover index") {
            checkAll(Arb.int(0..2), Arb.float(0f, 1f, includeNaNs = false)) { index, t ->
                val shifted = t - SeriesUnfoldTiming.COVER_STAGGER * index
                coverProgress(t, index) shouldBe (unfoldFrame(shifted).containerProgress plusOrMinus TOLERANCE)
            }
        }

        it("#258 TC-11: is 1 once t reaches 70% plus the cover's offset") {
            (0..2).forEach { index ->
                val arrival = SeriesUnfoldTiming.GROW_END + SeriesUnfoldTiming.COVER_STAGGER * index
                checkAll(Arb.float(arrival, 1.5f, includeNaNs = false)) { t ->
                    coverProgress(t, index) shouldBe (1f plusOrMinus TOLERANCE)
                }
            }
        }

        it("#258 TC-11: never has a later cover ahead of an earlier one") {
            checkAll(Arb.int(0..1), Arb.float(0f, 1.5f, includeNaNs = false)) { index, t ->
                (coverProgress(t, index + 1) <= coverProgress(t, index) + TOLERANCE) shouldBe true
            }
        }

        context("when the cover is past the third") {
            it("#258 TC-17: starts its progress with cover 3, so it stops staggering") {
                checkAll(Arb.int(3..12), Arb.float(-0.5f, 1.5f, includeNaNs = false)) { index, t ->
                    coverProgress(t, index) shouldBe coverProgress(t, 3)
                }
            }

            it("#258 TC-17: is posed like cover 2 at the stack, with no alpha") {
                checkAll(Arb.int(3..12), Arb.float(20f, 400f, includeNaNs = false)) { index, width ->
                    coverPose(index, 0f, width, 37f, -12f) shouldBe coverPose(2, 0f, width, 37f, -12f).copy(alpha = 0f)
                }
            }

            it("#258 TC-17: fades its alpha in with its own progress") {
                checkAll(Arb.int(3..12), Arb.float(0f, 1f, includeNaNs = false)) { index, p ->
                    coverPose(index, p, 120f, 0f, 0f).alpha shouldBe p
                }
            }
        }

        context("when the cover is one of the first three") {
            it("#258 TC-17: is fully opaque at any progress") {
                checkAll(Arb.int(0..2), Arb.float(0f, 1f, includeNaNs = false)) { index, p ->
                    coverPose(index, p, 120f, 0f, 0f).alpha shouldBe 1f
                }
            }
        }
    }

    describe("the container geometry") {

        it("#258 TC-12: starts at the stack cell's container rect, with the same vertex count") {
            checkAll(runCases) { case ->
                val shape = seriesOutline(case.start, case.count, case.columns)
                val rect = stackRect(case.start, case.columns, case.grid)
                val full = polygonsOf(shape, case.grid)

                val start = full.map { collapseToward(it, rect, 0f) }

                start.flatten().bounds() shouldBe rect
                start.map { it.size } shouldBe full.map { it.size }
            }
        }

        it("#258 TC-12: ends at the full outline") {
            checkAll(runCases) { case ->
                val shape = seriesOutline(case.start, case.count, case.columns)
                val rect = stackRect(case.start, case.columns, case.grid)
                val full = polygonsOf(shape, case.grid)

                full.map { collapseToward(it, rect, 1f) }.zip(full).all { (end, expected) ->
                    end.isCloseTo(expected)
                } shouldBe true
            }
        }

        it("#258 TC-13: keeps the bounds at the earlier progress inside those at the later one") {
            checkAll(runCases, Arb.float(0f, 1f, includeNaNs = false), Arb.float(0f, 1f, includeNaNs = false)) { case, a, b ->
                val (earlier, later) = listOf(a, b).sorted()
                val shape = seriesOutline(case.start, case.count, case.columns)
                val rect = stackRect(case.start, case.columns, case.grid)
                val full = polygonsOf(shape, case.grid)

                val early = full.map { collapseToward(it, rect, earlier) }.flatten().bounds()
                val late = full.map { collapseToward(it, rect, later) }.flatten().bounds()

                (early.left + TOLERANCE >= late.left) shouldBe true
                (early.top + TOLERANCE >= late.top) shouldBe true
                (early.right <= late.right + TOLERANCE) shouldBe true
                (early.bottom <= late.bottom + TOLERANCE) shouldBe true
            }
        }

        it("#258 TC-13: keeps every point within the full outline's bounds") {
            checkAll(runCases, Arb.float(0f, 1f, includeNaNs = false)) { case, p ->
                val shape = seriesOutline(case.start, case.count, case.columns)
                val rect = stackRect(case.start, case.columns, case.grid)
                val full = polygonsOf(shape, case.grid).flatten().bounds()

                val moved = polygonsOf(shape, case.grid).map { collapseToward(it, rect, p) }.flatten().bounds()

                (moved.left + TOLERANCE >= full.left) shouldBe true
                (moved.top + TOLERANCE >= full.top) shouldBe true
                (moved.right <= full.right + TOLERANCE) shouldBe true
                (moved.bottom <= full.bottom + TOLERANCE) shouldBe true
            }
        }
    }

    describe("the cover pose") {

        it("#258 TC-14: sits in the stack pose over the anchor cell at progress 0") {
            checkAll(Arb.int(0..2), Arb.float(20f, 400f, includeNaNs = false), Arb.float(-300f, 300f, includeNaNs = false), Arb.float(-300f, 300f, includeNaNs = false)) { index, width, dx, dy ->
                val pose = coverPose(index, 0f, width, dx, dy)

                pose.widthFraction shouldBe ((width - 12f) / width plusOrMinus TOLERANCE)
                pose.dxDp shouldBe (dx + 6f * index plusOrMinus TOLERANCE)
                pose.dyDp shouldBe (dy - 6f * index plusOrMinus TOLERANCE)
            }
        }

        it("#258 TC-14: sits in its own slot, full width, at progress 1") {
            checkAll(Arb.int(0..2), Arb.float(20f, 400f, includeNaNs = false), Arb.float(-300f, 300f, includeNaNs = false), Arb.float(-300f, 300f, includeNaNs = false)) { index, width, dx, dy ->
                val pose = coverPose(index, 1f, width, dx, dy)

                pose.widthFraction shouldBe 1f
                pose.dxDp shouldBe 0f
                pose.dyDp shouldBe 0f
            }
        }

        it("#258 TC-14: moves linearly between the stack pose and the slot pose") {
            checkAll(Arb.int(0..2), Arb.float(20f, 400f, includeNaNs = false), Arb.float(-300f, 300f, includeNaNs = false), Arb.float(-300f, 300f, includeNaNs = false), Arb.float(0f, 1f, includeNaNs = false)) { index, width, dx, dy, p ->
                val start = coverPose(index, 0f, width, dx, dy)
                val end = coverPose(index, 1f, width, dx, dy)
                val pose = coverPose(index, p, width, dx, dy)

                pose.widthFraction shouldBe (start.widthFraction + (end.widthFraction - start.widthFraction) * p plusOrMinus TOLERANCE)
                pose.dxDp shouldBe (start.dxDp + (end.dxDp - start.dxDp) * p plusOrMinus TOLERANCE)
                pose.dyDp shouldBe (start.dyDp + (end.dyDp - start.dyDp) * p plusOrMinus TOLERANCE)
            }
        }

        context("when the cover is the front one") {
            it("#258 TC-15: has no veil at any progress") {
                checkAll(Arb.float(0f, 1f, includeNaNs = false)) { p ->
                    coverPose(0, p, 120f, 0f, 0f).veilAlpha shouldBe 0f
                }
            }
        }

        context("when the cover is behind the front one") {
            it("#258 TC-15: fades the veil out with the cover's progress") {
                checkAll(Arb.int(1..2), Arb.float(0f, 1f, includeNaNs = false)) { index, p ->
                    coverPose(index, p, 120f, 0f, 0f).veilAlpha shouldBe (0.45f * (1f - p) plusOrMinus TOLERANCE)
                }
            }
        }
    }

    describe("the remaining duration") {

        it("#258 TC-16: takes the full duration for a whole sweep and a fraction of it for part of one") {
            remainingMillis(0f, 1f) shouldBe 800
            remainingMillis(1f, 0f) shouldBe 600
            remainingMillis(0.3f, 0f) shouldBe 180
            remainingMillis(0.5f, 0.5f) shouldBe 0
        }

        context("when unfolding") {
            it("#258 TC-16: scales 800 ms by the distance left") {
                checkAll(Arb.float(0f, 1f, includeNaNs = false), Arb.float(0f, 1f, includeNaNs = false)) { a, b ->
                    val (from, to) = listOf(a, b).sorted()

                    remainingMillis(from, to) shouldBe ((to - from) * 800f).roundToInt()
                }
            }
        }

        context("when folding") {
            it("#258 TC-16: scales 600 ms by the distance left") {
                checkAll(Arb.float(0f, 1f, includeNaNs = false), Arb.float(0f, 1f, includeNaNs = false)) { a, b ->
                    val (to, from) = listOf(a, b).sorted()

                    remainingMillis(from, to) shouldBe ((from - to) * 600f).roundToInt()
                }
            }
        }
    }
})
