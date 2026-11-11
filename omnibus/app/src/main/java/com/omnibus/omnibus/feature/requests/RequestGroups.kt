package com.omnibus.omnibus.feature.requests

import com.omnibus.omnibus.data.requests.BookRequest
import com.omnibus.omnibus.data.requests.RequestKind
import com.omnibus.omnibus.data.requests.RequestListing
import com.omnibus.omnibus.data.requests.RequestState
import com.omnibus.omnibus.data.requests.RequestViewer
import com.omnibus.omnibus.data.requests.holds

// Pure decisions for the Requests list. They port the web's `requestGroups.ts` and iOS `RequestsFlow`, phone branch only.

/** How often the list is read again while anything is moving. */
internal const val LIST_POLL_MILLIS = 10_000L

/** Where a book stands on the list, most urgent first. */
enum class RequestGroupKey {
    NeedsYou, OnItsWay, Looking, Stuck, Ready, Cancelled;

    /** The colour of a row's status dot. */
    val tone: RequestTone
        get() = when (this) {
            NeedsYou -> RequestTone.Accent
            Stuck -> RequestTone.Bad
            Ready -> RequestTone.Ok
            Cancelled -> RequestTone.Ink3
            OnItsWay, Looking -> RequestTone.Ink1
        }
}

/** A palette colour, named so the decision stays testable. */
enum class RequestTone { Accent, Bad, Ok, Ink1, Ink3 }

/** Only Cancelled starts folded. */
internal val DefaultFolded: Set<RequestGroupKey> = setOf(RequestGroupKey.Cancelled)

/** One group of the list: its books, each by the request it is filed under. */
data class RequestGroup(val key: RequestGroupKey, val items: List<RequestListing>)

/** [key]'s label above its books. */
internal fun groupLabel(key: RequestGroupKey, wording: RequestWording = RequestWording()): String = when (key) {
    RequestGroupKey.NeedsYou -> wording.groupNeedsYou
    RequestGroupKey.OnItsWay -> wording.groupOnItsWay
    RequestGroupKey.Looking -> wording.groupLooking
    RequestGroupKey.Stuck -> wording.groupStuck
    RequestGroupKey.Ready -> wording.groupReady
    RequestGroupKey.Cancelled -> wording.groupCancelled
}

/** The group a request files its book under. A pick still to make needs only a reader who can choose. */
internal fun groupOf(request: BookRequest, viewer: RequestViewer): RequestGroupKey = when (request.state) {
    RequestState.Choose -> if (viewer.canChoose) RequestGroupKey.NeedsYou else RequestGroupKey.Looking
    RequestState.Looking -> RequestGroupKey.Looking
    RequestState.Chosen, RequestState.OnItsWay -> RequestGroupKey.OnItsWay
    RequestState.Stuck -> RequestGroupKey.Stuck
    RequestState.Ready -> RequestGroupKey.Ready
    RequestState.Cancelled -> RequestGroupKey.Cancelled
}

private class Filed(val key: RequestGroupKey, val listing: RequestListing)

/**
 * The Requests list: one item per book, filed under its most urgent request (the newest of equals, the last cancelled
 * among cancelled ones), in group order, with empty groups left out. [listings] come newest first, and a book keeps the
 * slot where its newest listing appeared, as the web's insertion-ordered `Map` does.
 */
internal fun groupRequests(listings: List<RequestListing>, viewer: RequestViewer): List<RequestGroup> {
    val filed = LinkedHashMap<String, Filed>()
    for (listing in listings) {
        val key = groupOf(listing.request, viewer)
        val held = filed[listing.book.uuid]
        val lastCancelled = held != null && key == RequestGroupKey.Cancelled && key == held.key &&
            listing.request.updatedAt > held.listing.request.updatedAt
        if (held == null || key < held.key || lastCancelled) filed[listing.book.uuid] = Filed(key, listing)
    }
    val items = filed.values.toList()
    return RequestGroupKey.entries.map { key -> RequestGroup(key, items.filter { it.key == key }.map { it.listing }) }
        .filter { it.items.isNotEmpty() }
}

