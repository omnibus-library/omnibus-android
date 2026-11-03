package com.omnibus.omnibus.data.deletion.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The body of `POST api/books/{uuid}/delete-files`. It has no defaults on purpose: kotlinx skips a property that equals
 * its default, so a default would drop an empty list from the JSON. Both keys are always sent.
 */
@Serializable
internal data class DeleteFilesRequestDto(
    @SerialName("file_ids") val fileIds: List<Long>,
    @SerialName("copy_ids") val copyIds: List<Long>,
)
