package com.omnibus.omnibus.feature.reader.ui

import com.omnibus.omnibus.data.reader.ReaderMargins
import kotlin.math.floor

/**
 * The bookmark ribbon's metrics, in dp. Keep in step with the page: [STAGE_SIDE] mirrors `reader.html`
 * `#stage` (10px each side, inside the insets), and the text gap mirrors epub.js `Layout.calculate`
 * (an even `floor(width / 12)`, half of it padding each side of the column).
 */
internal object ReaderRibbonMetrics {
    const val WIDTH = 28f
    const val HEIGHT = 92f

    /** How far the shape runs above the top edge, offscreen, so overshoot and sway never show a gap. */
    const val TOP_BLEED = 26f

    /** The notch's depth, as a fraction of the whole shape including [TOP_BLEED] (the mock's 82% apex). */
    const val NOTCH_DEPTH = 0.18f

    /** The single-page ribbon's left edge, from the page's left inset. */
    const val EDGE = 20f
    const val STAGE_SIDE = 10f

    /** The ribbon's alpha where it covers text. */
    const val OVERLAP_ALPHA = 0.68f

    /** Fully above the top edge: the mock's translateY(-110%) of the whole shape. */
    const val HIDDEN_OFFSET = -1.1f * (HEIGHT + TOP_BLEED)
}

/**
 * What the ribbon shows: the trimmed location CFI, whether it is bookmarked, and whether the quick action was tapped
 * on it.
 */
internal data class RibbonFrame(val page: String?, val shown: Boolean, val tapped: Boolean)

/** How the ribbon moves from one frame to the next. */
internal enum class RibbonMotion { None, Drop, FadeIn, FadeOut, Unfurl, Furl, CrossfadeIn, CrossfadeOut }

/** Where the ribbon hangs: its physical left edge, whether it covers text, and whether the page is a spread. */
internal data class RibbonPlacement(val startDp: Float, val translucent: Boolean, val twoPage: Boolean)

/**
 * The motion from [previous] to [current]. The first frame shown fades in, and a page change drops the old ribbon at
 * once. Only a flip on the same page is animated as a tap, so a bookmark that changes without a tap fades. Under
 * [reduceMotion] every change is a cross-fade in place, except that a page change still drops.
 */
internal fun ribbonMotion(previous: RibbonFrame?, current: RibbonFrame, reduceMotion: Boolean): RibbonMotion {
    val fadeIn = if (reduceMotion) RibbonMotion.CrossfadeIn else RibbonMotion.FadeIn
    if (previous == null) return if (current.shown) fadeIn else RibbonMotion.None
    if (previous.page != current.page) return if (current.shown) fadeIn else RibbonMotion.Drop
    if (previous.shown == current.shown) return RibbonMotion.None
    return when {
        reduceMotion -> if (current.shown) RibbonMotion.CrossfadeIn else RibbonMotion.CrossfadeOut
        current.tapped -> if (current.shown) RibbonMotion.Unfurl else RibbonMotion.Furl
        else -> if (current.shown) RibbonMotion.FadeIn else RibbonMotion.FadeOut
    }
}

/**
 * Where the ribbon hangs on a page [pageWidthDp] wide, given the page's insets and the reader's [margins] and
 * [spread]. A spread centres it on the gutter. A single page hangs it from the left inset. It is translucent where
 * the text starts under it.
 */
internal fun ribbonPlacement(
    pageWidthDp: Float,
    insetLeftDp: Float,
    insetRightDp: Float,
    margins: ReaderMargins,
    spread: String,
): RibbonPlacement {
    val m = ReaderRibbonMetrics
    val stageLeft = insetLeftDp + m.STAGE_SIDE
    val stageWidth = (pageWidthDp - insetLeftDp - insetRightDp - 2 * m.STAGE_SIDE).coerceAtLeast(0f)
    val measure = margins.css.removeSuffix("%").toFloat() // 95, 80 or 65
    val columnWidth = stageWidth * measure / 100f
    val columnLeft = stageLeft + (stageWidth - columnWidth) / 2f
    val section = floor(columnWidth / 12f).toInt()
    val gap = if (section % 2 == 0) section else section - 1
    val twoPage = spread == "auto" && columnWidth >= ReaderScripts.MIN_SPREAD_WIDTH
    if (twoPage) {
        val centre = stageLeft + stageWidth / 2f // the gutter
        return RibbonPlacement(centre - m.WIDTH / 2f, translucent = gap < m.WIDTH, twoPage = true)
    }
    val start = insetLeftDp + m.EDGE
    return RibbonPlacement(start, translucent = columnLeft + gap / 2f < start + m.WIDTH, twoPage = false)
}
