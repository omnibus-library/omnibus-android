package com.omnibus.omnibus.feature.reader

import com.omnibus.omnibus.feature.reader.ui.randomCfi
import com.omnibus.omnibus.feature.reader.ui.randomPageRect
import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomEnum
import com.omnibus.omnibus.testing.randomInt
import com.omnibus.omnibus.testing.randomString

fun randomPageHighlight(): PageHighlight = PageHighlight(
    cfiRange = randomCfi(),
    color = randomEnum(),
    hasNote = randomBoolean(),
)

fun randomKnownHighlight(): KnownHighlight = KnownHighlight(
    color = randomEnum(),
    text = randomString().takeIf { randomBoolean() },
    hasNote = randomBoolean(),
    note = randomString().takeIf { randomBoolean() },
)

fun randomHighlightMenuEntry(): HighlightMenuEntry = HighlightMenuEntry(
    cfiRange = randomCfi(),
    rects = List(randomInt(1..3)) { randomPageRect() },
    color = randomEnum(),
    text = randomString().takeIf { randomBoolean() },
    hasNote = randomBoolean(),
    note = randomString().takeIf { randomBoolean() },
)
