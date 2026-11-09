package com.omnibus.omnibus.feature.library

/** A grid corner: [column] is a column boundary 0..columns, [row] a row boundary counted from the run's first row. */
data class GridCorner(val column: Int, val row: Int)

/** Cells [startColumn, endColumn) of run row [row]; an open side runs on to the grid's edge. */
data class OutlineRow(
    val row: Int,
    val startColumn: Int,
    val endColumn: Int,
    val openStart: Boolean,
    val openEnd: Boolean,
)

/** The outline of one run of cells, in grid units. */
sealed interface SeriesOutlineShape {

    /** One closed outline around the union of the run's cells (1-2 columns). */
    data class Loop(val corners: List<GridCorner>) : SeriesOutlineShape

    /** One container per row (3 or more columns). */
    data class Rows(val rows: List<OutlineRow>) : SeriesOutlineShape
}

/** The outline of [cellCount] consecutive cells starting at [startColumn] of a [columns]-wide grid. */
fun seriesOutline(startColumn: Int, cellCount: Int, columns: Int): SeriesOutlineShape {
    require(columns >= 1 && startColumn in 0 until columns && cellCount >= 1)
    val s = startColumn
    val last = (s + cellCount - 1) / columns
    val end = (s + cellCount - 1) % columns + 1
    return if (columns <= 2) {
        val raw = if (last == 0) {
            listOf(GridCorner(s, 0), GridCorner(end, 0), GridCorner(end, 1), GridCorner(s, 1))
        } else {
            listOf(
                GridCorner(s, 0),
                GridCorner(columns, 0),
                GridCorner(columns, last),
                GridCorner(end, last),
                GridCorner(end, last + 1),
                GridCorner(0, last + 1),
                GridCorner(0, 1),
                GridCorner(s, 1),
            )
        }
        SeriesOutlineShape.Loop(withoutStraightCorners(raw))
    } else {
        SeriesOutlineShape.Rows(
            (0..last).map { row ->
                OutlineRow(
                    row = row,
                    startColumn = if (row == 0) s else 0,
                    endColumn = if (row == last) end else columns,
                    openStart = row > 0,
                    openEnd = row < last,
                )
            },
        )
    }
}

/** A point of an outline in px. */
data class OutlinePoint(val x: Float, val y: Float)

/** A polyline in px, closed back to its first point when [closed]. */
data class OutlineStroke(val points: List<OutlinePoint>, val closed: Boolean)

/**
 * The grid in px. Column c's slot starts at contentLeft + c * (slotWidth + columnGap). [rowTops]/[rowBottoms] are the
 * run's rows (index 0 = its first row): the top of the cells below their row padding, and the bottom of the tallest
 * cell. [rowGap] is the space between one row's bottom and the next row's top. Open row ends run to [edgeLeft] and
 * [edgeRight].
 */
data class OutlineGrid(
    val contentLeft: Float,
    val slotWidth: Float,
    val columnGap: Float,
    val rowGap: Float,
    val rowTops: List<Float>,
    val rowBottoms: List<Float>,
    val edgeLeft: Float,
    val edgeRight: Float,
)

/** The strokes that draw [shape] in the gutters of [grid], never over a cell. */
fun outlineStrokes(shape: SeriesOutlineShape, grid: OutlineGrid): List<OutlineStroke> = when (shape) {
    is SeriesOutlineShape.Loop -> listOf(
        OutlineStroke(
            points = shape.corners.map { OutlinePoint(grid.columnX(it.column), grid.rowY(it.row)) },
            closed = true,
        ),
    )

    is SeriesOutlineShape.Rows -> shape.rows.flatMap { grid.rowStrokes(it) }
}

/** The x of column boundary [column], in the gutter just before that column. */
private fun OutlineGrid.columnX(column: Int): Float = contentLeft + column * (slotWidth + columnGap) - columnGap / 2

/** The y of row boundary [boundary]: the middle of the gap above or below a run row, or the gap around the run. */
private fun OutlineGrid.rowY(boundary: Int): Float {
    val rows = rowTops.size
    return when (boundary) {
        0 -> rowTops[0] - rowGap / 2
        rows -> rowBottoms[rows - 1] + rowGap / 2
        else -> (rowBottoms[boundary - 1] + rowTops[boundary]) / 2
    }
}

/** One row's container, held in the gaps above and below it. An open side runs to the grid's edge. */
private fun OutlineGrid.rowStrokes(row: OutlineRow): List<OutlineStroke> {
    val top = rowTops[row.row] - rowGap / 4
    val bottom = rowBottoms[row.row] + rowGap / 4
    val left = if (row.openStart) edgeLeft else columnX(row.startColumn)
    val right = if (row.openEnd) edgeRight else columnX(row.endColumn)
    return when {
        !row.openStart && !row.openEnd -> listOf(
            OutlineStroke(
                listOf(OutlinePoint(left, top), OutlinePoint(right, top), OutlinePoint(right, bottom), OutlinePoint(left, bottom)),
                closed = true,
            ),
        )

        row.openEnd && !row.openStart -> listOf(
            OutlineStroke(
                listOf(OutlinePoint(right, top), OutlinePoint(left, top), OutlinePoint(left, bottom), OutlinePoint(right, bottom)),
                closed = false,
            ),
        )

        row.openStart && !row.openEnd -> listOf(
            OutlineStroke(
                listOf(OutlinePoint(left, top), OutlinePoint(right, top), OutlinePoint(right, bottom), OutlinePoint(left, bottom)),
                closed = false,
            ),
        )

        else -> listOf(
            OutlineStroke(listOf(OutlinePoint(left, top), OutlinePoint(right, top)), closed = false),
            OutlineStroke(listOf(OutlinePoint(left, bottom), OutlinePoint(right, bottom)), closed = false),
        )
    }
}

/**
 * The corners with repeats and the points on a straight edge removed, treating the list as a closed loop. A corner
 * shared by two cells that touch only at a point stays, once per visit.
 */
private fun withoutStraightCorners(corners: List<GridCorner>): List<GridCorner> {
    val loop = corners.toMutableList()
    while (loop.size > 3) {
        val index = loop.indices.firstOrNull { i ->
            val cur = loop[i]
            val prev = loop[(i - 1 + loop.size) % loop.size]
            val next = loop[(i + 1) % loop.size]
            cur == prev ||
                (prev.column == cur.column && cur.column == next.column) ||
                (prev.row == cur.row && cur.row == next.row)
        } ?: break
        loop.removeAt(index)
    }
    return loop
}
