package com.omnibus.omnibus.feature.library

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int

/** The left edge of the grid's content in the generated px layouts. */
internal const val CONTENT_LEFT = 20f

/** The top of the first generated row, above the run and its neighbour row. */
internal const val FIRST_TOP = 50f

/** Every run of `count` cells from each start column of each grid width in [widths], with counts in [counts]. */
internal fun runs(widths: IntRange, counts: IntRange): List<Triple<Int, Int, Int>> = widths.flatMap { columns ->
    (0 until columns).flatMap { start -> counts.map { count -> Triple(columns, start, count) } }
}

/** The runs that fit in one row: every start column and count of 3 to 6 columns, with the whole run in the first row. */
internal val singleRowRuns: List<Triple<Int, Int, Int>> =
    runs(widths = 3..6, counts = 1..14).filter { (columns, start, count) -> start + count <= columns }

/** The run's cells as (row, column) pairs in reading order, the run's first row being row 0. */
internal fun runCells(columns: Int, start: Int, count: Int): List<Pair<Int, Int>> =
    (0 until count).map { k -> (start + k) / columns to (start + k) % columns }

/** A cell in px. */
internal data class CellBox(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * A generated run in a generated px layout. [box] is the cell at a row relative to the run's first row (-1 is the row
 * above it) and a column. The grid holds only the run's rows, and the neighbour rows above and below are kept aside.
 */
internal class RunCase(
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
internal val runCases: Arb<RunCase> = arbitrary {
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
