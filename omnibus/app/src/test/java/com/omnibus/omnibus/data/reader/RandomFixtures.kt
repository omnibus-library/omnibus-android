package com.omnibus.omnibus.data.reader

import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomEnum
import com.omnibus.omnibus.testing.randomInt
import com.omnibus.omnibus.testing.randomOf

fun randomReaderSettings(): ReaderSettings = ReaderSettings(
    theme = randomEnum(),
    typeface = randomEnum(),
    fontSize = randomInt(ReaderSettings.FONT_SIZE_RANGE),
    lineHeight = randomInt(ReaderSettings.LINE_HEIGHT_TENTHS) / 10.0,
    margins = randomEnum(),
    justify = randomBoolean(),
    spread = randomOf(listOf("none", "auto")),
)
