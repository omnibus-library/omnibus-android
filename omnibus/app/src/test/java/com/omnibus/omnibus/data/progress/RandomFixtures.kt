package com.omnibus.omnibus.data.progress

import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomInt
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString

private fun <T : Any> T.orNull(): T? = takeIf { randomBoolean() }

fun randomReadingPosition(): ReadingPosition = ReadingPosition(
    bookUuid = randomString(),
    cfi = randomString().orNull(),
    percent = randomInt(0..100).orNull(),
    clientUpdatedAt = randomLong(),
)

fun randomServerReadingPosition(): ServerReadingPosition = ServerReadingPosition(
    cfi = randomString().orNull(),
    percent = randomInt(0..100).orNull(),
    derivedCfi = randomString().orNull(),
    updatedAt = randomLong(),
    clientUpdatedAt = randomLong().orNull(),
)
