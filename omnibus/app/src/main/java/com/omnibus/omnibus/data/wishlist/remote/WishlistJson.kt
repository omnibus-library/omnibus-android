package com.omnibus.omnibus.data.wishlist.remote

import kotlinx.serialization.json.Json

/**
 * Lenient JSON for the wishlist. Unknown keys are ignored, a wrong-typed value falls back to the property's
 * default, and null properties are omitted when encoding.
 */
internal val WishlistJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}
