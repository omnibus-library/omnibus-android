package com.omnibus.omnibus.feature.library

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.kotest.property.Exhaustive
import io.kotest.property.arbitrary.filter
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection

/** Whether the point ([px], [py]) is inside the closed loop, by counting crossings of a ray to the right. */
private fun loopContains(corners: List<GridCorner>, px: Double, py: Double): Boolean {
    val crossings = corners.indices.count { i ->
        val a = corners[i]
        val b = corners[(i + 1) % corners.size]
        val ay = a.row.toDouble()
        val by = b.row.toDouble()
        val crossX = a.column + (py - ay) * (b.column - a.column) / (by - ay)
        (ay > py) != (by > py) && px < crossX
    }
    return crossings % 2 == 1
}

/** Whether the point ([px], [py]) is inside the closed polygon, by counting crossings of a ray to the right. */
private fun polygonContains(points: List<OutlinePoint>, px: Float, py: Float): Boolean {
    val crossings = points.indices.count { i ->
        val a = points[i]
        val b = points[(i + 1) % points.size]
        (a.y > py) != (b.y > py) && px < a.x + (py - a.y) * (b.x - a.x) / (b.y - a.y)
    }
    return crossings % 2 == 1
}

/** Whether this stroke's segments pass through the interior of [box]. Its segments are axis-aligned. */
private fun OutlineStroke.crosses(box: CellBox): Boolean {
    val segments = points.zipWithNext() + if (closed) listOf(points.last() to points.first()) else emptyList()
    return segments.any { (a, b) ->
        if (a.y == b.y) {
            a.y > box.top && a.y < box.bottom &&
                minOf(a.x, b.x) < box.right && maxOf(a.x, b.x) > box.left
        } else {
            a.x > box.left && a.x < box.right &&
                minOf(a.y, b.y) < box.bottom && maxOf(a.y, b.y) > box.top
        }
    }
}

