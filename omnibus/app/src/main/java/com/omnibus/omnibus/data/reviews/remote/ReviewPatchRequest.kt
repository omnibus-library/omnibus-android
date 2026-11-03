package com.omnibus.omnibus.data.reviews.remote

import com.omnibus.omnibus.data.reviews.Patch
import com.omnibus.omnibus.data.reviews.ReviewPatch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The body of `PATCH api/reviews/{uuid}`: only the set fields, with snake_case names. A set of null is written as JSON
 * null, which is how the server clears a field.
 */
internal fun ReviewPatch.toJsonObject(): JsonObject = buildJsonObject {
    // put(key, null) writes JSON null. Never route this through ReviewsJson: its explicitNulls = false would drop the null
    // and turn a clear into a no-op.
    rating.let { if (it is Patch.Set) put("stars", it.value) }
    text.let { if (it is Patch.Set) put("body_md", it.value) }
    privateNotes.let { if (it is Patch.Set) put("notes_md", it.value) }
    isSpoiler.let { if (it is Patch.Set) put("spoiler", it.value) }
}

/** The PATCH body as JSON, with the media type the server expects. */
internal fun ReviewPatch.toJsonRequest(): RequestBody =
    toJsonObject().toString().toRequestBody(JSON_MEDIA_TYPE)

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
