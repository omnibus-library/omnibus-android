package com.omnibus.omnibus.feature.bookdetail.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

/**
 * The page's stage and tab host, laid out as a column or as two panes, from [split]. Exactly two children: the stage
 * pane, then the tab host. The layout is the same composition in both modes, so the mode goes through [split] and the
 * modifiers alone. Keep it that way: no conditional composable above a stateful child.
 *
 * In column mode the two children stack at full width, and [trailingSpace] is reserved below them for the action
 * area. In two-pane mode the stage pane takes the leading width, plus [overhang] on each side so the cover's
 * halo is not cut, and the host takes the rest, drawn above the overhang.
 *
 * @param overhang How far the stage pane reaches past each side of its width. The request page, which has no halo,
 * passes zero.
 */
@Composable
internal fun BookDetailPaneLayout(
    split: PaneSplit?,
    trailingSpace: Dp,
    modifier: Modifier = Modifier,
    overhang: Dp = StageHaloOverhang,
    stagePane: @Composable () -> Unit,
    tabHost: @Composable () -> Unit,
) {
    Layout(content = {
        stagePane()
        tabHost()
    }, modifier = modifier) { measurables, constraints ->
        val (stageMeasurable, hostMeasurable) = measurables
        val width = constraints.maxWidth
        if (split == null) {
            val stage = stageMeasurable.measure(Constraints(minWidth = width, maxWidth = width))
            val host = hostMeasurable.measure(Constraints(minWidth = width, maxWidth = width))
            layout(width, stage.height + host.height + trailingSpace.roundToPx()) {
                stage.placeRelative(0, 0)
                host.placeRelative(0, stage.height)
            }
        } else {
            val height = constraints.maxHeight
            val lead = split.leading.roundToPx().coerceAtMost(width)
            val over = overhang.roundToPx()
            val stage = stageMeasurable.measure(Constraints.fixed(lead + 2 * over, height))
            // The trailing width is the rest in pixels, so rounding never leaves a gap between the panes.
            val host = hostMeasurable.measure(Constraints.fixed(width - lead, height))
            layout(width, height) {
                stage.placeRelative(-over, 0)
                host.placeRelative(lead, 0, zIndex = 1f)
            }
        }
    }
}
