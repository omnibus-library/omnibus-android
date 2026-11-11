package com.omnibus.omnibus.data.requests.remote

import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.remote.BookDto
import com.omnibus.omnibus.data.library.remote.LibraryJson
import com.omnibus.omnibus.data.requests.BookRequest
import com.omnibus.omnibus.data.requests.CoverOffer
import com.omnibus.omnibus.data.requests.LibraryHit
import com.omnibus.omnibus.data.requests.MatchedBook
import com.omnibus.omnibus.data.requests.ProviderBook
import com.omnibus.omnibus.data.requests.ReleaseProtocol
import com.omnibus.omnibus.data.requests.RequestKind
import com.omnibus.omnibus.data.requests.RequestListing
import com.omnibus.omnibus.data.requests.RequestState
import com.omnibus.omnibus.data.requests.RequestVersion
import com.omnibus.omnibus.data.requests.ScanMatch
import com.omnibus.omnibus.data.requests.ScanOutcomeKind
import com.omnibus.omnibus.data.requests.StuckReason
import com.omnibus.omnibus.data.requests.Wanter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

/** [body] as a JSON request body. */
internal fun jsonBody(body: String): RequestBody = body.toRequestBody(JSON_MEDIA_TYPE)

@Serializable
internal data class RequestVersionDto(
    val id: Long,
    val title: String,
    @SerialName("size_bytes") val sizeBytes: Long? = null,
    val format: String? = null,
    val indexer: String? = null,
    val seeders: Long? = null,
    val protocol: ReleaseProtocol? = null,
    @SerialName("info_url") val infoUrl: String? = null,
    @SerialName("best_match") val bestMatch: Boolean = false,
    val rejected: Boolean = false,
) {
    fun toDomain() = RequestVersion(id, title, sizeBytes, format, indexer, seeders, protocol, infoUrl, bestMatch, rejected)
}

@Serializable
internal data class CoverOfferDto(
    val width: Int,
    val height: Int,
    @SerialName("current_width") val currentWidth: Int,
    @SerialName("current_height") val currentHeight: Int,
) {
    fun toDomain() = CoverOffer(width, height, currentWidth, currentHeight)
}

@Serializable
internal data class BookRequestDto(
    val id: Long,
    @SerialName("book_uuid") val bookUuid: String,
    @SerialName("user_id") val userId: Long,
    val kind: RequestKind,
    val state: RequestState,
    @SerialName("stuck_reason") val stuckReason: StuckReason? = null,
    @SerialName("stuck_detail") val stuckDetail: String? = null,
    @SerialName("chosen_result_id") val chosenResultId: Long? = null,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
    val progress: Double? = null,
    @SerialName("expected_done_at") val expectedDoneAt: Long? = null,
    @SerialName("cancel_at") val cancelAt: Long? = null,
    @SerialName("cancelled_by") val cancelledBy: Long? = null,
    val note: String? = null,
    @SerialName("cover_offer") val coverOffer: CoverOfferDto? = null,
    val versions: List<RequestVersionDto> = emptyList(),
) {
    fun toDomain() = BookRequest(
        id = id,
        bookUuid = bookUuid,
        userId = userId,
        kind = kind,
        state = state,
        stuckReason = stuckReason,
        stuckDetail = stuckDetail,
        chosenResultId = chosenResultId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        progress = progress,
        expectedDoneAt = expectedDoneAt,
        cancelAt = cancelAt,
        cancelledBy = cancelledBy,
        note = note,
        coverOffer = coverOffer?.toDomain(),
        versions = versions.map { it.toDomain() },
    )
}

@Serializable
internal data class RequestListingDto(
    val request: BookRequestDto,
    val book: JsonObject,
    @SerialName("requester_name") val requesterName: String = "",
    @SerialName("requester_has_avatar") val requesterHasAvatar: Boolean = false,
    @SerialName("cancelled_by_name") val cancelledByName: String? = null,
) {
    fun toDomain() = RequestListing(request.toDomain(), book.toBook(), requesterName, requesterHasAvatar, cancelledByName)
}

/** The body of `POST api/books/{uuid}/requests`. */
@Serializable
internal data class StartRequestDto(val kind: RequestKind)

