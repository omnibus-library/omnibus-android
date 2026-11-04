package com.omnibus.omnibus.data.audiobook.position.remote

import kotlinx.serialization.json.Json

/**
 * JSON for this package's request bodies and the store's stored values. Defaults and explicit nulls
 * are written, so `"format"` and `"epub_cfi": null` always appear in the POST body. Responses are
 * decoded with `LibraryJson`, not this.
 */
internal val AudioProgressJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = true
}
