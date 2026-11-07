package com.omnibus.omnibus.data.audiobook.offers

import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomDouble
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString

fun randomEbookOffer(): EbookOffer = EbookOffer(
    bookUuid = randomString(),
    bookFileId = randomLong(1L..1_000_000L),
    positionSeconds = randomDouble(0.01, 40_000.0),
    sourceAhead = randomBoolean(),
    sourceClock = randomLong(),
)
