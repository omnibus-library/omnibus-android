package com.omnibus.omnibus.feature.bookdetail.progress

import kotlin.math.max

/** The most segments a bar shows. Longer chapter lists are grouped evenly down to this. */
const val MAX_SEGMENTS = 24

/** A segment narrower than this share of the book folds into the next one, so no sliver is drawn. */
const val MIN_SHARE = 0.015f

/**
 * The bar's segment widths for [chapters], as relative weights that sum to 1. Starts outside (0, 1) and duplicates are
 * ignored. No usable chapter gives one full-width segment. Slivers fold into what follows, and more than [MAX_SEGMENTS]
 * segments are grouped evenly.
 */
fun segmentWeights(chapters: List<BookChapter>): List<Float> {
    val inside = chapters.map { it.startFraction }.filter { it.isFinite() && it > 0f && it < 1f }.sorted()
    val edges = listOf(0f) + inside + 1f
    val widths = edges.zipWithNext { a, b -> b - a }.filter { it > 0.0001f }
    return merged(widths.ifEmpty { listOf(1f) })
}

/** iOS `StageProgress.merged`: slivers fold into what follows, then even groups until at most [MAX_SEGMENTS] remain. */
private fun merged(widths: List<Float>): List<Float> {
    val floor = widths.sum() * MIN_SHARE
    val kept = mutableListOf<Float>()
    var carried = 0f
    for (width in widths) {
        if (width + carried < floor) carried += width else { kept += width + carried; carried = 0f }
    }
    if (carried > 0f) { if (kept.isEmpty()) kept += carried else kept[kept.lastIndex] += carried }
    if (kept.size <= MAX_SEGMENTS) return kept
    val size = (kept.size + MAX_SEGMENTS - 1) / MAX_SEGMENTS
    return kept.chunked(size) { it.sum() }
}

/**
 * One segment of the bar: its [weight] share of the bar and how much of it is filled.
 *
 * @property fill The filled share of this segment, 0 to 1.
 */
data class BarSegment(val weight: Float, val fill: Float)

/**
 * Lays [fraction] over segments of [weights]: each segment's fill is the part of the fraction inside it. Empty when the
 * weights sum to zero or less. [fraction] is clamped to 0..1, and NaN counts as 0.
 */
fun barSegments(weights: List<Float>, fraction: Float): List<BarSegment> {
    val total = weights.sum()
    if (total <= 0f) return emptyList()
    val reached = (if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)) * total
    var run = 0f
    return weights.map { weight ->
        val start = run
        run += weight
        BarSegment(weight, ((reached - start) / weight).coerceIn(0f, 1f))
    }
}

/**
 * The x of the fill's end as the bar draws it. Segments share [barWidth] minus the [gap]s between them, by weight, so the
 * fill can end in a gap's shift from `fraction * barWidth`. Returns 0 when nothing is filled.
 */
fun fillEdgeX(segments: List<BarSegment>, barWidth: Float, gap: Float): Float {
    if (segments.isEmpty() || barWidth <= 0f) return 0f
    val total = segments.sumOf { it.weight.toDouble() }.toFloat()
    val available = (barWidth - gap * (segments.size - 1)).coerceAtLeast(0f)
    var start = 0f
    var edge = 0f
    for (segment in segments) {
        val width = available * segment.weight / total
        if (segment.fill > 0f) edge = start + width * segment.fill
        start += width + gap
    }
    return edge.coerceIn(0f, barWidth)
}

/**
 * The centre of a flag [flagWidth] wide, kept inside a bar [barWidth] wide. The flag stays within the bar's ends, so its
 * centre moves toward [x] only as far as that allows. A flag wider than the bar is centred.
 */
fun flagCenterX(x: Float, barWidth: Float, flagWidth: Float): Float =
    if (flagWidth >= barWidth) barWidth / 2 else x.coerceIn(flagWidth / 2, barWidth - flagWidth / 2)

/**
 * The left edge of a stem [stemWidth] wide that stands at [x], kept inside a bar [barWidth] wide.
 */
fun stemX(x: Float, barWidth: Float, stemWidth: Float): Float =
    (x - stemWidth / 2).coerceIn(0f, max(0f, barWidth - stemWidth))
