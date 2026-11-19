package com.omnibus.omnibus.feature.reader

import com.omnibus.omnibus.feature.reader.ui.randomCfi
import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomEnum

fun randomPageHighlight(): PageHighlight = PageHighlight(
    cfiRange = randomCfi(),
    color = randomEnum(),
    hasNote = randomBoolean(),
)
