package com.omnibus.omnibus.feature.library.ui

import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.feature.library.OutlineGrid
import com.omnibus.omnibus.feature.library.OutlineStroke
import com.omnibus.omnibus.feature.library.SeriesOutlineShape
import com.omnibus.omnibus.feature.library.outlineFills
import com.omnibus.omnibus.feature.library.outlineStrokes
import com.omnibus.omnibus.feature.library.seriesOutline
import com.omnibus.omnibus.ui.theme.Radius
import com.omnibus.omnibus.ui.theme.Spacing
import kotlin.math.abs

/** An unfolded series' cells in the grid: the grid index of its summary, and how many cells it spans. */
internal data class OutlineRun(val firstGridIndex: Int, val cellCount: Int)

/** The 26 dp gap above each row of the Library grid, from the cells' own top padding. */
private val ROW_GAP = 26.dp

/** The 16 dp gap between columns of the Library grid. */
private val COLUMN_GAP = 16.dp

/** A row's cells in px: the top of the cells below their padding, and the bottom of the tallest one. */
private class RowBox(val top: Float, val bottom: Float)

/**
 * Fills and outlines each of [runs] in the gutters of the grid: the fill goes under the grid's content and the outline
 * over it. The shapes are computed from the visible items of [gridState]. A run row that is scrolled off-screen is
 * extrapolated from the nearest visible row, so an outline does not stop at the viewport edge. The drawing reads the
 * layout in the draw phase, so scrolling redraws it without recomposing.
 *
 * @param runs One run per unfolded series, in grid order.
 * @param columns The grid's column count.
 * @param stroke The outline colour.
 * @param fill The colour inside each container.
 */
internal fun Modifier.seriesOutlines(
    gridState: LazyGridState,
    runs: List<OutlineRun>,
    columns: Int,
    stroke: Color,
    fill: Color,
): Modifier = drawWithContent {
    val placed = placeRuns(gridState.layoutInfo, runs, columns)
    val radius = Radius.lg.toPx()
    val fillPaint = Paint().apply {
        color = fill
        style = PaintingStyle.Fill
        pathEffect = PathEffect.cornerPathEffect(radius)
    }
    clipRect {
        drawIntoCanvas { canvas ->
            for ((shape, grid) in placed) {
                // An open end runs past the screen edge, so its rounded corner is cut off by the clip.
                val fillGrid = grid.copy(edgeLeft = -radius, edgeRight = size.width + radius)
                for (polygon in outlineFills(shape, fillGrid)) {
                    canvas.drawPath(OutlineStroke(polygon, closed = true).toPath(), fillPaint)
                }
            }
        }
    }
    drawContent()
    for ((shape, grid) in placed) {
        for (outline in outlineStrokes(shape, grid)) {
            drawPath(
                path = outline.toPath(),
                color = stroke,
                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.cornerPathEffect(radius)),
            )
        }
    }
}

/** Each of [runs] with a row on screen, as its outline shape and its grid in px. */
private fun DrawScope.placeRuns(
    info: LazyGridLayoutInfo,
    runs: List<OutlineRun>,
    columns: Int,
): List<Pair<SeriesOutlineShape, OutlineGrid>> {
    if (runs.isEmpty()) return emptyList()
    val rowGap = ROW_GAP.toPx()
    val columnGap = COLUMN_GAP.toPx()
    val rows = visibleRows(info, columns, rowGap)
    if (rows.isEmpty()) return emptyList()
    val pitch = rowPitch(rows, rowGap)
    val contentLeft = Spacing.screen.toPx()
    val slotWidth = (size.width - 2 * contentLeft - columnGap * (columns - 1)) / columns

    return runs.mapNotNull { run ->
        val first = run.firstGridIndex - HEADER_ITEM_COUNT
        val start = first % columns
        val firstRow = first / columns
        val lastRow = firstRow + (start + run.cellCount - 1) / columns
        if ((firstRow..lastRow).none { it in rows }) return@mapNotNull null
        val boxes = (firstRow..lastRow).map { rowBox(it, rows, pitch) }
        val grid = OutlineGrid(
            contentLeft = contentLeft,
            slotWidth = slotWidth,
            columnGap = columnGap,
            rowGap = rowGap,
            rowTops = boxes.map { it.top },
            rowBottoms = boxes.map { it.bottom },
            edgeLeft = 0f,
            edgeRight = size.width,
        )
        seriesOutline(start, run.cellCount, columns) to grid
    }
}

/** The visible book-area rows by row index, skipping the header and footer items. */
private fun visibleRows(info: LazyGridLayoutInfo, columns: Int, rowGap: Float): Map<Int, RowBox> {
    val tops = HashMap<Int, Float>()
    val bottoms = HashMap<Int, Float>()
    for (item in info.visibleItemsInfo) {
        if (item.key == HEADER_KEY || item.key == FOOTER_KEY) continue
        val book = item.index - HEADER_ITEM_COUNT
        if (book < 0) continue
        val row = book / columns
        // The grid's offsets leave out its content padding, which is added back at placement.
        val y = (item.offset.y + info.beforeContentPadding).toFloat()
        val top = if (row > 0) y + rowGap else y
        val bottom = y + item.size.height
        tops[row] = minOf(top, tops[row] ?: top)
        bottoms[row] = maxOf(bottom, bottoms[row] ?: bottom)
    }
    return tops.mapValues { (row, top) -> RowBox(top, bottoms.getValue(row)) }
}

/** The distance from one row's top to the next: the row's height and the gap below it. */
private fun rowPitch(rows: Map<Int, RowBox>, rowGap: Float): Float {
    val sorted = rows.keys.sorted()
    if (sorted.size < 2) {
        val only = rows.getValue(sorted.single())
        return only.bottom - only.top + rowGap
    }
    val first = rows.getValue(sorted.first())
    val last = rows.getValue(sorted.last())
    return (last.top - first.top) / (sorted.last() - sorted.first())
}

/** [row]'s cells: measured when visible, else placed from the nearest visible row by [pitch]. */
private fun rowBox(row: Int, rows: Map<Int, RowBox>, pitch: Float): RowBox {
    rows[row]?.let { return it }
    val nearest = rows.keys.minBy { abs(it - row) }
    val box = rows.getValue(nearest)
    val top = box.top + (row - nearest) * pitch
    return RowBox(top, top + (box.bottom - box.top))
}

private fun OutlineStroke.toPath(): Path = Path().apply {
    points.forEachIndexed { i, point ->
        if (i == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
    }
    if (closed) close()
}
