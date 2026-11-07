package com.omnibus.omnibus.data.journals

import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString

/** The server sends whole seconds, so the creation time is whole seconds in milliseconds. */
fun randomJournalEntry(): JournalEntry = JournalEntry(
    id = randomLong(),
    authorId = randomLong(),
    authorName = randomString(),
    bodyMd = randomString(),
    isDraft = randomBoolean(),
    createdAtMillis = if (randomBoolean()) randomLong(0L..2_000_000_000L) * 1_000L else null,
)
