package com.omnibus.omnibus.feature.reader.ui.curl

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.numericFloat
import io.kotest.property.checkAll
import kotlin.math.abs

class CurlGeometryTest : DescribeSpec({

    /** Shoelace area of [polygon]; a polygon with no points has none. */
    fun areaOf(polygon: List<Vec>): Double = abs(
        polygon.indices.sumOf { i ->
            val p = polygon[i]
            val q = polygon[(i + 1) % polygon.size]
            p.x.toDouble() * q.y - q.x.toDouble() * p.y
        } / 2,
    )

    /** A corner on the right edge of [rect] at [cornerFraction] of its height, and a fold offset from it. */
    fun sceneOf(rect: CurlRect, cornerFraction: Float, offsetX: Float, offsetY: Float): CurlScene {
        val corner = Vec(rect.right, rect.top + cornerFraction * rect.height)
        val fold = Vec(corner.x + offsetX * rect.width, corner.y + offsetY * rect.height)
        return CurlScene(rect, rect, corner, fold)
    }

    /** The fraction of the rect's height the corner sits at. Edge values put it on the top or bottom edge. */
    fun cornerFractions() = Arb.numericFloat(0f, 1f)

    /**
     * An offset of the fold from the corner, in rect widths or heights. At least 0.03 of a side, so the fold is at
     * least 1.5 px from the corner on a rect 50 px wide, and never flat.
     */
    fun foldOffsets() = Arb.numericFloat(-3f, 3f).filter { abs(it) >= 0.03f }

    describe("the fold of a curl") {
        it("TC-309: keeps and lifts the whole page, with no overlap, for any fold point") {
            checkAll(arbCurlRect(), cornerFractions(), foldOffsets(), foldOffsets()) { rect, fraction, offsetX, offsetY ->
                val geometry = CurlGeometry.of(sceneOf(rect, fraction, offsetX, offsetY))

                val total = areaOf(geometry.kept) + areaOf(geometry.far)
                val pageArea = rect.width.toDouble() * rect.height
                total shouldBe (pageArea plusOrMinus 1e-3 * pageArea)
            }
        }

        it("TC-309: reflects the corner onto the fold point") {
            checkAll(arbCurlRect(), cornerFractions(), foldOffsets(), foldOffsets()) { rect, fraction, offsetX, offsetY ->
                val scene = sceneOf(rect, fraction, offsetX, offsetY)
                val geometry = CurlGeometry.of(scene)
                val tolerance = 1e-3f * minOf(rect.width, rect.height)

                val reflected = geometry.reflect(scene.corner)

                reflected.x shouldBe (scene.fold.x plusOrMinus tolerance)
                reflected.y shouldBe (scene.fold.y plusOrMinus tolerance)
            }
        }

        it("TC-309: reflects every point of the page back to itself after two reflections") {
            checkAll(arbCurlRect(), cornerFractions(), foldOffsets(), foldOffsets()) { rect, fraction, offsetX, offsetY ->
                val geometry = CurlGeometry.of(sceneOf(rect, fraction, offsetX, offsetY))
                val tolerance = 1e-3f * minOf(rect.width, rect.height)

                rect.corners().forEach { corner ->
                    val twice = geometry.reflect(geometry.reflect(corner))
                    twice.x shouldBe (corner.x plusOrMinus tolerance)
                    twice.y shouldBe (corner.y plusOrMinus tolerance)
                }
            }
        }

        context("when the fold point is close to the corner") {
            it("TC-309: is flat at 0.99 px") {
                checkAll(arbCurlRect()) { rect ->
                    val corner = Vec(rect.right, rect.top)

                    CurlGeometry.of(CurlScene(rect, rect, corner, Vec(corner.x - 0.99f, corner.y))).isFlat shouldBe true
                }
            }

            it("TC-309: is not flat at 1.01 px") {
                checkAll(arbCurlRect()) { rect ->
                    val corner = Vec(rect.right, rect.top)

                    CurlGeometry.of(CurlScene(rect, rect, corner, Vec(corner.x - 1.01f, corner.y))).isFlat shouldBe false
                }
            }
        }
    }
})
