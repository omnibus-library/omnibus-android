package com.omnibus.omnibus.data.export.remote

import kotlinx.serialization.json.Json

/**
 * Lenient JSON for export responses. Unknown keys are ignored, and a null or wrong-typed value takes the
 * property's default.
 */
internal val ExportJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}
