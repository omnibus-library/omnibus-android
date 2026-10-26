package com.omnibus.omnibus.data.library.remote

import com.omnibus.omnibus.data.library.Shelf
import com.omnibus.omnibus.data.library.ShelfKind
import com.omnibus.omnibus.data.library.ShelfVisibility
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ShelfDto(
    val id: Long,
    @SerialName("owner_user_id")
    val ownerUserId: Long,
    @SerialName("owner_username")
    val ownerUsername: String = "",
    val kind: String,
    val name: String = "",
    val visibility: String = "private",
    val accent: String? = null,
    @SerialName("book_count")
    val bookCount: Long = 0,
) {
    fun toDomain(): Shelf = Shelf(
        id = id,
        ownerUserId = ownerUserId,
        ownerUsername = ownerUsername,
        kind = when (kind) {
            "smart" -> ShelfKind.Smart
            "wishlist" -> ShelfKind.Wishlist
            else -> ShelfKind.Manual
        },
        name = name,
        visibility = if (visibility == "public") ShelfVisibility.Public else ShelfVisibility.Private,
        accent = accent,
        bookCount = bookCount,
    )
}

@Serializable
internal data class ShelfPageResponseDto(
    val books: List<BookDto> = emptyList(),
)
