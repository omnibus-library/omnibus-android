package com.omnibus.omnibus.data.audiobook.crossformat

import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomDouble
import com.omnibus.omnibus.testing.randomLong

fun randomCandidate(): CrossFormatAudioResult.Candidate = CrossFormatAudioResult.Candidate(
    positionSeconds = randomDouble(0.01, 40_000.0),
    bookFileId = randomLong(1L..1_000_000L),
    sourceAhead = randomBoolean(),
    sourceClock = randomLong(),
    follow = randomBoolean(),
)
