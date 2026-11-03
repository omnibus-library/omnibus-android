package com.omnibus.omnibus.data.deletion.remote

import kotlinx.serialization.json.Json

/**
 * Lenient JSON for the deletion calls. Unknown keys are ignored, a wrong-typed value falls back to the property's
 * default, and null properties are omitted when encoding.
 */
internal val DeletionJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}
