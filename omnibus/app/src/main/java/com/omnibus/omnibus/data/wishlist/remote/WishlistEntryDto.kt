package com.omnibus.omnibus.data.wishlist.remote

import com.omnibus.omnibus.data.wishlist.WishlistEntry
import com.omnibus.omnibus.data.wishlist.WishlistSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A `WishlistEntry` from `GET api/physical/{uuid}/wishlist`. The server's `added_at` is Unix seconds. A `source` token
 * that is unknown or missing reads as no source.
 */
@Serializable
internal data class WishlistEntryDto(
    @SerialName("id") val id: Long = 0,
    @SerialName("user_id") val userId: Long = 0,
    @SerialName("book_uuid") val bookUuid: String? = null,
    @SerialName("added_at") val addedAt: Long? = null,
    @SerialName("source") val source: String? = null,
) {
    /** This entry as a domain entry, with the time in milliseconds. */
    fun toDomain(): WishlistEntry = WishlistEntry(
        id = id,
        addedAtMillis = addedAt?.times(1_000L),
        source = source?.trim()?.let { token ->
            WishlistSource.entries.firstOrNull { it.name.equals(token, ignoreCase = true) }
        },
    )
}
