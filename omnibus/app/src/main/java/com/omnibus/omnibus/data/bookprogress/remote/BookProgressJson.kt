package com.omnibus.omnibus.data.bookprogress.remote

import kotlinx.serialization.json.Json

/**
 * Lenient JSON for book progress. Unknown keys are ignored, a wrong-typed value falls back to the property's
 * default, and null properties are omitted when encoding.
 */
internal val BookProgressJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}
