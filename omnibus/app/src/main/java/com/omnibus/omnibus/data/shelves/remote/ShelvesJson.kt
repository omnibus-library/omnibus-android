package com.omnibus.omnibus.data.shelves.remote

import kotlinx.serialization.json.Json

/**
 * Lenient JSON for shelves responses. The server adds keys over time, so unknown keys are ignored,
 * and a null or wrong-typed value falls back to the property's default.
 */
internal val ShelvesJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}
