package com.omnibus.omnibus.feature.requests

import com.omnibus.omnibus.data.requests.BookRequest
import com.omnibus.omnibus.data.requests.CoverOffer
import com.omnibus.omnibus.data.requests.ReleaseProtocol
import com.omnibus.omnibus.data.requests.RequestKind
import com.omnibus.omnibus.data.requests.RequestListing
import com.omnibus.omnibus.data.requests.RequestState
import com.omnibus.omnibus.data.requests.RequestVersion
import com.omnibus.omnibus.data.requests.RequestViewer
import com.omnibus.omnibus.data.requests.RequestsError
import com.omnibus.omnibus.data.requests.StuckReason
import com.omnibus.omnibus.data.requests.Wanter
import com.omnibus.omnibus.data.requests.holds
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.roundToInt

// Pure rules for the request page. They port the phone branches of the web's `requestView.ts` and iOS
// `BookRequestFlow`, with no Compose or Android types. Every time is Unix seconds on the wire and epoch milliseconds
// here, as `TimeSource` gives them.

/** Joins the parts of a line; non-breaking before the dot, so a line never starts with a separator. */
internal const val SEP = " · "

/** How often the page reads again while any lane is moving. */
internal const val DETAIL_POLL_MILLIS = 2_000L

/** How long a search runs before its lane says it is still going. */
internal const val SEARCH_SLOW_AFTER_SECS = 20L

/** How long a search may run before its request offers Try again: past the first attempt's 120 s timeout. */
internal const val SEARCH_STALE_AFTER_SECS = 150L

/** How much of the server's undo window is left unused, so Undo is never offered after the grace. */
internal const val UNDO_MARGIN_MILLIS = 1_500L

/** How many versions that can be chosen show before Show more. */
private const val SHOWN_AT_FIRST = 4

private const val MILLIS = 1_000L

/** A request state in Omnibus's words rather than the downloader's. [note] is null where the lane's own content says it. */
internal data class RequestStateView(val heading: String, val note: String?)

/** A state's heading and the line under it. A stuck request prefers the error the server recorded. */
internal fun stateView(
    state: RequestState,
    stuckReason: StuckReason? = null,
    kind: RequestKind = RequestKind.Ebook,
    stuckDetail: String? = null,
    wording: RequestWording = RequestWording(),
): RequestStateView = when (state) {
    RequestState.Looking -> RequestStateView(wording.looking, wording.lookingNote)
    RequestState.Choose -> RequestStateView(wording.choose, null)
    RequestState.Chosen -> RequestStateView(wording.chosen, wording.chosenNote)
    RequestState.OnItsWay -> RequestStateView(wording.onItsWay, wording.onItsWayNote)
    RequestState.Ready ->
        RequestStateView(if (kind == RequestKind.Audiobook) wording.readyToListen else wording.readyToRead, wording.readyNote)
    RequestState.Stuck -> stuckView(stuckReason, stuckDetail, wording)
    RequestState.Cancelled -> RequestStateView(wording.cancelled, wording.cancelledNote)
}

private fun stuckView(reason: StuckReason?, detail: String?, wording: RequestWording): RequestStateView {
    val recorded = detail?.takeIf { it.isNotBlank() }?.let(::capitalised)
    return when (reason) {
        StuckReason.StoppedMoving -> RequestStateView(wording.stoppedMoving, recorded ?: wording.stoppedMovingNote)
        StuckReason.CouldntAdd -> RequestStateView(wording.couldntAdd, recorded ?: wording.couldntAddNote)
        StuckReason.NotFound, StuckReason.SearchFailed, null ->
            RequestStateView(wording.couldntFind, recorded ?: wording.couldntFindNote)
    }
}

/** [text] opening a sentence. A lower-case word with a capital after it, such as "qBittorrent", is a name and stays. */
private fun capitalised(text: String): String =
    if (text.length >= 2 && text[0] in 'a'..'z' && text[1] in 'A'..'Z') text else text.replaceFirstChar { it.uppercase() }

/** Whether a request is moving without the reader: searching, picked or downloading. */
internal fun isMoving(state: RequestState): Boolean =
    state == RequestState.Looking || state == RequestState.Chosen || state == RequestState.OnItsWay

