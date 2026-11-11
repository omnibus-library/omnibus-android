package com.omnibus.omnibus.data.requests.remote

import kotlinx.serialization.json.Json

/**
 * JSON for the request routes. Unknown keys are ignored and null properties are omitted when encoding. It does not
 * coerce, so a `state`, `kind`, `stuck_reason` or `protocol` this app doesn't know fails to decode instead of
 * reading as a default.
 */
internal val RequestsJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
