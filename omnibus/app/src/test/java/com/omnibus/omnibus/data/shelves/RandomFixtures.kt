package com.omnibus.omnibus.data.shelves

import com.omnibus.omnibus.data.library.randomShelf
import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomEnum
import com.omnibus.omnibus.testing.randomInt
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomOf
import com.omnibus.omnibus.testing.randomString

fun randomShelfSummary(): ShelfSummary = ShelfSummary(
    id = randomLong(),
    ownerUserId = randomLong(),
    kind = randomEnum(),
    name = randomString(),
    bookCount = randomLong(),
    coverUuids = List(randomInt(0..4)) { randomString() },
)

fun randomShelfDetail(): ShelfDetail = ShelfDetail(
    shelf = randomShelf(),
    description = if (randomBoolean()) randomString() else null,
    rules = ShelfRules(
        matchMode = randomOf(listOf<ShelfMatchMode?>(null) + ShelfMatchMode.entries),
        rules = List(randomInt(0..4)) { randomShelfRule() },
    ),
)

fun randomShelfRule(): ShelfRule = ShelfRule(
    field = randomEnum<ShelfRuleField>(),
    op = randomEnum<ShelfRuleOp>(),
    value = randomString(),
)
