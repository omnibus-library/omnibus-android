package com.omnibus.omnibus.feature.libraries.switching

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Where one spine sits: the shelf it is on, and its left edge from the shelf's left edge. */
data class SpineSlot(val shelf: Int, val x: Dp)

/** The bookcase's measures (#369, shelves #370). The values are the spec's "Constants" table. */
object BookcaseGeometry {

    /** Width of every spine. */
    val SPINE_WIDTH: Dp = 30.dp

    /** Space between spines on a shelf. */
    val SPINE_GAP: Dp = 7.dp

    /** How far the raised spine lifts. */
    val RAISE: Dp = 16.dp

    /** Space on each side of a shelf. */
    val SHELF_MARGIN: Dp = 20.dp

    /** The widest a shelf grows. Narrower windows than the 600 dp breakpoint never reach it. */
    val SHELF_MAX_WIDTH: Dp = 560.dp

    /** Space between one shelf and the next one below it. */
    val SHELF_GAP: Dp = 12.dp

    /** The whole lift: the settle, then the new spine's lift. */
    val LIFT: Duration = 900.milliseconds

    /** Size of the framed initial. */
    val INITIAL_SIZE: Dp = 64.dp

    /**
     * How many spines fit on one shelf of a bookcase [availableWidth] wide, capped at [SHELF_MAX_WIDTH], inside its
     * [SHELF_MARGIN]s. At least one, so a window too narrow for a spine still shows every library.
     */
    fun spinesPerShelf(availableWidth: Dp): Int {
        val inside = availableWidth.coerceAtMost(SHELF_MAX_WIDTH) - SHELF_MARGIN * 2
        return maxOf(1, ((inside + SPINE_GAP) / (SPINE_WIDTH + SPINE_GAP)).toInt())
    }

    /**
     * Places [count] spines in list order: each shelf fills from the left with as many as fit [availableWidth]
     * ([spinesPerShelf]), and the next spine starts a new shelf below. There is no limit on shelves.
     */
    fun layout(count: Int, availableWidth: Dp): List<SpineSlot> {
        val perShelf = spinesPerShelf(availableWidth)
        return List(count) {
            SpineSlot(shelf = it / perShelf, x = SHELF_MARGIN + (SPINE_WIDTH + SPINE_GAP) * (it % perShelf))
        }
    }
}
