package com.omnibus.omnibus.feature.reader.ui

import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomInt
import com.omnibus.omnibus.testing.randomString

/** An EPUB CFI that won't collide with another one in a test. */
fun randomCfi(): String = "epubcfi(/6/${randomString(8..16)})"

fun randomRelocateData(): RelocateData = RelocateData(
    cfi = randomCfi().takeIf { randomBoolean() },
    page = randomInt(0..1000),
    totalPages = randomInt(0..1000),
    pct = randomInt(0..100),
    atEnd = randomBoolean(),
    chapter = randomInt(0..50),
    totalChapters = randomInt(0..50),
    chapterTitle = randomString(),
    chapterPagesLeft = randomInt(0..50),
    echo = randomBoolean(),
    settledSeek = randomInt().takeIf { randomBoolean() },
)
