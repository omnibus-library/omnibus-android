package com.omnibus.omnibus.data.requests

import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomDouble
import com.omnibus.omnibus.testing.randomEnum
import com.omnibus.omnibus.testing.randomInt
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private fun <T : Any> T.orNull(): T? = takeIf { randomBoolean() }

fun randomRequestVersion(): RequestVersion = RequestVersion(
    id = randomLong(),
    title = randomString(),
    sizeBytes = randomLong(1L..5_000_000_000L).orNull(),
    format = randomString().orNull(),
    indexer = randomString().orNull(),
    seeders = randomLong(0L..100_000L).orNull(),
    protocol = randomEnum<ReleaseProtocol>().orNull(),
    infoUrl = randomString().orNull(),
    bestMatch = randomBoolean(),
    rejected = randomBoolean(),
)

fun randomCoverOffer(): CoverOffer =
    CoverOffer(randomInt(1..4000), randomInt(1..4000), randomInt(1..4000), randomInt(1..4000))

fun randomBookRequest(): BookRequest = BookRequest(
    id = randomLong(),
    bookUuid = randomString(),
    userId = randomLong(),
    kind = randomEnum<RequestKind>(),
    state = randomEnum<RequestState>(),
    stuckReason = randomEnum<StuckReason>().orNull(),
    stuckDetail = randomString().orNull(),
    chosenResultId = randomLong().orNull(),
    createdAt = randomLong(0L..2_000_000_000L),
    updatedAt = randomLong(0L..2_000_000_000L),
    progress = randomDouble().orNull(),
    expectedDoneAt = randomLong(0L..2_000_000_000L).orNull(),
    cancelAt = randomLong(0L..2_000_000_000L).orNull(),
    cancelledBy = randomLong().orNull(),
    note = randomString().orNull(),
    coverOffer = randomCoverOffer().orNull(),
    versions = List(randomInt(0..3)) { randomRequestVersion() },
)

/** A listing whose book is the one [request] is for. */
fun randomRequestListing(): RequestListing = randomBookRequest().let { request ->
    RequestListing(
        request = request,
        book = randomBook().copy(uniqueIdentifier = request.bookUuid),
        requesterName = randomString(),
        requesterHasAvatar = randomBoolean(),
        cancelledByName = randomString().orNull(),
    )
}

fun randomRequestViewer(): RequestViewer = RequestViewer(randomLong(), randomBoolean(), randomBoolean())

fun randomWanter(): Wanter = Wanter(randomLong(), randomString())

fun randomLibraryHit(): LibraryHit = LibraryHit(
    uuid = randomString(),
    title = randomString(),
    authorDisplay = randomString(),
    year = randomString().orNull(),
    formats = List(randomInt(0..3)) { randomString() },
    coverUrl = randomString().orNull(),
    accent = randomString().orNull(),
)

fun randomProviderBook(): ProviderBook {
    val title = randomString()
    val authors = List(randomInt(0..3)) { randomString() }
    val source = randomString()
    return ProviderBook(
        meta = JsonObject(mapOf("title" to JsonPrimitive(title), "source" to JsonPrimitive(source))),
        title = title,
        authors = authors,
        year = randomString().orNull(),
        coverUrl = randomString().orNull(),
        source = source,
    )
}

fun randomMatchedBook(): MatchedBook = MatchedBook(randomString(), randomString(), randomBoolean())

fun randomScanMatch(): ScanMatch =
    ScanMatch(List(randomInt(0..3)) { randomMatchedBook() }, randomEnum<ScanOutcomeKind>())