/** The request the page opens a lane on: the one the list files its most urgent book under. */
internal fun mainRequest(listings: List<RequestListing>, viewer: RequestViewer): RequestListing? =
    groupRequests(listings, viewer).firstOrNull()?.items?.firstOrNull()

/** Where a request stands, after its format. A phone drops the group's own word, so a download reads "62%". */
private fun whereItStands(request: BookRequest, group: RequestGroupKey, wording: RequestWording): String = when (request.state) {
    RequestState.Choose -> if (group == RequestGroupKey.NeedsYou) wording.chooseAVersion else wording.choosingAVersion
    RequestState.Looking -> wording.lookingForAVersion
    RequestState.Chosen -> wording.onItsWayLower
    RequestState.OnItsWay -> downloadPercent(request)?.let { "$it%" } ?: wording.onItsWayLower
    RequestState.Stuck -> stateView(RequestState.Stuck, request.stuckReason, wording = wording).heading.lowercase()
    RequestState.Ready -> if (request.kind == RequestKind.Audiobook) wording.readyToListenLower else wording.readyToReadLower
    RequestState.Cancelled -> wording.cancelledLower
}

/** A row's second line: its format, then where it stands ("Ebook · 62%"). */
internal fun itemLine(listing: RequestListing, group: RequestGroupKey, wording: RequestWording = RequestWording()): String =
    wording.itemLine(kindLabel(listing.request.kind, wording), whereItStands(listing.request, group, wording))

/** "7 books · 1 needs you", the second half only when one does. Cancelled books aren't counted. */
internal fun countsLine(groups: List<RequestGroup>, wording: RequestWording = RequestWording()): String {
    val books = groups.filter { it.key != RequestGroupKey.Cancelled }.sumOf { it.items.size }
    val needs = groups.firstOrNull { it.key == RequestGroupKey.NeedsYou }?.items?.size ?: 0
    return wording.countsLine(wording.books(books), if (needs == 0) null else wording.needsYou(needs))
}

/** How many books wait on the viewer to pick a version. */
internal fun needsYouCount(listings: List<RequestListing>, viewer: RequestViewer): Int =
    groupRequests(listings, viewer).firstOrNull { it.key == RequestGroupKey.NeedsYou }?.items?.size ?: 0

/** The You tab's pill: null when nothing needs the viewer. */
internal fun needsYouPill(count: Int, wording: RequestWording = RequestWording()): String? =
    if (count == 0) null else wording.needsYou(count)

/** How long until the list is read again: every ten seconds while anything is moving, else null. */
internal fun listPollIntervalMillis(listings: List<RequestListing>?): Long? =
    LIST_POLL_MILLIS.takeIf { listings?.any { isMoving(it.request.state) } == true }

/** What a library book calls for on the Request a book sheet. */
internal enum class AddAction {
    /** Its ebook request is going: open it. */
    Open,

    /** The library already holds an ebook: nothing to request. */
    Held,

    Start,
}

/** Each book with an ebook request still going (finished and cancelled ones don't count), by uuid. */
internal fun requestedBooks(listings: List<RequestListing>): Set<String> = listings
    .filter { it.request.kind == RequestKind.Ebook && it.request.state != RequestState.Ready && it.request.state != RequestState.Cancelled }
    .map { it.book.uuid }
    .toSet()

/** Open its request, say its ebook is here, or start one. A lookup that sends no [formats] counts any file as an ebook. */
internal fun addAction(uuid: String, formats: List<String>?, hasFiles: Boolean, requested: Set<String>): AddAction = when {
    uuid in requested -> AddAction.Open
    (formats?.let { holds(RequestKind.Ebook, it) } ?: hasFiles) -> AddAction.Held
    else -> AddAction.Start
}

/** The heading over the providers' results: "From Hardcover and Open Library", or a general line when none answered. */
internal fun providersHeading(sources: List<String>, wording: RequestWording = RequestWording()): String {
    val names = sources.map { source -> source.split('_').joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } } }.distinct()
    return when (names.size) {
        0 -> wording.fromOnlineProviders
        1 -> wording.fromProviders(names.single())
        else -> wording.fromProviders(names.dropLast(1).joinToString(", ") + " ${wording.and} " + names.last())
    }
}
