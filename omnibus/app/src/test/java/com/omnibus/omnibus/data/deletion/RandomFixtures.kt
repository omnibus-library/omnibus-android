package com.omnibus.omnibus.data.deletion

import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString

private fun randomNullableString(): String? = if (randomBoolean()) randomString() else null

fun randomDeletableFile(): DeletableFile = DeletableFile(
    id = randomLong(),
    format = randomString(),
    filename = randomString(),
    label = randomNullableString(),
    sizeBytes = randomLong(),
)

fun randomDeletableCopy(): DeletableCopy =
    DeletableCopy(id = randomLong(), isbn = randomNullableString(), note = randomNullableString())

fun randomDeletionImpact(): DeletionImpact = DeletionImpact(
    highlights = randomLong(),
    journalEntries = randomLong(),
    bookmarks = randomLong(),
    readingSessions = randomLong(),
    listeningSessions = randomLong(),
    reviews = randomLong(),
    shelves = randomLong(),
)