/** Whether a request is still going for its reader. */
internal fun isLive(state: RequestState): Boolean = state == RequestState.Choose || isMoving(state)

/** Whether a request's picked version is still downloading. */
internal fun isDownloading(request: BookRequest): Boolean =
    request.state == RequestState.OnItsWay && (request.progress ?: 0.0) < 1.0

/** Whether Cancel is offered: any request still going, until its download is complete. */
internal fun isCancellable(request: BookRequest): Boolean =
    if (request.state == RequestState.OnItsWay) isDownloading(request) else isLive(request.state)

/** Whether [viewer] may cancel [request]: anyone who can pick versions, or the reader who made it. */
internal fun canCancel(viewer: RequestViewer, request: BookRequest): Boolean =
    viewer.canChoose || viewer.id == request.userId

/** Whether Cancel is offered on [request]: the viewer may cancel it and it can still be cancelled. */
internal fun offersCancel(viewer: RequestViewer, request: BookRequest): Boolean = canCancel(viewer, request) && isCancellable(request)

/** A download's progress as a whole percent, rounded down and held under 100 until it is done, so the bar never reads done early. */
internal fun progressPercent(progress: Double?): Int {
    // Rounded to a hundredth of a percent first, so 0.29 * 100 = 28.999… still reads 29.
    val percent = (Math.round((progress ?: 0.0) * 1e4) / 100).toInt()
    return if (progress != null && progress < 1.0) minOf(percent, 99) else percent
}

/** The percent a download shows while it runs; null before the client reports, once it is complete, or for any other state. */
internal fun downloadPercent(request: BookRequest): Int? =
    request.progress?.takeIf { isDownloading(request) }?.let(::progressPercent)

/** What a request's format is called: "Ebook" or "Audiobook". */
internal fun kindLabel(kind: RequestKind, wording: RequestWording = RequestWording()): String =
    if (kind == RequestKind.Ebook) wording.ebook else wording.audiobook

/** The file format a request's chip names. */
internal fun kindChip(kind: RequestKind): String = if (kind == RequestKind.Ebook) "EPUB" else "M4B"

/** The version a request picked, if any. */
internal fun pickedVersion(request: BookRequest): RequestVersion? = request.versions.firstOrNull { it.id == request.chosenResultId }

// Stepper and download

internal enum class StepStatus { Done, Current, Todo }

/** One step of a lane's stepper. [percent] is the download's, on the On its way step while it runs. */
internal data class RequestStep(val label: String, val status: StepStatus, val percent: Int?)

/** The step each state is on; ready has passed them all. */
private fun stepIndex(state: RequestState): Int = when (state) {
    RequestState.Looking -> 0
    RequestState.Choose -> 1
    RequestState.Chosen, RequestState.OnItsWay -> 2
    RequestState.Ready, RequestState.Stuck, RequestState.Cancelled -> 4
}

/** A lane's stepper, Looking, Choose a version, On its way, Ready; null for a stuck or cancelled request, which says so in words. */
internal fun stepsFor(request: BookRequest, wording: RequestWording = RequestWording()): List<RequestStep>? {
    if (request.state == RequestState.Stuck || request.state == RequestState.Cancelled) return null
    val at = stepIndex(request.state)
    fun status(step: Int) = when {
        step < at -> StepStatus.Done
        step == at -> StepStatus.Current
        else -> StepStatus.Todo
    }
    val found = request.versions.size
    return listOf(
        RequestStep(if (at > 0 && found > 0) wording.lookingFound(found) else wording.looking, status(0), null),
        RequestStep(if (at > 1) wording.choseAVersion else wording.choose, status(1), null),
        RequestStep(wording.onItsWay, status(2), downloadPercent(request)),
        RequestStep(wording.ready, status(3), null),
    )
}

/** How long a download has left at [nowMillis], roughly; null when the client can't tell, the time has come, or it is complete. */
internal fun timeLeft(request: BookRequest, nowMillis: Long, wording: RequestWording = RequestWording()): String? {
    val expected = request.expectedDoneAt ?: return null
    if ((request.progress ?: 0.0) >= 1.0) return null
    val secs = expected - nowMillis / MILLIS.toDouble()
    return when {
        secs <= 0 -> null
        secs < 60 -> wording.lessThanAMinute
        else -> (secs / 60).roundToInt().let { minutes ->
            if (minutes < 60) wording.minutesLeft(minutes) else wording.hoursLeft((secs / 3600).roundToInt())
        }
    }
}

