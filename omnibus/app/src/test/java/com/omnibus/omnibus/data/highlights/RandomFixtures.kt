package com.omnibus.omnibus.data.highlights

import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomDouble
import com.omnibus.omnibus.testing.randomEnum
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString

/** The server sends whole seconds, so the creation time is whole seconds in milliseconds. */
fun randomHighlight(): Highlight = Highlight(
    id = randomLong(),
    text = if (randomBoolean()) randomString() else null,
    note = if (randomBoolean()) randomString() else null,
    color = randomEnum(),
    location = if (randomBoolean()) randomString() else null,
    format = if (randomBoolean()) randomString() else null,
    createdAtMillis = if (randomBoolean()) randomLong(0L..2_000_000_000L) * 1_000L else null,
    fraction = if (randomBoolean()) randomDouble().toFloat() else null,
)
