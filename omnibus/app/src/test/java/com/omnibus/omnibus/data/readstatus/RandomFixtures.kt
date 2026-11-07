package com.omnibus.omnibus.data.readstatus

import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomEnum
import com.omnibus.omnibus.testing.randomLong

/** The server sends whole seconds, so the times are whole seconds in milliseconds. */
fun randomReadStatusRecord(): ReadStatusRecord = ReadStatusRecord(
    status = randomEnum(),
    updatedAtMillis = if (randomBoolean()) randomLong(0L..2_000_000_000L) * 1_000L else null,
    finishedAtMillis = if (randomBoolean()) randomLong(0L..2_000_000_000L) * 1_000L else null,
)
