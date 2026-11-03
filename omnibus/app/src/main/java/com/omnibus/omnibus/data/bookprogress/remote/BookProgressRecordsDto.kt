package com.omnibus.omnibus.data.bookprogress.remote

import com.omnibus.omnibus.data.library.remote.ProgressRecordDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `GET api/progress/{uuid}`: every format's record for one book. */
@Serializable
internal data class BookProgressRecordsDto(
    @SerialName("book_uuid")
    val bookUuid: String = "",
    val records: List<ProgressRecordDto> = emptyList(),
    val furthest: String? = null,
)
