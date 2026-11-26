package com.omnibus.omnibus.feature.libraries.switching

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Where one spine sits: the shelf it is on, and its left edge from the shelf's left edge. */
data class SpineSlot(val shelf: Int, val x: Dp)

/** The bookcase's measures (#369). The values are the spec's "Constants" table. */
object BookcaseGeometry {

    /** Width of every spine. */
    val SPINE_WIDTH: Dp = 30.dp

    /** Space between spines on a shelf. */
    val SPINE_GAP: Dp = 7.dp

    /** How far the raised spine lifts. */
    val RAISE: Dp = 16.dp

    /** Space on each side of a shelf. */
    val SHELF_MARGIN: Dp = 20.dp

    /** The widest a shelf grows. */
    val SHELF_MAX_WIDTH: Dp = 560.dp

    /** The whole lift: the settle, then the new spine's lift. */
    val LIFT: Duration = 900.milliseconds

    /** Size of the framed initial. */
    val INITIAL_SIZE: Dp = 64.dp

    /**
     * Places [count] spines from the left. Every spine is on shelf 0: [availableWidth] is unused until more shelves
     * are drawn, and then this is the only function that decides where a spine goes.
     */
    fun layout(count: Int, availableWidth: Dp): List<SpineSlot> = List(count) {
        SpineSlot(shelf = 0, x = SHELF_MARGIN + (SPINE_WIDTH + SPINE_GAP) * it)
    }
}
