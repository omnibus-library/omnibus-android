package com.omnibus.omnibus.data.scan.remote

import com.omnibus.omnibus.data.scan.ScanBook
import com.omnibus.omnibus.data.scan.ScanOutcome
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

internal val ScanJson = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

@Serializable internal data class ResolveRequestDto(val isbn: String)

@Serializable internal data class CheckInRequestDto(
    @SerialName("book_uuid") val bookUuid: String,
    val isbn: String?,
    val note: String?,
)

@Serializable internal data class BookRefDto(@SerialName("book_uuid") val bookUuid: String)

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

/** Reads a `ScanOutcome` body by its `kind`. A kind this version doesn't act on, or no kind, is [ScanOutcome.Unplaced]. */
internal fun decodeScanOutcome(body: String): ScanOutcome {
    val obj = ScanJson.parseToJsonElement(body).jsonObject
    fun book() = ScanJson.decodeFromJsonElement(ScanBookDto.serializer(), obj.getValue("book")).toDomain()
    return when ((obj["kind"] as? JsonPrimitive)?.contentOrNull) {
        "already_owned" -> ScanOutcome.AlreadyOwned(book())
        "on_wishlist" -> ScanOutcome.OnWishlist(book())
        "in_library_unowned" -> ScanOutcome.InLibraryUnowned(book())
        else -> ScanOutcome.Unplaced
    }
}
