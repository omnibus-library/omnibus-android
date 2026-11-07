package com.omnibus.omnibus.data.library

import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomEnum
import com.omnibus.omnibus.testing.randomInt
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString

private fun <T : Any> T.orNull(): T? = takeIf { randomBoolean() }

private fun randomStrings(): List<String> = List(randomInt(0..3)) { randomString() }

fun randomCreator(): Creator = Creator(
    name = randomString(),
    role = randomString().orNull(),
    fileAs = randomString().orNull(),
    id = randomLong().orNull(),
)

fun randomBookFile(): BookFile = BookFile(
    id = randomLong(),
    format = randomString(),
    filename = randomString(),
    ordinal = randomInt(0..100),
    label = randomString().orNull(),
    sizeBytes = randomLong(),
    etag = randomString().orNull(),
)

fun randomBook(): Book = Book(
    id = randomLong(),
    uniqueIdentifier = randomString().orNull(),
    filename = randomString(),
    title = randomString().orNull(),
    creators = List(randomInt(0..3)) { randomCreator() },
    coverUrl = randomString().orNull(),
    accent = randomString().orNull(),
    formats = randomStrings(),
    series = randomString().orNull(),
    seriesIndex = randomString().orNull(),
    hasPhysical = randomBoolean(),
    addedAt = randomString().orNull(),
    lastInteractedAt = randomString().orNull(),
    bookFiles = List(randomInt(0..3)) { randomBookFile() },
    description = randomString().orNull(),
    genres = randomStrings(),
    subjects = randomStrings(),
    publisher = randomString().orNull(),
    publishedDate = randomString().orNull(),
    language = randomString().orNull(),
    isbn13 = randomString().orNull(),
    seriesId = randomLong().orNull(),
    isWishlisted = randomBoolean(),
    wishlistSource = randomString().orNull(),
)

fun randomLibrarySortPreference(): LibrarySortPreference =
    LibrarySortPreference(sort = randomEnum<LibrarySort>(), direction = randomEnum<SortDirection>())