/** How long ago, in the web's words: "just now", "12m ago", "yesterday", "5d ago". */
internal fun recency(sinceSecs: Long, nowMillis: Long, wording: RequestWording = RequestWording()): String {
    val secs = max(0L, nowMillis / MILLIS - sinceSecs)
    return when {
        secs < 90 -> wording.justNow
        secs < 3_600 -> wording.minutesAgo(secs / 60)
        secs < 86_400 -> wording.hoursAgo(secs / 3_600)
        secs < 172_800 -> wording.yesterday
        else -> wording.daysAgo(secs / 86_400)
    }
}

/** Who made a request and how long ago: "You · just now". */
internal fun askedBy(listing: RequestListing, viewerId: Long, nowMillis: Long, wording: RequestWording = RequestWording()): String {
    val name = if (listing.request.userId == viewerId) wording.you else listing.requesterName
    return name + SEP + recency(listing.request.createdAt, nowMillis, wording)
}

// Versions

/** A size in decimal units, kept on one line; null when the source did not say. */
internal fun versionSize(bytes: Long?): String? {
    if (bytes == null || bytes <= 0) return null
    val size = when {
        bytes >= 1_000_000_000 -> "%.1f GB".format(Locale.US, bytes / 1e9)
        bytes >= 1_000_000 -> "%.1f MB".format(Locale.US, bytes / 1e6)
        bytes >= 1_000 -> "%.1f KB".format(Locale.US, bytes / 1e3)
        else -> "$bytes B"
    }
    return size.replace(' ', ' ')
}

private fun join(parts: List<String?>): String = parts.filterNotNull().joinToString(SEP)

/** How a version is shared: Usenet, its seeds, or null for a torrent whose seeds are unknown. */
private fun sharing(version: RequestVersion, wording: RequestWording): String? {
    if (version.protocol == ReleaseProtocol.Usenet) return wording.usenet
    val seeders = version.seeders ?: return null
    return wording.seeds(NumberFormat.getIntegerInstance(Locale.US).format(seeders), seeders)
}

/** "MyAnonamouse · EPUB · 1.8 MB · 142 seeds". A missing source or seed count drops out. */
internal fun versionSource(version: RequestVersion, wording: RequestWording = RequestWording()): String = join(
    listOf(
        version.indexer,
        version.format ?: wording.formatUnknown,
        versionSize(version.sizeBytes) ?: wording.sizeUnknown,
        sharing(version, wording),
    ),
)

/** Whether adding the version converts it to EPUB first. Mirrors the server's convertible formats. */
internal fun convertsToEpub(version: RequestVersion): Boolean = version.format == "AZW3" || version.format == "MOBI"

/** The line under a picked version's name: its size and source, each left out when unknown. */
internal fun pickedMeta(version: RequestVersion): String = join(listOf(versionSize(version.sizeBytes), version.indexer))

/** The heading a picked version gets: its format and size, each left out when unknown. */
internal fun pickedHeading(version: RequestVersion): String = join(listOf(version.format, versionSize(version.sizeBytes)))

/** The caption over the versions: how many can be chosen, and that the best match leads when one does. */
internal fun versionsFound(versions: List<RequestVersion>, wording: RequestWording = RequestWording()): String {
    val choosable = versions.filter { !it.rejected }
    val count = wording.versionsFound(choosable.size)
    return if (choosable.any { it.bestMatch }) count + SEP + wording.bestMatchFirst else count
}

/** The versions shown before Show more: the first four that can be chosen, and every one that was tried. */
internal fun firstShown(versions: List<RequestVersion>): List<RequestVersion> {
    val offerable = versions.filter { !it.rejected }.take(SHOWN_AT_FIRST).map { it.id }.toSet()
    return versions.filter { it.rejected || it.id in offerable }
}

