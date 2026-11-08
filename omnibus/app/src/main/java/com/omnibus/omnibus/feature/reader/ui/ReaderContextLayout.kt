package com.omnibus.omnibus.feature.reader.ui

import kotlin.math.max
import kotlin.math.min

/** A rectangle in the page area's own units. */
data class ContextRect(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
}

/** The zoomed-out card and where its neighbours peek in, in the page area's units. */
data class ContextCardLayout(
    val scale: Float,
    val card: ContextRect,
    val previous: ContextRect,
    val next: ContextRect,
)

/** A neighbour page that peeks in beside the card. */
enum class PeekSide { Previous, Next }

internal object ContextScale {
    const val PHONE = 0.80f
    const val SQUARE = 0.75f
    const val MIN = 0.3f

    /** At or below this short-to-long side ratio, an area is phone-shaped. */
    const val PHONE_ASPECT = 0.5f
}

/**
 * The scale of the zoomed-out card. It eases from [ContextScale.PHONE] for phone-shaped areas to
 * [ContextScale.SQUARE] for square ones, then shrinks further if the card would not fit between
 * [reservedTop] and [reservedBottom]. Never below [ContextScale.MIN]. A non-positive area is 1.
 */
fun contextCardScale(areaWidth: Float, areaHeight: Float, reservedTop: Float, reservedBottom: Float): Float {
    if (areaWidth <= 0f || areaHeight <= 0f) return 1f
    val aspect = min(areaWidth, areaHeight) / max(areaWidth, areaHeight)
    val t = ((aspect - ContextScale.PHONE_ASPECT) / (1f - ContextScale.PHONE_ASPECT)).coerceIn(0f, 1f)
    val shapeScale = ContextScale.PHONE + (ContextScale.SQUARE - ContextScale.PHONE) * t
    val fitScale = (areaHeight - reservedTop - reservedBottom) / areaHeight
    return min(shapeScale, fitScale).coerceAtLeast(ContextScale.MIN)
}

/**
 * The card, centred horizontally under [reservedTop], and the two peeks [gap] beyond its sides. The card
 * keeps the area's aspect ratio. A non-positive area gives scale 1 and three copies of the area.
 */
fun contextCardLayout(
    areaWidth: Float,
    areaHeight: Float,
    reservedTop: Float,
    reservedBottom: Float,
    gap: Float,
): ContextCardLayout {
    if (areaWidth <= 0f || areaHeight <= 0f) {
        val full = ContextRect(0f, 0f, areaWidth, areaHeight)
        return ContextCardLayout(scale = 1f, card = full, previous = full, next = full)
    }
    val scale = contextCardScale(areaWidth, areaHeight, reservedTop, reservedBottom)
    val width = areaWidth * scale
    val card = ContextRect(
        left = (areaWidth - width) / 2f,
        top = reservedTop,
        width = width,
        height = areaHeight * scale,
    )
    return ContextCardLayout(
        scale = scale,
        card = card,
        previous = card.copy(left = card.left - gap - width),
        next = card.copy(left = card.right + gap),
    )
}

/**
 * The neighbours that peek in. There is no previous page on the first page, and no next page at the end of
 * the book. Without page numbers the previous page is kept. A null location keeps both.
 */
fun contextPeeks(location: RelocateData?): Set<PeekSide> {
    val peeks = mutableSetOf(PeekSide.Previous, PeekSide.Next)
    if (location == null) return peeks
    if (location.totalPages > 0 && location.page <= 1) peeks.remove(PeekSide.Previous)
    if (location.atEnd) peeks.remove(PeekSide.Next)
    return peeks
}
