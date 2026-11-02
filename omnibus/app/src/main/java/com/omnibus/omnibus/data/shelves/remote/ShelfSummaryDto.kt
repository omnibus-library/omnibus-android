package com.omnibus.omnibus.data.shelves.remote

import com.omnibus.omnibus.data.library.ShelfKind
import com.omnibus.omnibus.data.shelves.ShelfSummary
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One entry of `GET api/shelves`. */
@Serializable
internal data class ShelfSummaryDto(
    val id: Long,
    @SerialName("owner_user_id")
    val ownerUserId: Long,
    val kind: String,
    val name: String,
    @SerialName("book_count")
    val bookCount: Long = 0,
    @SerialName("cover_uuids")
    val coverUuids: List<String> = emptyList(),
) {
    /** The shelf, or null for a kind this app doesn't know, so that shelf is left out. */
    fun toDomain(): ShelfSummary? {
        val shelfKind = when (kind) {
            "smart" -> ShelfKind.Smart
            "manual" -> ShelfKind.Manual
            "wishlist" -> ShelfKind.Wishlist
            else -> return null
        }
        return ShelfSummary(
            id = id,
            ownerUserId = ownerUserId,
            kind = shelfKind,
            name = name,
            bookCount = bookCount,
            coverUuids = coverUuids,
        )
    }
}
