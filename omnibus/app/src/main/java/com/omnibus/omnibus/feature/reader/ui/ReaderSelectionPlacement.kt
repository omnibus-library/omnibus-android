package com.omnibus.omnibus.feature.reader.ui

/**
 * The selection menu's metrics, in dp, which equal the page's CSS px. The menu is the highlight menu's toolbar, the five
 * colours and Copy, so it shares [HighlightMenuMetrics]. It is a body with a tail pointing at the selection, and [GAP]
 * plus [KNOB_REACH] keeps the tail's tip clear of the handles' knobs.
 */
internal object SelectionMenuMetrics {
    /** The menu's width: the toolbar's padding, five colours, the divider and Copy. A narrower page shrinks it. */
    const val MAX_WIDTH = 2 * HighlightMenuMetrics.PADDING + 5 * HighlightMenuMetrics.SLOT +
        HighlightMenuMetrics.DIVIDER_SPACE + HighlightMenuMetrics.SLOT
    const val BODY_HEIGHT = HighlightMenuMetrics.ROW_HEIGHT
    const val TAIL_HEIGHT = 8f
    const val TAIL_WIDTH = 24f

    /** The body's corner radius, which the tail must clear. */
    const val CORNER = HighlightMenuMetrics.CORNER

    /** The gap between the selection's line edge and the menu's tip. */
    const val GAP = 8f

    /** How far past [GAP] the tip sits, so it never covers a handle's knob. */
    const val KNOB_REACH = 12f

    /** The minimum distance from the page's side edges. */
    const val SCREEN_EDGE = 10f

    /** The top band of the page that the reader's chrome covers. A menu above it has no room. */
    const val CHROME_BAND = 64f

    const val HEIGHT = BODY_HEIGHT + TAIL_HEIGHT
}

/**
 * Where the selection menu goes, in the page's dp: its top-left corner and width, whether its tail points up (the menu
 * is below the selection), and the tail's centre x inside the menu.
 */
internal data class SelectionMenuPlacement(
    val left: Float,
    val top: Float,
    val width: Float,
    val tailUp: Boolean,
    val tailCenterX: Float,
)

/**
 * The placement of the selection menu over [rects], the selection's per-line rectangles, on a page [boundsWidth] by
 * [boundsHeight] dp. The menu sits above the selection when there is room above the chrome band, and below it when not.
 * It is centred on the selection's horizontal span, clamped [SelectionMenuMetrics.SCREEN_EDGE] from the side edges, and
 * its tail stays clear of the body's corners. Null when [rects] is empty.
 */
internal fun selectionMenuPlacement(
    rects: List<PageRect>,
    boundsWidth: Float,
    boundsHeight: Float,
): SelectionMenuPlacement? {
    if (rects.isEmpty()) return null
    val m = SelectionMenuMetrics
    val top = rects.minOf { it.y }.toFloat()
    val bottom = rects.maxOf { it.y + it.height }.toFloat()
    val anchorX = ((rects.minOf { it.x } + rects.maxOf { it.x + it.width }) / 2).toFloat()
    val width = minOf(m.MAX_WIDTH, boundsWidth - 2 * m.SCREEN_EDGE)
    val offset = m.GAP + m.KNOB_REACH
    val aboveTop = top - offset - m.HEIGHT
    val tailUp = aboveTop < m.CHROME_BAND
    val panelTop = if (tailUp) (bottom + offset).coerceAtMost(boundsHeight - m.HEIGHT - m.SCREEN_EDGE) else aboveTop
    val left = (anchorX - width / 2).coerceIn(m.SCREEN_EDGE, maxOf(m.SCREEN_EDGE, boundsWidth - m.SCREEN_EDGE - width))
    val tailInset = m.CORNER + m.TAIL_WIDTH / 2
    val tailCenterX = (anchorX - left).coerceIn(tailInset, maxOf(tailInset, width - tailInset))
    return SelectionMenuPlacement(left, panelTop, width, tailUp, tailCenterX)
}
