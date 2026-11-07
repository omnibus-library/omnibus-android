package com.omnibus.omnibus.data.shelves

import com.omnibus.omnibus.testing.randomEnum
import com.omnibus.omnibus.testing.randomInt
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString

fun randomShelfSummary(): ShelfSummary = ShelfSummary(
    id = randomLong(),
    ownerUserId = randomLong(),
    kind = randomEnum(),
    name = randomString(),
    bookCount = randomLong(),
    coverUuids = List(randomInt(0..4)) { randomString() },
)
