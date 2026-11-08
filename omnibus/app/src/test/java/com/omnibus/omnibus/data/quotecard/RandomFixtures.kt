package com.omnibus.omnibus.data.quotecard

import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomInt

private const val OPAQUE = 0xFF000000.toInt()

fun randomOpaqueArgb(): Int = OPAQUE or randomInt(0..0xFFFFFF)

fun randomCoverPair(): CoverPair =
    CoverPair(fromArgb = randomOpaqueArgb(), toArgb = randomOpaqueArgb(), lightInk = randomBoolean())
