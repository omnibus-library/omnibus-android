package com.omnibus.omnibus.data.highlights.remote

import kotlinx.serialization.json.Json

/**
 * Lenient JSON for the highlights list. Unknown keys are ignored, and an explicit null on an optional field takes its
 * default. A wrong-typed value fails the whole decode.
 */
internal val HighlightsJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}
