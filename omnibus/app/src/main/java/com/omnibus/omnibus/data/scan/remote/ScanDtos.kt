package com.omnibus.omnibus.data.scan.remote

import com.omnibus.omnibus.data.scan.ProviderRecord
import com.omnibus.omnibus.data.scan.ScanBook
import com.omnibus.omnibus.data.scan.ScanOutcome
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal val ScanJson = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

@Serializable internal data class ResolveRequestDto(val isbn: String)

@Serializable internal data class CheckInRequestDto(
    @SerialName("book_uuid") val bookUuid: String,
    val isbn: String?,
    val note: String?,
)

@Serializable internal data class BookRefDto(@SerialName("book_uuid") val bookUuid: String)

/** The body of `POST api/scan/physical-only`. */
@Serializable internal data class PhysicalOnlyRequestDto(val meta: JsonObject)

/** The body of `POST api/scan/wishlist`. [source] is the server's lower-case token. */
@Serializable internal data class ScanWishlistRequestDto(val meta: JsonObject, val source: String)

@Serializable internal data class ScanBookDto(
    val uuid: String,
    val title: String,
    val authors: List<String> = emptyList(),
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("has_physical") val hasPhysical: Boolean = false,
    @SerialName("has_files") val hasFiles: Boolean = false,
    val isbn: String? = null,
) {
    fun toDomain() = ScanBook(uuid, title, authors, coverUrl, hasPhysical, hasFiles, isbn)
}

/** A provider record as the server writes it. The record keeps every key, so a write can send it back whole. */
internal fun JsonObject.toProviderRecord(): ProviderRecord = ProviderRecord(
    raw = this,
    isbn13 = getValue("isbn13").jsonPrimitive.content,
    title = getValue("title").jsonPrimitive.content,
    authors = (this["authors"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
    year = (this["year"] as? JsonPrimitive)?.contentOrNull,
    firstPublishYear = (this["first_publish_year"] as? JsonPrimitive)?.longOrNull,
    publisher = (this["publisher"] as? JsonPrimitive)?.contentOrNull,
    pages = (this["pages"] as? JsonPrimitive)?.longOrNull,
    series = (this["series"] as? JsonPrimitive)?.contentOrNull,
    coverUrl = (this["cover_url"] as? JsonPrimitive)?.contentOrNull,
)

/** Reads a `ScanOutcome` body by its `kind`. A kind this version doesn't act on, or no kind, is [ScanOutcome.Unplaced]. */
internal fun decodeScanOutcome(body: String): ScanOutcome {
    val obj = ScanJson.parseToJsonElement(body).jsonObject
    fun book() = ScanJson.decodeFromJsonElement(ScanBookDto.serializer(), obj.getValue("book")).toDomain()
    return when ((obj["kind"] as? JsonPrimitive)?.contentOrNull) {
        "already_owned" -> ScanOutcome.AlreadyOwned(book())
        "on_wishlist" -> ScanOutcome.OnWishlist(book())
        "in_library_unowned" -> ScanOutcome.InLibraryUnowned(book())
        "not_in_library" -> ScanOutcome.NotInLibrary(obj.getValue("online").jsonObject.toProviderRecord())
        else -> ScanOutcome.Unplaced
    }
}
