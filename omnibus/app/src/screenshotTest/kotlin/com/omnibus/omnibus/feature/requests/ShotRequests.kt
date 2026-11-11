package com.omnibus.omnibus.feature.requests

import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.data.requests.BookRequest
import com.omnibus.omnibus.data.requests.CoverOffer
import com.omnibus.omnibus.data.requests.ReleaseProtocol
import com.omnibus.omnibus.data.requests.RequestKind
import com.omnibus.omnibus.data.requests.RequestListing
import com.omnibus.omnibus.data.requests.RequestState
import com.omnibus.omnibus.data.requests.RequestVersion
import com.omnibus.omnibus.data.requests.RequestViewer
import com.omnibus.omnibus.data.requests.StuckReason
import com.omnibus.omnibus.data.requests.Wanter
import com.omnibus.omnibus.feature.requests.page.BookRequestUIState
import com.omnibus.omnibus.feature.requests.page.RequestMessage

/** The clock every request shot reads, in epoch seconds and milliseconds. */
internal const val SHOT_NOW_SECS = 1_700_000_000L
internal const val SHOT_NOW_MILLIS = SHOT_NOW_SECS * 1_000

internal val ShotChooser = RequestViewer(id = 1, canStart = true, canChoose = true)
internal val ShotAsker = RequestViewer(id = 1, canStart = true, canChoose = false)

/** A wished book with no copy in the library, so both kinds can be requested. */
internal fun shotRequestBook(uuid: String = "uuid-1", title: String = "Piranesi"): Book = Book(
    id = 1, uniqueIdentifier = uuid, filename = "piranesi", title = title,
    creators = listOf(Creator("Susanna Clarke", "author", null, 7L)),
    coverUrl = null, accent = null, formats = emptyList(), series = null, seriesIndex = null, hasPhysical = false,
    addedAt = null, lastInteractedAt = null, bookFiles = emptyList(),
)

internal fun shotVersion(
    id: Long,
    title: String = "Piranesi - Susanna Clarke (2020) [Retail EPUB]",
    format: String? = "EPUB",
    sizeBytes: Long? = 1_800_000,
    indexer: String? = "MyAnonamouse",
    seeders: Long? = 142,
    protocol: ReleaseProtocol? = ReleaseProtocol.Torrent,
    bestMatch: Boolean = false,
    rejected: Boolean = false,
) = RequestVersion(id, title, sizeBytes, format, indexer, seeders, protocol, null, bestMatch, rejected)

/** Six versions: a best match, a rejected one, an AZW3 that converts, a Usenet row, and enough to need Show more. */
internal fun shotVersions(): List<RequestVersion> = listOf(
    shotVersion(1, bestMatch = true),
    shotVersion(2, "Piranesi (Clarke, Susanna) - epub", sizeBytes = 2_400_000, indexer = "Libgen", seeders = 12),
    shotVersion(3, "Piranesi.azw3", format = "AZW3", sizeBytes = 900_000, indexer = "Books", seeders = 3),
    shotVersion(4, "Piranesi 2020 retail", indexer = "NZBgeek", seeders = null, protocol = ReleaseProtocol.Usenet),
    shotVersion(5, "Piranesi (pdf scan)", format = "PDF", sizeBytes = 9_100_000, seeders = 1),
    shotVersion(6, "Piranesi [bad rip]", rejected = true),
)

internal fun shotRequest(
    state: RequestState,
    id: Long = 1,
    kind: RequestKind = RequestKind.Ebook,
    userId: Long = 1,
    updatedAt: Long = SHOT_NOW_SECS - 5,
    progress: Double? = null,
    expectedDoneAt: Long? = null,
    cancelAt: Long? = null,
    stuckReason: StuckReason? = null,
    stuckDetail: String? = null,
    chosen: Long? = null,
    versions: List<RequestVersion> = emptyList(),
    coverOffer: CoverOffer? = null,
    cancelledBy: Long? = null,
): BookRequest = BookRequest(
    id = id, bookUuid = "uuid-1", userId = userId, kind = kind, state = state, stuckReason = stuckReason,
    stuckDetail = stuckDetail, chosenResultId = chosen, createdAt = SHOT_NOW_SECS - 600, updatedAt = updatedAt,
    progress = progress, expectedDoneAt = expectedDoneAt, cancelAt = cancelAt, cancelledBy = cancelledBy, note = null,
    coverOffer = coverOffer, versions = versions,
)

/** The request page's state for [lanes] (the first is the main lane) and [history]. */
internal fun shotPage(
    lanes: List<BookRequest>,
    viewer: RequestViewer = ShotChooser,
    history: List<BookRequest> = emptyList(),
    message: RequestMessage? = null,
    wanters: List<Wanter>? = listOf(Wanter(2, "Jordan")),
    book: Book = shotRequestBook(),
): BookRequestUIState {
    val listings = (lanes + history).map { RequestListing(it, book, "Jordan", false, "Jordan".takeIf { _ -> it.cancelledBy != null && it.cancelledBy != viewer.id }) }
    return BookRequestUIState(
        book = book, wanters = wanters, listings = listings, reads = lanes.associateBy { it.id }, mainId = lanes.first().id,
        viewer = viewer, message = message, nowMillis = SHOT_NOW_MILLIS,
    )
}

internal fun shotListing(uuid: String, title: String, request: BookRequest): RequestListing =
    RequestListing(request.copy(bookUuid = uuid), shotRequestBook(uuid, title), "Jordan", false, null)
