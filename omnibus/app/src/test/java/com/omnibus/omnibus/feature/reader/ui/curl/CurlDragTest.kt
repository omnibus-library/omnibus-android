package com.omnibus.omnibus.feature.reader.ui.curl

import com.omnibus.omnibus.feature.reader.ui.curl.CurlDecision.Cancel
import com.omnibus.omnibus.feature.reader.ui.curl.CurlDecision.Commit
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.numericFloat
import io.kotest.property.checkAll

/** A drag over [rect], from a touch-down at ([downX], [downY]), and the finger's position at the end of it. */
private data class DragCase(val drag: CurlDrag, val downX: Float, val downY: Float, val x: Float, val y: Float)

/** A drag in direction [dir] from a touch-down at fractions ([fx], [fy]) of [rect]. */
private fun dragAt(dir: Int, rect: CurlRect, fx: Float, fy: Float): CurlDrag =
    CurlDrag(dir, rect, rect.left + fx * rect.width, rect.top + fy * rect.height)

/**
 * A drag in direction [dir] over [rect]. The finger travels [travel] widths against the turn (so it stays on the side
 * the turn moves toward) and [verticalFraction] heights down.
 */
private fun dragOver(dir: Int, rect: CurlRect, fx: Float, fy: Float, travel: Float, verticalFraction: Float): DragCase {
    val downX = rect.left + fx * rect.width
    val downY = rect.top + fy * rect.height
    return DragCase(
        drag = CurlDrag(dir, rect, downX, downY),
        downX = downX,
        downY = downY,
        x = downX - dir * travel * rect.width,
        y = downY + verticalFraction * rect.height,
    )
}

private fun Vec.shouldBeNear(expected: Vec, tolerance: Float) {
    x shouldBe (expected.x plusOrMinus tolerance)
    y shouldBe (expected.y plusOrMinus tolerance)
}

/** Positions are compared to this many pixels. Coordinates reach a few thousand, so floats are good to far less. */
private const val PIXELS = 0.01f

private fun unit() = Arb.numericFloat(0f, 1f)

private fun signedUnit() = Arb.numericFloat(-1f, 1f)

