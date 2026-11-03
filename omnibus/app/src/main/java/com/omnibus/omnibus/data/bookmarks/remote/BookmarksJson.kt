package com.omnibus.omnibus.data.bookmarks.remote

import kotlinx.serialization.json.Json

/**
 * Lenient JSON for bookmark rows. Unknown keys are ignored, and an explicit null on an optional field takes its default.
 * On encode, [explicitNulls] = false drops a null field, which is how a bookmark without a title omits `title`.
 */
internal val BookmarksJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}
