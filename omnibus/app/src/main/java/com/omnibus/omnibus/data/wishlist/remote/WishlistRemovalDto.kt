package com.omnibus.omnibus.data.wishlist.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The body of a 2xx `DELETE api/physical/{uuid}/wishlist`. A missing `book_deleted` reads as false. */
@Serializable
internal data class WishlistRemovalDto(
    @SerialName("book_deleted") val bookDeleted: Boolean = false,
)
