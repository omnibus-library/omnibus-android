package com.omnibus.omnibus.data.library

import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomDouble
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

fun randomShelf(): Shelf = Shelf(
    id = randomLong(),
    ownerUserId = randomLong(),
    ownerUsername = randomString(),
    kind = randomEnum<ShelfKind>(),
    name = randomString(),
    visibility = randomEnum<ShelfVisibility>(),
    accent = randomString().orNull(),
    bookCount = randomLong(0L..500L),
)

fun randomShelfPreview(): ShelfPreview = ShelfPreview(randomShelf(), List(randomInt(0..4)) { randomString() })

fun randomLibrarySortPreference(): LibrarySortPreference =
    LibrarySortPreference(sort = randomEnum<LibrarySort>(), direction = randomEnum<SortDirection>())

fun randomResolvedPosition(): ResolvedPosition = ResolvedPosition(
    chapterOrdinal = randomInt(1..50),
    chaptersTotal = randomInt(1..50),
    chapterTitle = randomString().orNull(),
    percentThroughBook = randomDouble(0.0, 100.0).orNull(),
    confidence = randomEnum<PositionConfidence>(),
)

fun randomProgressRecord(): ProgressRecord = ProgressRecord(
    bookUuid = randomString(),
    format = randomEnum<ProgressFormat>(),
    epubCfi = randomString().orNull(),
    audioPositionSeconds = randomDouble(0.0, 10_000.0).orNull(),
    progressPercent = randomDouble(0.0, 100.0).orNull(),
    updatedAt = randomLong(),
    clientUpdatedAt = randomLong().orNull(),
    bookFileId = randomLong().orNull(),
    totalDurationSeconds = randomDouble(0.0, 10_000.0).orNull(),
    resolved = randomResolvedPosition().orNull(),
)

/** A point whose record names its book, as the server's does. */
fun randomResumePoint(): ResumePoint = randomBook().let { book ->
    ResumePoint(
        record = randomProgressRecord().copy(bookUuid = book.uuid),
        book = book,
        audioPart = randomInt(1..10).orNull(),
        audioPartCount = randomInt(1..10).orNull(),
        playbackRate = randomDouble(0.5, 3.0).orNull(),
    )
}

/** This point about [book], with its record naming that book. */
fun ResumePoint.about(book: Book): ResumePoint = copy(book = book, record = record.copy(bookUuid = book.uuid))

/** This point with [change] applied to its record. */
fun ResumePoint.withRecord(change: ProgressRecord.() -> ProgressRecord): ResumePoint = copy(record = record.change())