/** The cover offer's sizes in pixels, as the card says them. It never says where the existing cover came from. */
internal fun coverSizesLine(offer: CoverOffer, wording: RequestWording = RequestWording()): String =
    wording.coverSizes(offer.width, offer.height, offer.currentWidth, offer.currentHeight)

// Failures

/** Why starting a request failed, as a sentence for a message. */
internal fun startFailure(error: RequestsError, wording: RequestWording = RequestWording()): String =
    when ((error as? RequestsError.Http)?.code) {
        429 -> wording.tooManyRequests
        403 -> wording.cantRequest
        404 -> wording.bookGone
        else -> wording.couldntStart
    }

/** The writes a lane makes after it has started. */
internal enum class RequestAction { Choose, Cancel, Undo, Swap, Retry, TakeCover, KeepCover }

/** A message for a failed write: the action, then the server's message, or a plain line for any other failure. */
internal fun actionFailure(action: RequestAction, error: RequestsError, wording: RequestWording = RequestWording()): String {
    val prefix = when (action) {
        RequestAction.Choose -> wording.failureChoose
        RequestAction.Cancel -> wording.failureCancel
        RequestAction.Undo -> wording.failureUndo
        RequestAction.Swap -> wording.failureSwap
        RequestAction.Retry -> wording.failureRetry
        RequestAction.TakeCover -> wording.failureTakeCover
        RequestAction.KeepCover -> wording.failureKeepCover
    }
    val message = when (error) {
        is RequestsError.Http -> error.message ?: wording.serverError(error.code)
        else -> wording.somethingWentWrong
    }
    return wording.failureLine(prefix, message)
}

// Gating

/** How long the Undo message stays: until 1.5 s before the server's `cancel_at`. Null without one, so no Undo is offered. */
internal fun undoDelayMillis(cancelAt: Long?, nowMillis: Long): Long? =
    cancelAt?.let { max(0L, it * MILLIS - nowMillis - UNDO_MARGIN_MILLIS) }

/**
 * Whether Request again is offered: a cancelled request whose undo has run out, to a reader who may request and has no
 * request of that kind going or finished for the book already.
 */
internal fun offersRequestAgain(
    request: BookRequest,
    viewer: RequestViewer,
    bookRequests: List<BookRequest>,
    nowMillis: Long,
): Boolean {
    val undoOpen = request.cancelAt?.let { nowMillis < it * MILLIS } ?: false
    val alreadyGoing = bookRequests.any {
        it.userId == viewer.id && it.kind == request.kind && (isLive(it.state) || it.state == RequestState.Ready)
    }
    return request.state == RequestState.Cancelled && viewer.canStart && !undoOpen && !alreadyGoing
}

/** Whether a search that began at [updatedAt] has run long enough by [nowMillis] to say it is still going. */
internal fun isSearchSlow(updatedAt: Long, nowMillis: Long): Boolean = nowMillis - updatedAt * MILLIS >= SEARCH_SLOW_AFTER_SECS * MILLIS

/** Whether a search that began at [updatedAt] has run past its timeout by [nowMillis]. */
internal fun isSearchStale(updatedAt: Long, nowMillis: Long): Boolean = nowMillis - updatedAt * MILLIS >= SEARCH_STALE_AFTER_SECS * MILLIS

/** What the Looking card says while a search runs. */
internal fun lookingLabel(request: BookRequest, nowMillis: Long, wording: RequestWording = RequestWording()): String =
    if (isSearchSlow(request.updatedAt, nowMillis)) wording.lookingSlowLabel else wording.lookingLabel

/** Whether a stale search offers Try again, which only a reader who can pick versions may press. */
internal fun offersLookingRetry(request: BookRequest, viewer: RequestViewer, nowMillis: Long): Boolean =
    viewer.canChoose && isSearchStale(request.updatedAt, nowMillis)

/** The kinds the viewer may start that the library lacks and no request, in any state, covers. */
internal fun unrequestedKinds(formats: List<String>, listings: List<RequestListing>, viewer: RequestViewer): List<RequestKind> {
    if (!viewer.canStart) return emptyList()
    return RequestKind.entries.filter { kind -> !holds(kind, formats) && listings.none { it.request.kind == kind } }
}

