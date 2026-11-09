package com.omnibus.omnibus.feature.library

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.kotest.property.Arb
import io.kotest.property.Exhaustive
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection

/** The left edge of the grid's content in the generated px layouts. */
private const val CONTENT_LEFT = 20f

/** The top of the first generated row, above the run and its neighbour row. */
private const val FIRST_TOP = 50f

/** Every run of `count` cells from each start column of each grid width in [widths], with counts in [counts]. */
private fun runs(widths: IntRange, counts: IntRange): List<Triple<Int, Int, Int>> = widths.flatMap { columns ->
    (0 until columns).flatMap { start -> counts.map { count -> Triple(columns, start, count) } }
}

/** The runs that fit in one row: every start column and count of 3 to 6 columns, with the whole run in the first row. */
private val singleRowRuns: List<Triple<Int, Int, Int>> =
    runs(widths = 3..6, counts = 1..14).filter { (columns, start, count) -> start + count <= columns }

/** The run's cells as (row, column) pairs in reading order, the run's first row being row 0. */
private fun runCells(columns: Int, start: Int, count: Int): List<Pair<Int, Int>> =
    (0 until count).map { k -> (start + k) / columns to (start + k) % columns }

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

/** A cell in px. */
private data class CellBox(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * A generated run in a generated px layout. [box] is the cell at a row relative to the run's first row (-1 is the row
 * above it) and a column. The grid holds only the run's rows, and the neighbour rows above and below are kept aside.
 */
private class RunCase(
    val columns: Int,
    val start: Int,
    val count: Int,
    val grid: OutlineGrid,
    private val cellWidth: Float,
    private val tops: List<Float>,
    private val bottoms: List<Float>,
) {
    val last: Int = (start + count - 1) / columns

    /** Every cell of the run. */
    val runBoxes: List<CellBox> = runCells(columns, start, count).map { (row, column) -> box(row, column) }

    /** Every cell of the run's rows and of the neighbour rows above and below them. */
    val neighbourhood: List<CellBox> = (-1..last + 1).flatMap { row -> (0 until columns).map { box(row, it) } }

    fun box(row: Int, column: Int): CellBox {
        val left = grid.contentLeft + column * (grid.slotWidth + grid.columnGap)
        return CellBox(left, tops[row + 1], left + cellWidth, bottoms[row + 1])
    }
}

/** Generated runs in grids of 1 to 6 columns, with random slot and cell sizes, gaps and row heights. */
private val runCases: Arb<RunCase> = arbitrary {
    val columns = Arb.int(1..6).bind()
    val start = Arb.int(0 until columns).bind()
    val count = Arb.int(1..14).bind()
    val last = (start + count - 1) / columns
    val slotWidth = Arb.int(80..200).bind().toFloat()
    val cellWidth = slotWidth * Arb.int(70..100).bind() / 100f
    val columnGap = Arb.int(8..24).bind().toFloat()
    val rowGap = Arb.int(10..40).bind().toFloat()
    val heights = List(last + 3) { Arb.int(100..300).bind().toFloat() }
    val tops = heights.runningFold(FIRST_TOP) { top, height -> top + height + rowGap }.dropLast(1)
    val bottoms = heights.zip(tops) { height, top -> top + height }
    RunCase(
        columns = columns,
        start = start,
        count = count,
        grid = OutlineGrid(
            contentLeft = CONTENT_LEFT,
            slotWidth = slotWidth,
            columnGap = columnGap,
            rowGap = rowGap,
            rowTops = tops.subList(1, last + 2),
            rowBottoms = bottoms.subList(1, last + 2),
            edgeLeft = 0f,
            edgeRight = CONTENT_LEFT * 2 + columns * slotWidth + (columns - 1) * columnGap,
        ),
        cellWidth = cellWidth,
        tops = tops,
        bottoms = bottoms,
    )
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
})