class SeriesOutlineTest : DescribeSpec({

    describe("the outline for up to two columns") {

        it("#235 TC-26: is a loop around exactly the run's cells") {
            checkAll(Exhaustive.collection(runs(widths = 1..2, counts = 1..7))) { (columns, start, count) ->
                val corners = seriesOutline(start, count, columns).shouldBeTypeOf<SeriesOutlineShape.Loop>().corners
                val last = (start + count - 1) / columns

                val inside = (-1..last + 1).flatMap { row -> (-1..columns).map { column -> row to column } }
                    .filter { (row, column) -> loopContains(corners, column + 0.5, row + 0.5) }

                inside shouldBe runCells(columns, start, count)
            }
        }

        it("#235 TC-26: has only axis-aligned edges") {
            checkAll(Exhaustive.collection(runs(widths = 1..2, counts = 1..7))) { (columns, start, count) ->
                val corners = seriesOutline(start, count, columns).shouldBeTypeOf<SeriesOutlineShape.Loop>().corners

                val edges = corners.zipWithNext() + (corners.last() to corners.first())
                edges.all { (a, b) -> a.column == b.column || a.row == b.row } shouldBe true
            }
        }
    }

    describe("the outline for three or more columns") {

        it("#235 TC-27: is one container per row of the run, covering exactly the run's cells") {
            checkAll(Exhaustive.collection(runs(widths = 3..6, counts = 1..14))) { (columns, start, count) ->
                val rows = seriesOutline(start, count, columns).shouldBeTypeOf<SeriesOutlineShape.Rows>().rows

                rows.flatMap { row -> (row.startColumn until row.endColumn).map { row.row to it } } shouldBe
                    runCells(columns, start, count)
            }
        }

        it("#235 TC-27: is open toward the run's continuation on every row but the first and last") {
            checkAll(Exhaustive.collection(runs(widths = 3..6, counts = 1..14))) { (columns, start, count) ->
                val rows = seriesOutline(start, count, columns).shouldBeTypeOf<SeriesOutlineShape.Rows>().rows

                rows.map { it.openStart } shouldBe rows.indices.map { it > 0 }
                rows.map { it.openEnd } shouldBe rows.indices.map { it < rows.lastIndex }
            }
        }

        context("when the run fits in one row") {
            it("#235 TC-27: is a closed box") {
                checkAll(Exhaustive.collection(singleRowRuns)) { (columns, start, count) ->
                    val row = seriesOutline(start, count, columns).shouldBeTypeOf<SeriesOutlineShape.Rows>().rows.single()

                    (row.openStart || row.openEnd) shouldBe false
                }
            }
        }
    }

    describe("the strokes in pixels") {

        it("#235 TC-28: draws no stroke through a cell, of the run or of a neighbouring one") {
            checkAll(runCases) { case ->
                val strokes = outlineStrokes(seriesOutline(case.start, case.count, case.columns), case.grid)

                strokes.any { stroke -> case.neighbourhood.any { stroke.crosses(it) } } shouldBe false
            }
        }

        it("#235 TC-28: keeps every cell of the run inside the strokes' bounds") {
            checkAll(runCases) { case ->
                val points = outlineStrokes(seriesOutline(case.start, case.count, case.columns), case.grid).flatMap { it.points }
                val minX = points.minOf { it.x }
                val maxX = points.maxOf { it.x }
                val minY = points.minOf { it.y }
                val maxY = points.maxOf { it.y }

                case.runBoxes.all { box ->
                    box.left >= minX && box.right <= maxX && box.top >= minY && box.bottom <= maxY
                } shouldBe true
            }
        }
    }

    describe("the fills for up to two columns") {

        it("#258 TC-3: is the outline loop") {
            checkAll(Exhaustive.collection(runs(widths = 1..2, counts = 1..14))) { (columns, start, count) ->
                val shape = seriesOutline(start, count, columns)
                val rows = (start + count - 1) / columns + 1
                val grid = OutlineGrid(
                    contentLeft = CONTENT_LEFT,
                    slotWidth = 120f,
                    columnGap = 16f,
                    rowGap = 26f,
                    rowTops = List(rows) { FIRST_TOP + it * 300f },
                    rowBottoms = List(rows) { FIRST_TOP + it * 300f + 200f },
                    edgeLeft = 0f,
                    edgeRight = CONTENT_LEFT * 2 + columns * 120f + (columns - 1) * 16f,
                )

                outlineFills(shape, grid).single() shouldBe outlineStrokes(shape, grid).single().points
            }
        }
    }

    describe("the fills for three or more columns") {

        it("#258 TC-4: is one container per row of the run") {
            checkAll(runCases.filter { it.columns >= 3 }) { case ->
                val shape = seriesOutline(case.start, case.count, case.columns).shouldBeTypeOf<SeriesOutlineShape.Rows>()

                outlineFills(shape, case.grid).size shouldBe shape.rows.size
            }
        }

        it("#258 TC-4: runs each open side to the screen edge") {
            checkAll(runCases.filter { it.columns >= 3 }) { case ->
                val shape = seriesOutline(case.start, case.count, case.columns).shouldBeTypeOf<SeriesOutlineShape.Rows>()
                val fills = outlineFills(shape, case.grid)

                shape.rows.zip(fills).forEach { (row, fill) ->
                    val strokePoints = outlineStrokes(SeriesOutlineShape.Rows(listOf(row)), case.grid).flatMap { it.points }

                    fill.minOf { it.x } shouldBe (if (row.openStart) case.grid.edgeLeft else strokePoints.minOf { it.x })
                    fill.maxOf { it.x } shouldBe (if (row.openEnd) case.grid.edgeRight else strokePoints.maxOf { it.x })
                }
            }
        }

        context("when the run fits in one row") {
            it("#258 TC-4: is a closed box") {
                checkAll(runCases.filter { it.columns >= 3 && it.start + it.count <= it.columns }) { case ->
                    val shape = seriesOutline(case.start, case.count, case.columns)

                    outlineFills(shape, case.grid).single() shouldBe outlineStrokes(shape, case.grid).single().points
                }
            }
        }
    }

    describe("the fills in pixels") {

        it("#258 TC-1: covers exactly the run's cells") {
            checkAll(runCases) { case ->
                val polygons = outlineFills(seriesOutline(case.start, case.count, case.columns), case.grid)
                fun covered(box: CellBox): Boolean = polygons.any { polygon ->
                    polygonContains(polygon, (box.left + box.right) / 2, (box.top + box.bottom) / 2)
                }

                case.runBoxes.all { covered(it) } shouldBe true
                (case.neighbourhood - case.runBoxes.toSet()).any { covered(it) } shouldBe false
            }
        }

        it("#258 TC-2: stays within the strokes' bounds") {
            checkAll(runCases) { case ->
                val shape = seriesOutline(case.start, case.count, case.columns)
                val points = outlineStrokes(shape, case.grid).flatMap { it.points }
                val minX = points.minOf { it.x }
                val maxX = points.maxOf { it.x }
                val minY = points.minOf { it.y }
                val maxY = points.maxOf { it.y }

                outlineFills(shape, case.grid).all { polygon ->
                    polygon.all { it.x >= minX && it.x <= maxX && it.y >= minY && it.y <= maxY }
                } shouldBe true
            }
        }

        it("#258 TC-2: is a closed polygon of at least four axis-aligned edges") {
            checkAll(runCases) { case ->
                val polygons = outlineFills(seriesOutline(case.start, case.count, case.columns), case.grid)

                polygons.all { polygon ->
                    val edges = polygon.zipWithNext() + (polygon.last() to polygon.first())
                    polygon.size >= 4 && edges.all { (a, b) -> a.x == b.x || a.y == b.y }
                } shouldBe true
            }
        }

        it("#258 TC-5: is the stroke's rectangle for a one-cell run") {
            checkAll(runCases.filter { it.count == 1 }) { case ->
                val shape = seriesOutline(case.start, case.count, case.columns)
                val fill = outlineFills(shape, case.grid).single()
                val box = case.runBoxes.single()

                fill shouldBe outlineStrokes(shape, case.grid).single().points
                fill.size shouldBe 4
                val containsBox = fill.minOf { it.x } <= box.left && fill.maxOf { it.x } >= box.right &&
                    fill.minOf { it.y } <= box.top && fill.maxOf { it.y } >= box.bottom
                containsBox shouldBe true
            }
        }
    }
})