class CurlDragTest : DescribeSpec({

    describe("folding the page under the finger") {
        context("for a forward turn") {
            it("TC-310: carries the fold's corner with the finger's horizontal travel") {
                checkAll(arbCurlRect(), unit(), unit(), unit(), signedUnit()) { rect, fx, fy, travel, vertical ->
                    val case = dragOver(1, rect, fx, fy, travel, vertical)

                    val fold = case.drag.fold(case.x, case.y)

                    fold.x shouldBe (case.drag.corner.x + (case.x - case.downX) plusOrMinus PIXELS)
                }
            }

            it("TC-310: damps the fold's vertical travel to 0.35 of the finger's") {
                checkAll(arbCurlRect(), unit(), unit(), unit(), signedUnit()) { rect, fx, fy, travel, vertical ->
                    val case = dragOver(1, rect, fx, fy, travel, vertical)

                    val fold = case.drag.fold(case.x, case.y)

                    fold.y shouldBe (case.drag.corner.y + 0.35f * (case.y - case.downY) plusOrMinus PIXELS)
                }
            }

            it("TC-310: keeps the fold level for a purely horizontal drag") {
                checkAll(arbCurlRect(), unit(), unit(), unit()) { rect, fx, fy, travel ->
                    val case = dragOver(1, rect, fx, fy, travel, 0f)

                    case.drag.fold(case.x, case.y).y shouldBe case.drag.corner.y
                }
            }
        }

        context("for a back turn") {
            it("TC-310: carries the fold from the turned position with twice the finger's horizontal travel") {
                checkAll(arbCurlRect(), unit(), unit(), unit(), signedUnit()) { rect, fx, fy, travel, vertical ->
                    val case = dragOver(-1, rect, fx, fy, travel, vertical)

                    val fold = case.drag.fold(case.x, case.y)

                    fold.x shouldBe (case.drag.turned.x + 2 * (case.x - case.downX) plusOrMinus PIXELS)
                }
            }

            it("TC-310: damps the fold's vertical travel to 0.7 of the finger's") {
                checkAll(arbCurlRect(), unit(), unit(), unit(), signedUnit()) { rect, fx, fy, travel, vertical ->
                    val case = dragOver(-1, rect, fx, fy, travel, vertical)

                    val fold = case.drag.fold(case.x, case.y)

                    fold.y shouldBe (case.drag.corner.y + 0.7f * (case.y - case.downY) plusOrMinus PIXELS)
                }
            }

            it("TC-310: keeps the fold level for a purely horizontal drag") {
                checkAll(arbCurlRect(), unit(), unit(), unit()) { rect, fx, fy, travel ->
                    val case = dragOver(-1, rect, fx, fy, travel, 0f)

                    case.drag.fold(case.x, case.y).y shouldBe case.drag.corner.y
                }
            }
        }
    }

    describe("releasing a drag") {
        context("when the release is slow") {
            it("TC-311: commits at or past halfway") {
                checkAll(Arb.numericFloat(0.5f, 1f), Arb.numericFloat(-999f, 999f)) { progress, velocity ->
                    CurlDrag.release(1, progress, velocity) shouldBe Commit
                }
            }

            it("TC-311: cancels short of halfway") {
                checkAll(Arb.numericFloat(0f, 0.49f), Arb.numericFloat(-999f, 999f)) { progress, velocity ->
                    CurlDrag.release(1, progress, velocity) shouldBe Cancel
                }
            }
        }

        context("when the release is a flick") {
            it("TC-311: commits a flick toward the turn, at any progress") {
                checkAll(unit(), Arb.numericFloat(1_000f, 5_000f)) { progress, speed ->
                    CurlDrag.release(1, progress, -speed) shouldBe Commit
                    CurlDrag.release(-1, progress, speed) shouldBe Commit
                }
            }

            it("TC-311: cancels a flick away from the turn, at any progress") {
                checkAll(unit(), Arb.numericFloat(1_000f, 5_000f)) { progress, speed ->
                    CurlDrag.release(1, progress, speed) shouldBe Cancel
                    CurlDrag.release(-1, progress, -speed) shouldBe Cancel
                }
            }
        }
    }

    describe("the fold a release settles to") {
        context("for a forward turn") {
            it("TC-311: is the turned fold when the turn commits") {
                checkAll(arbCurlRect(), unit(), unit()) { rect, fx, fy ->
                    val drag = dragAt(1, rect, fx, fy)

                    drag.target(Commit) shouldBe drag.turned
                }
            }

            it("TC-311: is the corner when the turn is cancelled") {
                checkAll(arbCurlRect(), unit(), unit()) { rect, fx, fy ->
                    val drag = dragAt(1, rect, fx, fy)

                    drag.target(Cancel) shouldBe drag.corner
                }
            }
        }

        context("for a back turn") {
            it("TC-311: is the corner when the turn commits") {
                checkAll(arbCurlRect(), unit(), unit()) { rect, fx, fy ->
                    val drag = dragAt(-1, rect, fx, fy)

                    drag.target(Commit) shouldBe drag.corner
                }
            }

            it("TC-311: is the turned fold when the turn is cancelled") {
                checkAll(arbCurlRect(), unit(), unit()) { rect, fx, fy ->
                    val drag = dragAt(-1, rect, fx, fy)

                    drag.target(Cancel) shouldBe drag.turned
                }
            }
        }
    }

    describe("the tap fold") {
        context("for a forward turn") {
            it("TC-312: starts at the corner") {
                checkAll(arbCurlRect()) { rect ->
                    CurlDrag.tapFold(1, rect, 0f).shouldBeNear(Vec(rect.right, rect.top + 0.7f * rect.height), PIXELS)
                }
            }

            it("TC-312: ends at the turned fold, on the corner's row") {
                checkAll(arbCurlRect()) { rect ->
                    CurlDrag.tapFold(1, rect, 1f)
                        .shouldBeNear(Vec(2 * rect.left - rect.right - 1f, rect.top + 0.7f * rect.height), PIXELS)
                }
            }

            it("TC-312: never moves back toward the page's right edge as it runs") {
                checkAll(arbCurlRect()) { rect ->
                    val xs = (0..20).map { i -> CurlDrag.tapFold(1, rect, i / 20f).x }

                    xs.zipWithNext { before, after -> after shouldBeLessThanOrEqualTo (before + PIXELS) }
                }
            }
        }

        context("for a back turn") {
            it("TC-312: runs the forward path in reverse") {
                checkAll(arbCurlRect()) { rect ->
                    (0..20).forEach { i ->
                        val t = i / 20f
                        CurlDrag.tapFold(-1, rect, t).shouldBeNear(CurlDrag.tapFold(1, rect, 1f - t), PIXELS)
                    }
                }
            }

            it("TC-312: ends at the corner") {
                checkAll(arbCurlRect()) { rect ->
                    CurlDrag.tapFold(-1, rect, 1f).shouldBeNear(Vec(rect.right, rect.top + 0.7f * rect.height), PIXELS)
                }
            }
        }
    }
})
