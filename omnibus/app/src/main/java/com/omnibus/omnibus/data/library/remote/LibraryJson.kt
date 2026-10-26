package com.omnibus.omnibus.data.library.remote

import kotlinx.serialization.json.Json

/**
 * Lenient JSON for library responses. The server adds keys over time, so unknown keys are
 * ignored, and a null or wrong-typed value falls back to the property's default.
 */
internal val LibraryJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}
