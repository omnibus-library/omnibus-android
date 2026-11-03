package com.omnibus.omnibus.data.merge.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Lenient JSON for merge responses. The server adds keys over time, so unknown keys are ignored, and a null or
 * wrong-typed value falls back to the property's default.
 */
internal val MergeJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

/** The body of `POST api/books/merge`. */
@Serializable
internal data class MergeBooksRequestDto(
    @SerialName("source_uuid")
    val sourceUuid: String,
    @SerialName("target_uuid")
    val targetUuid: String,
) {
    fun toJsonRequest(): RequestBody = MergeJson.encodeToString(this).toRequestBody(JSON_MEDIA_TYPE)
}

/** The response of `POST api/books/merge`. */
@Serializable
internal data class MergeBooksResponseDto(
    @SerialName("merge_log_id")
    val mergeLogId: Long,
    @SerialName("target_uuid")
    val targetUuid: String,
)

/** The body of `POST api/books/merge/undo`. */
@Serializable
internal data class UndoMergeRequestDto(
    @SerialName("merge_log_id")
    val mergeLogId: Long,
) {
    fun toJsonRequest(): RequestBody = MergeJson.encodeToString(this).toRequestBody(JSON_MEDIA_TYPE)
}

/** The response of `POST api/books/merge/undo`. */
@Serializable
internal data class UndoMergeResponseDto(
    @SerialName("restored_uuid")
    val restoredUuid: String,
)

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
