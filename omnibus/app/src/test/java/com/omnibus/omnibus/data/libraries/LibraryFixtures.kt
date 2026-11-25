package com.omnibus.omnibus.data.libraries

import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import java.util.UUID

fun randomLibrary(): Library = Library(
    id = UUID(randomLong(), randomLong()).toString(),
    address = "https://${randomString().lowercase()}.example",
    name = randomString(),
    username = randomString().takeIf { randomBoolean() },
    addedAt = randomLong(),
    lastUsedAt = randomLong(),
    isActive = randomBoolean(),
)
