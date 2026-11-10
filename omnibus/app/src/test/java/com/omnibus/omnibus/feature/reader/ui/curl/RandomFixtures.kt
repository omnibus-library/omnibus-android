package com.omnibus.omnibus.feature.reader.ui.curl

import com.omnibus.omnibus.testing.TEST_RANDOM_SOURCE
import io.kotest.property.Arb
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.numericFloat
import io.kotest.property.arbitrary.pair

/** Stage rectangles with an origin in 0..1000 and sides in 50..2000 pixels. Never degenerate. */
internal fun arbCurlRect(): Arb<CurlRect> = Arb.pair(
    Arb.pair(Arb.numericFloat(0f, 1000f), Arb.numericFloat(0f, 1000f)),
    Arb.pair(Arb.numericFloat(50f, 2000f), Arb.numericFloat(50f, 2000f)),
).map { (origin, size) ->
    val (left, top) = origin
    val (width, height) = size
    CurlRect(left, top, left + width, top + height)
}

internal fun randomCurlRect(): CurlRect = arbCurlRect().next(TEST_RANDOM_SOURCE)
