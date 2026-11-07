package com.omnibus.omnibus.feature.bookdetail.overview.reading

import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomDouble

fun randomCardProgress(): CardProgress = CardProgress(
    fraction = if (randomBoolean()) randomDouble().toFloat() else null,
    hasPosition = randomBoolean(),
)