/** The body of `POST api/requests`. [meta] is the provider's record, verbatim. */
@Serializable
internal data class StartRequestFromMetaDto(val kind: RequestKind, val meta: JsonObject)

/** The body of `POST api/requests/{id}/choose`. The server denies unknown fields, so it carries nothing else. */
@Serializable
internal data class ChooseVersionDto(@SerialName("result_id") val resultId: Long)

/** The part of a `GET api/wishlists` row the request page reads. */
@Serializable
internal data class WishlistBookDto(
    val book: JsonObject,
    @SerialName("wanted_by") val wantedBy: List<WanterDto> = emptyList(),
)

/** The nested book, read as the library reads a book, so a null where it would default doesn't fail the whole list. */
internal fun JsonObject.toBook(): Book = LibraryJson.decodeFromJsonElement(BookDto.serializer(), this).toDomain()

@Serializable
internal data class WanterDto(val id: Long, val name: String) {
    fun toDomain() = Wanter(id, name)
}

/** The part of `GET api/search/palette` the sheet reads. */
@Serializable
internal data class PaletteResultsDto(val books: List<PaletteBookDto> = emptyList())

@Serializable
internal data class PaletteBookDto(
    val uuid: String = "",
    val title: String,
    @SerialName("author_display") val authorDisplay: String = "",
    val year: String? = null,
    val formats: List<String> = emptyList(),
    @SerialName("cover_url") val coverUrl: String? = null,
    val accent: String? = null,
) {
    fun toDomain() = LibraryHit(uuid, title, authorDisplay, year, formats, coverUrl, accent)
}

/** The body of `POST api/scan/search`. */
@Serializable
internal data class ScanSearchRequestDto(val query: String)

/** The answer to `POST api/scan/search`. Each record stays a [JsonObject] so it can be sent back untouched. */
@Serializable
internal data class ScanSearchResponseDto(val results: List<JsonObject> = emptyList())

/** The body of `POST api/scan/resolve-meta`. */
@Serializable
internal data class ResolveMetaRequestDto(val meta: JsonObject)

/** A provider record as the sheet shows it. A record with no title is not usable, so it fails the whole read. */
internal fun JsonObject.toProviderBook(): ProviderBook = ProviderBook(
    meta = this,
    title = getValue("title").jsonPrimitive.content,
    authors = (this["authors"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
    year = (this["year"] as? JsonPrimitive)?.contentOrNull,
    coverUrl = (this["cover_url"] as? JsonPrimitive)?.contentOrNull,
    source = (this["source"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
)

private fun JsonObject.toMatchedBook() = MatchedBook(
    uuid = getValue("uuid").jsonPrimitive.content,
    title = getValue("title").jsonPrimitive.content,
    hasFiles = (this["has_files"] as? JsonPrimitive)?.booleanOrNull ?: false,
)

/**
 * Reads a `scan/resolve-meta` outcome, tagged by `kind`. The books it lists are the ones iOS puts under "In your
 * library": the head and the others of a close match, or the one book of an ownership match. A kind this app does not
 * know reads as unresolved.
 */
internal fun parseScanMatch(body: String): ScanMatch {
    val obj = RequestsJson.parseToJsonElement(body).jsonObject
    fun book(element: JsonElement) = element.jsonObject.toMatchedBook()
    return when (obj["kind"]?.jsonPrimitive?.content) {
        "already_owned" -> ScanMatch(listOf(book(obj.getValue("book"))), ScanOutcomeKind.AlreadyOwned)
        "on_wishlist" -> ScanMatch(listOf(book(obj.getValue("book"))), ScanOutcomeKind.OnWishlist)
        "in_library_unowned" -> ScanMatch(listOf(book(obj.getValue("book"))), ScanOutcomeKind.InLibraryUnowned)
        "close_match" -> ScanMatch(
            listOf(book(obj.getValue("book"))) + (obj["others"] as? JsonArray).orEmpty().map(::book),
            ScanOutcomeKind.CloseMatch,
        )
        "not_in_library" -> ScanMatch(emptyList(), ScanOutcomeKind.NotInLibrary)
        else -> ScanMatch(emptyList(), ScanOutcomeKind.Unresolved)
    }
}
