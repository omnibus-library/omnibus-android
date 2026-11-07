package com.omnibus.omnibus.data.wishlist

import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomEnum
import com.omnibus.omnibus.testing.randomLong

/** The server sends whole seconds, so the time is whole seconds in milliseconds. */
fun randomWishlistEntry(): WishlistEntry = WishlistEntry(
    id = randomLong(),
    addedAtMillis = if (randomBoolean()) randomLong(0L..2_000_000_000L) * 1_000L else null,
    source = if (randomBoolean()) randomEnum<WishlistSource>() else null,
)
