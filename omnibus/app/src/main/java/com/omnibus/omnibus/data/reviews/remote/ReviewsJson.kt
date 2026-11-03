package com.omnibus.omnibus.data.reviews.remote

import kotlinx.serialization.json.Json

/**
 * Lenient JSON for reviews. Unknown keys are ignored, a wrong-typed value falls back to the property's default, and
 * null properties are omitted when encoding.
 */
internal val ReviewsJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}
