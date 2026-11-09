package com.omnibus.omnibus.feature.library.ui

import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.feature.library.OutlineGrid
import com.omnibus.omnibus.feature.library.OutlineStroke
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
 * Draws an outline around each of [runs] in the gutters of the grid, over its content. The outlines are computed from
 * the visible items of [gridState]. A run row that is scrolled off-screen is extrapolated from the nearest visible row,
 * so an outline does not stop at the viewport edge. The drawing reads the layout in the draw phase, so scrolling
 * redraws it without recomposing.
 *
 * @param runs One run per unfolded series, in grid order.
 * @param columns The grid's column count.
 * @param color The stroke colour.
 */
internal fun Modifier.seriesOutlines(
    gridState: LazyGridState,
    runs: List<OutlineRun>,
    columns: Int,
    color: Color,
): Modifier = drawWithContent {
    drawContent()
    if (runs.isEmpty()) return@drawWithContent
    val rowGap = ROW_GAP.toPx()
    val columnGap = COLUMN_GAP.toPx()
    val rows = visibleRows(gridState.layoutInfo, columns, rowGap)
    if (rows.isEmpty()) return@drawWithContent
    val pitch = rowPitch(rows, rowGap)
    val contentLeft = Spacing.screen.toPx()
    val slotWidth = (size.width - 2 * contentLeft - columnGap * (columns - 1)) / columns

    for (run in runs) {
        val first = run.firstGridIndex - HEADER_ITEM_COUNT
        val start = first % columns
        val firstRow = first / columns
        val lastRow = firstRow + (start + run.cellCount - 1) / columns
        if ((firstRow..lastRow).none { it in rows }) continue
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
        for (stroke in outlineStrokes(seriesOutline(start, run.cellCount, columns), grid)) {
            drawPath(
                path = stroke.toPath(),
                color = color,
                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.cornerPathEffect(Radius.lg.toPx())),
            )
        }
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
