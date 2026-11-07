package com.omnibus.omnibus.feature.audiobook.player.offers

import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomDouble

fun randomEbookOfferUIState(): EbookOfferUIState = EbookOfferUIState(
    seconds = randomDouble(0.01, 40_000.0),
    sourceAhead = randomBoolean(),
)
