package com.omnibus.omnibus.feature.bookdetail.reviews.stars

import kotlin.math.ceil

/**
 * The half star under [x], from 0.5 to 5. [x] is measured from the layout's start edge, in the same unit as [width], so
 * the ten half steps each cover a tenth of the row. Step k covers `((k-1)/10, k/10]`, so an exact edge belongs to the
 * lower step. Out of range clamps to 0.5..5, and a NaN or non-positive [width], or a NaN [x], gives 0.5. Right-to-left
 * callers pass `width - x`.
 */
fun halfStepAt(x: Float, width: Float): Float {
    if (x.isNaN() || width.isNaN() || width <= 0f) return 0.5f
    // Multiply first: x / width * 10 lands a hair above an integer on an exact edge and ceils a whole step up.
    return (ceil(x * 10f / width) / 2f).coerceIn(0.5f, 5f)
}

/** The rating after a tap on [tapped]: tapping the current rating clears it, and any other value sets it. */
fun toggledRating(current: Float?, tapped: Float): Float? = if (current != null && tapped == current) null else tapped
