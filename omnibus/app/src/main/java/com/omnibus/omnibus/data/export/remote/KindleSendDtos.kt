package com.omnibus.omnibus.data.export.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The body of `POST api/kindle/send`. */
@Serializable
internal data class KindleSendRequestDto(
    @SerialName("book_uuid")
    val bookUuid: String,
)

/** The answer to `POST api/kindle/send`: the task to poll. */
@Serializable
internal data class KindleSendQueuedDto(
    @SerialName("task_id")
    val taskId: Long,
)

/** The answer to `GET api/kindle/send/status`: `pending`, `sent` or `failed`, with the server's message on failure. */
@Serializable
internal data class KindleSendStatusDto(
    val status: String = "",
    val message: String? = null,
)
