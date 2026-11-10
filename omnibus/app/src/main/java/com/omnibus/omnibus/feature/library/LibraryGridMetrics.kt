package com.omnibus.omnibus.feature.library

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Column count and cell width for a grid of covers, as pure functions so the breakpoints are testable without
 * Compose. Widths are dp values, and [availableWidth] is the width the grid has.
 */
internal class CoverGridMetrics(
    private val minCell: Float,
    private val maxCell: Float,
    private val spacing: Float,
) {

    /**
     * How many columns fit, with at least one column at any width. A column needs its minimum cell
     * plus the spacing before the next, so the count steps up every [minCell] + [spacing] dp.
     */
    fun columns(availableWidth: Float): Int =
        max(1, floor((availableWidth.coerceAtLeast(0f) + spacing) / (minCell + spacing)).toInt())

    /**
     * The width of each cell in [columns]. Cells share the row evenly but never exceed [maxCell],
     * so a single column on a wide screen stays a book-sized cell rather than a banner.
     */
    fun cellWidth(availableWidth: Float): Float {
        val n = columns(availableWidth)
        return min(maxCell, (availableWidth.coerceAtLeast(0f) - spacing * (n - 1)) / n)
    }
}

/**
 * Column count and cell width for the All books grid, as pure functions so the breakpoints are
 * testable without Compose. Widths are dp values, and [availableWidth] is the screen width minus the
 * 20 dp insets on each side.
 */
object LibraryGridMetrics {

    private const val MIN_CELL = 112f
    private const val MAX_CELL = 168f
    private const val SPACING = 16f

    private val metrics = CoverGridMetrics(MIN_CELL, MAX_CELL, SPACING)

    /**
     * How many columns fit, with at least one column at any width. A column needs its minimum cell
     * plus the spacing before the next, so the count steps up every 128 dp.
     */
    fun columns(availableWidth: Float): Int = metrics.columns(availableWidth)

    /**
     * The width of each cell in [columns]. Cells share the row evenly but never exceed [MAX_CELL],
     * so a single column on a wide screen stays a book-sized cell rather than a banner.
     */
    fun cellWidth(availableWidth: Float): Float = metrics.cellWidth(availableWidth)
}
