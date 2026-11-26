package com.omnibus.omnibus.feature.libraries.switching

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize

/**
 * Lays the bookcase out on as many shelves as [state]'s spines need (#370), one [shelf] per row of
 * [BookcaseGeometry.layout], [BookcaseGeometry.SHELF_GAP] apart. [shelf] draws one shelf from the state it is given:
 * the same raise and transition, with only that shelf's spines.
 *
 * When the shelves are taller than the space this is given, it scrolls by itself and never by touch. It starts on the
 * raised library's shelf, and when a lift starts it pans to the new library's shelf during the settle, so the new
 * spine lifts in view. With the system's remove-animations setting on, it moves there in one step.
 */
@Composable
fun BookcaseViewport(
    state: BookcaseState,
    modifier: Modifier = Modifier,
    shelf: @Composable (BookcaseState) -> Unit,
) {
    val instant = removeAnimations()
    val scroll = rememberScrollState()
    // Whether it has been put on the open library's shelf yet: that first move never pans.
    var started by remember { mutableStateOf(false) }
    // Each shelf's top and height in pixels, inside the scrolled column, once placed.
    val placed = remember { mutableStateMapOf<Int, ShelfBounds>() }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val shelves = shelvesOf(state.spines, maxWidth)
        // Unbounded (a test host with no height) means everything fits and nothing scrolls.
        val viewportHeight = constraints.maxHeight

        // Where the lift is heading, and where it comes from. Outside a lift both are the raised library.
        val transition = state.transition
        val toShelf = shelves.indexOfShelf((transition as? BookcaseTransition.Lift)?.toId ?: state.raisedLibraryId)
        val fromShelf = (transition as? BookcaseTransition.Lift)?.let { shelves.indexOfShelf(it.fromId) } ?: toShelf
        val ready = shelves.indices.all { it in placed }

        LaunchedEffect(toShelf, transition is BookcaseTransition.Lift, ready, viewportHeight, instant) {
            if (!ready || toShelf == null) return@LaunchedEffect
            if (!started && fromShelf != null) scroll.scrollTo(placed.getValue(fromShelf).centredIn(viewportHeight))
            started = true
            val target = placed.getValue(toShelf).centredIn(viewportHeight)
            if (transition is BookcaseTransition.Lift && !instant) {
                scroll.animateScrollTo(target, tween((BookcaseGeometry.LIFT / 2).inWholeMilliseconds.toInt()))
            } else {
                scroll.scrollTo(target)
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(scroll, enabled = false),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(BookcaseGeometry.SHELF_GAP),
        ) {
            shelves.forEachIndexed { index, spines ->
                key(index) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .onPlaced { placed[index] = ShelfBounds(it.positionInParent().y.toInt(), it.size) },
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        shelf(state.copy(spines = spines))
                    }
                }
            }
        }
    }
}

/** [spines] split into shelves as [BookcaseGeometry.layout] places them in a bookcase [availableWidth] wide. */
private fun shelvesOf(spines: List<BookcaseSpine>, availableWidth: Dp): List<List<BookcaseSpine>> {
    val slots = BookcaseGeometry.layout(spines.size, availableWidth)
    return spines.indices.groupBy { slots[it].shelf }.values.map { indices -> indices.map(spines::get) }
}

/** The shelf holding [libraryId]'s spine, or null when no spine is that library's. */
private fun List<List<BookcaseSpine>>.indexOfShelf(libraryId: String?): Int? =
    indexOfFirst { shelf -> shelf.any { it.libraryId == libraryId } }.takeIf { it >= 0 }

/** Where a shelf sits in the scrolled column. */
private data class ShelfBounds(val top: Int, val size: IntSize) {

    /** The scroll that centres this shelf in a viewport [viewportHeight] tall. The scroll state clamps it to its range. */
    fun centredIn(viewportHeight: Int): Int = (top - (viewportHeight - size.height) / 2).coerceAtLeast(0)
}
