package com.omnibus.omnibus.data.household

import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString

fun randomHouseholdReader(): HouseholdReader = HouseholdReader(
    userId = randomLong(),
    name = randomString(),
    hasAvatar = randomBoolean(),
    seconds = randomLong(0L..1_000_000L),
    sessions = randomLong(0L..1_000L),
    lastSessionAtMillis = if (randomBoolean()) randomLong() else null,
)