/** How often the page reads again: every two seconds while any lane is moving, else null. */
internal fun detailPollIntervalMillis(requests: List<BookRequest>): Long? =
    DETAIL_POLL_MILLIS.takeIf { requests.any { isMoving(it.state) } }

/** A book's requests besides the one on screen: those going, stuck or finished get a lane, and the history keeps the rest. */
internal data class BookSplit(val lanes: List<RequestListing>, val history: List<RequestListing>)

/**
 * Splits [listings] around the request [shown]. A request that is going, stuck or ready gets a lane, so a finished one
 * stays on the page. The history takes the cancelled ones and any going request that swapped a version.
 */
internal fun splitBookRequests(listings: List<RequestListing>, shown: Long): BookSplit {
    val others = listings.filter { it.request.id != shown }
    return BookSplit(
        lanes = others.filter { isLive(it.request.state) || it.request.state == RequestState.Stuck || it.request.state == RequestState.Ready },
        history = others.filter {
            it.request.state == RequestState.Cancelled ||
                (isLive(it.request.state) && it.request.versions.any { v -> v.rejected })
        },
    )
}

// History

private fun versionFacts(version: RequestVersion, wording: RequestWording): List<String> =
    listOf(version.format ?: wording.unknown) + listOfNotNull(versionSize(version.sizeBytes))

/** A history row's day, "Oct 12", on [zone]'s calendar. */
internal fun historyDay(unixSecs: Long, zone: TimeZone = TimeZone.getDefault()): String =
    SimpleDateFormat("MMM d", Locale.US).apply { timeZone = zone }.format(Date(unixSecs * MILLIS))

/** What happened to a request: "Ebook cancelled by you · Oct 12". */
internal fun historyLine(
    listing: RequestListing,
    viewerId: Long,
    zone: TimeZone = TimeZone.getDefault(),
    wording: RequestWording = RequestWording(),
): String {
    val request = listing.request
    val head = when {
        request.state != RequestState.Cancelled ->
            stateView(request.state, request.stuckReason, request.kind, null, wording).heading
        request.cancelledBy == viewerId -> wording.cancelledByYou
        listing.cancelledByName != null -> wording.cancelledBy(listing.cancelledByName)
        else -> wording.cancelled
    }
    // The day keeps its space unbreakable, so "Nov 10" never wraps between its words.
    return wording.historyLine(
        kindLabel(request.kind, wording),
        head.replaceFirstChar { it.lowercase() },
        historyDay(request.updatedAt, zone).replace(' ', '\u00a0'),
    )
}

/** The version and how far it got: "EPUB · 2.4 MB · stopped at 40%". Null when neither is known. */
internal fun historyDetails(listing: RequestListing, wording: RequestWording = RequestWording()): String? {
    val request = listing.request
    val details = pickedVersion(request)?.let { versionFacts(it, wording) }.orEmpty().toMutableList()
    val ended = request.state == RequestState.Cancelled || request.state == RequestState.Stuck
    val progress = request.progress
    if (ended && progress != null && progress < 1.0) details += wording.stoppedAt(progressPercent(progress))
    return details.takeIf { it.isNotEmpty() }?.joinToString(SEP)
}

/** One line per version the request gave up for another. */
internal data class SwapFact(val id: Long, val text: String)

/** The swap lines of [listing], keyed by version id. */
internal fun swapFacts(listing: RequestListing, wording: RequestWording = RequestWording()): List<SwapFact> =
    listing.request.versions.filter { it.rejected }.map {
        SwapFact(it.id, (listOf(wording.swappedForAnother) + versionFacts(it, wording)).joinToString(" · "))
    }

/** Who wants the book, "On Jordan’s and your wishlists", with the viewer as "your". Null when nobody does. */
internal fun wishersLine(wanters: List<Wanter>, viewerId: Long, wording: RequestWording = RequestWording()): String? {
    val owners = wanters.map { if (it.id == viewerId) wording.your else wording.possessive(it.name) }
    val last = owners.lastOrNull() ?: return null
    val others = owners.dropLast(1)
    return if (others.isEmpty()) wording.onWishlist(last) else wording.onWishlists(others.joinToString(", "), last)
}
