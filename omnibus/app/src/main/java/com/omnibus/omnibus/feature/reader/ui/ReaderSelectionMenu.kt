package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.data.highlights.HighlightColor
import com.omnibus.omnibus.feature.reader.HighlightMenuAction
import com.omnibus.omnibus.feature.reader.HighlightMenuTarget

/**
 * The selection menu's body and tail as one outline, so its 1 dp border has no seam. The tail points up when [tailUp]
 * (the menu is below the selection) and down otherwise, with its centre at [tailCenterX] from the menu's left edge. The
 * body's corners are [corner] across.
 */
internal class SelectionMenuShape(
    private val tailUp: Boolean,
    private val tailCenterX: Dp,
    private val corner: Dp = SelectionMenuMetrics.CORNER.dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = with(density) {
        val tail = SelectionMenuMetrics.TAIL_HEIGHT.dp.toPx()
        val half = (SelectionMenuMetrics.TAIL_WIDTH / 2).dp.toPx()
        val top = if (tailUp) tail else 0f
        val bottom = if (tailUp) size.height else size.height - tail
        val body = Path().apply {
            addRoundRect(RoundRect(0f, top, size.width, bottom, CornerRadius(corner.toPx())))
        }
        val cx = tailCenterX.toPx()
        val tip = Path().apply {
            if (tailUp) {
                moveTo(cx - half, top)
                lineTo(cx, 0f)
                lineTo(cx + half, top)
            } else {
                moveTo(cx - half, bottom)
                lineTo(cx, size.height)
                lineTo(cx + half, bottom)
            }
            close()
        }
        Outline.Generic(Path().also { it.op(body, tip, PathOperation.Union) })
    }
}

/**
 * The selection menu over the page: the highlight menu's two rows, the five colours and a rule over [actions]. It is
 * drawn at [placement] with its tail on the selection, in the reading theme's [colors]. Its size is fixed, so placement
 * needs no measure pass.
 */
@Composable
internal fun ReaderSelectionMenu(
    placement: SelectionMenuPlacement,
    colors: ReaderChromeColors,
    target: HighlightMenuTarget,
    actions: List<HighlightMenuAction>,
    onColorChosen: (HighlightColor) -> Unit,
    onAction: (HighlightMenuAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    HighlightMenuSurface(
        left = placement.left,
        top = placement.top,
        width = placement.width,
        height = SelectionMenuMetrics.HEIGHT,
        tailUp = placement.tailUp,
        tailCenterX = placement.tailCenterX,
        colors = colors,
        modifier = modifier.testTag(ReaderSelectionTags.MENU),
    ) {
        HighlightMenuColorRow(selected = null, onColor = onColorChosen)
        HorizontalDivider(thickness = HighlightMenuMetrics.RULE.dp, color = colors.border)
        HighlightMenuActionRow(actions = actions, target = target, colors = colors, onAction = onAction)
    }
}
