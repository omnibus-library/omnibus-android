package com.omnibus.omnibus.data.readstatus.remote

import kotlinx.serialization.json.Json

/**
 * Lenient JSON for read status. Unknown keys are ignored, a wrong-typed value falls back to the property's
 * default, and null properties are omitted when encoding.
 */
internal val ReadStatusJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}
