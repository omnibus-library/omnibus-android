package com.omnibus.omnibus.feature.reader.ui

/**
 * The highlight menu's metrics, in dp, which equal the page's CSS px. Swatches and action buttons each take a [SLOT]
 * touch target. The colour row, a rule and the action row set the height, and the switcher row adds a fixed height.
 */
internal object HighlightMenuMetrics {
    /** A swatch's or an action button's touch slot. */
    const val SLOT = 44f

    /** The colour row. */
    const val ROW_HEIGHT = 56f

    /** The action row under the colours. */
    const val ACTION_ROW_HEIGHT = 56f

    /** The "N of M" row above the colours. */
    const val SWITCHER_HEIGHT = 44f

    /** A rule between two rows. */
    const val RULE = 0.5f

    /** The colour row's side padding. */
    const val PADDING = 10f

    /** The menu's width: the colour row's padding and five swatch slots. A narrower page shrinks it. */
    const val WIDTH = 2 * PADDING + 5 * SLOT

    /** The body's corner radius, which the tail must clear. */
    const val CORNER = 28f

    const val TAIL_HEIGHT = SelectionMenuMetrics.TAIL_HEIGHT
    const val TAIL_WIDTH = SelectionMenuMetrics.TAIL_WIDTH
}

/** The menu's body width and body height, and its height with the tail. */
internal data class HighlightMenuSize(val width: Float, val bodyHeight: Float) {
    /** The body plus the tail. */
    val height: Float get() = bodyHeight + HighlightMenuMetrics.TAIL_HEIGHT
}

/**
 * The size of the menu: fixed, with the "N of M" row when [hasSwitcher]. The actions shown never change it.
 */
internal fun highlightMenuSize(hasSwitcher: Boolean): HighlightMenuSize {
    val m = HighlightMenuMetrics
    val bodyHeight = m.ROW_HEIGHT + m.RULE + m.ACTION_ROW_HEIGHT + (if (hasSwitcher) m.SWITCHER_HEIGHT + m.RULE else 0f)
    return HighlightMenuSize(m.WIDTH, bodyHeight)
}

/**
 * Where the highlight menu goes, in the page's dp: its top-left corner, its size, whether its tail points up (the menu
 * is below the highlight), and the tail's centre x inside the menu.
 */
internal data class HighlightMenuPlacement(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val tailUp: Boolean,
    val tailCenterX: Float,
)

/**
 * The placement of the highlight menu over [rects], the tapped highlight's per-line rectangles, on a page [boundsWidth]
 * by [boundsHeight] dp. It sits below the highlight unless that crosses the bottom band, then above it unless that
 * crosses the chrome band, and otherwise is pinned inside the page. It is centred on the highlight's horizontal span,
 * clamped [SelectionMenuMetrics.SCREEN_EDGE] from the side edges, and its tail stays clear of the body's corners. Null
 * when [rects] is empty.
 */
internal fun highlightMenuPlacement(
    rects: List<PageRect>,
    size: HighlightMenuSize,
    boundsWidth: Float,
    boundsHeight: Float,
): HighlightMenuPlacement? {
    if (rects.isEmpty()) return null
    val m = SelectionMenuMetrics
    val top = rects.minOf { it.y }.toFloat()
    val bottom = rects.maxOf { it.y + it.height }.toFloat()
    val anchorX = ((rects.minOf { it.x } + rects.maxOf { it.x + it.width }) / 2).toFloat()
    val width = minOf(size.width, boundsWidth - 2 * m.SCREEN_EDGE)
    val height = size.height
    val floor = boundsHeight - ReaderBands.Bottom.value
    val below = bottom + m.GAP
    val above = top - m.GAP - height
    val (panelTop, tailUp) = when {
        below + height <= floor -> below to true
        above >= m.CHROME_BAND -> above to false
        else -> below.coerceIn(m.CHROME_BAND, maxOf(m.CHROME_BAND, floor - height)) to true
    }
    val left = (anchorX - width / 2).coerceIn(m.SCREEN_EDGE, maxOf(m.SCREEN_EDGE, boundsWidth - m.SCREEN_EDGE - width))
    val tailInset = HighlightMenuMetrics.CORNER + HighlightMenuMetrics.TAIL_WIDTH / 2
    val tailCenterX = (anchorX - left).coerceIn(tailInset, maxOf(tailInset, width - tailInset))
    return HighlightMenuPlacement(left, panelTop, width, height, tailUp, tailCenterX)
}
