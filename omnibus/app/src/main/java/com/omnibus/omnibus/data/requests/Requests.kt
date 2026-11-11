package com.omnibus.omnibus.data.requests

import com.omnibus.omnibus.data.auth.CurrentUser
import com.omnibus.omnibus.data.library.Book
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Which format a request fetches. The wire names are decoded strictly: a value this app doesn't know is an error. */
@Serializable
enum class RequestKind {
    @SerialName("ebook")
    Ebook,

    @SerialName("audiobook")
    Audiobook,
}

/** Where a request stands. */
@Serializable
enum class RequestState {
    @SerialName("looking")
    Looking,

    @SerialName("choose")
    Choose,

    @SerialName("chosen")
    Chosen,

    @SerialName("on_its_way")
    OnItsWay,

    @SerialName("ready")
    Ready,

    @SerialName("stuck")
    Stuck,

    @SerialName("cancelled")
    Cancelled,
}

/** Why a request is stuck. */
@Serializable
enum class StuckReason {
    @SerialName("not_found")
    NotFound,

    @SerialName("search_failed")
    SearchFailed,

    @SerialName("stopped_moving")
    StoppedMoving,

    @SerialName("couldnt_add")
    CouldntAdd,
}

/** How a version is fetched. */
@Serializable
enum class ReleaseProtocol {
    @SerialName("torrent")
    Torrent,

    @SerialName("usenet")
    Usenet,
}

/** One version of the book the search found. */
data class RequestVersion(
    val id: Long,
    val title: String,
    val sizeBytes: Long?,
    val format: String?,
    val indexer: String?,
    val seeders: Long?,
    val protocol: ReleaseProtocol?,
    val infoUrl: String?,
    val bestMatch: Boolean,
    val rejected: Boolean,
)

/** A sharper cover a finished download brought, and the book's current one, both in pixels. */
data class CoverOffer(val width: Int, val height: Int, val currentWidth: Int, val currentHeight: Int)

/**
 * One request. Times are Unix seconds throughout, as the wire has them. [coverOffer] and the full [versions] come only
 * from a single request's read, not from the list routes.
 */
data class BookRequest(
    val id: Long,
    val bookUuid: String,
    val userId: Long,
    val kind: RequestKind,
    val state: RequestState,
    val stuckReason: StuckReason?,
    val stuckDetail: String?,
    val chosenResultId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    /** 0 to 1 while the download runs. */
    val progress: Double?,
    val expectedDoneAt: Long?,
    /** While cancelled, when the request stops being undoable. */
    val cancelAt: Long?,
    val cancelledBy: Long?,
    val note: String?,
    val coverOffer: CoverOffer?,
    val versions: List<RequestVersion>,
)

/** A request as a list shows it: its book, who made it and who cancelled it. */
data class RequestListing(
    val request: BookRequest,
    val book: Book,
    val requesterName: String,
    val requesterHasAvatar: Boolean,
    val cancelledByName: String?,
)

/** What the signed-in reader may do with requests. */
data class RequestViewer(val id: Long, val canStart: Boolean, val canChoose: Boolean)

/** Nobody signed in: may do nothing. */
val NobodyViewer = RequestViewer(id = 0, canStart = false, canChoose = false)

/**
 * The viewer a session makes. Every permission waits on the server having book requests on for the reader, so with it
 * off nothing is offered whatever the stored grants say. No user is nobody.
 */
fun CurrentUser?.toRequestViewer(): RequestViewer = this?.let {
    RequestViewer(it.id, canStart = it.bookRequests && it.canStartRequests, canChoose = it.bookRequests && it.canChooseRequestVersions)
} ?: NobodyViewer

/** A reader who has a book on their wishlist. */
data class Wanter(val id: Long, val name: String)

/** A library book a title search found. */
data class LibraryHit(
    val uuid: String,
    val title: String,
    val authorDisplay: String,
    val year: String?,
    val formats: List<String>,
    val coverUrl: String?,
    val accent: String?,
)

/** A provider's record: shown by its parsed fields, sent back as [meta] untouched. */
data class ProviderBook(
    val meta: JsonObject,
    val title: String,
    val authors: List<String>,
    val year: String?,
    val coverUrl: String?,
    val source: String,
)

/** A library book a provider record matched. [hasFiles] is false for a physical-only or wishlist-only book. */
data class MatchedBook(val uuid: String, val title: String, val hasFiles: Boolean)

/** How the library answered a provider record. */
enum class ScanOutcomeKind { AlreadyOwned, OnWishlist, InLibraryUnowned, CloseMatch, NotInLibrary, Unresolved }

/** What `scan/resolve-meta` said: the library books the record matches, in the server's order, and why. */
data class ScanMatch(val library: List<MatchedBook>, val outcome: ScanOutcomeKind)
