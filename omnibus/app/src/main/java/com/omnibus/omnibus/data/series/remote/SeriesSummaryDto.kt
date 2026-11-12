package com.omnibus.omnibus.data.series.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One entry of `GET api/series`: the series' name and how many books of it the library holds. */
@Serializable
internal data class SeriesSummaryDto(
    val name: String = "",
    @SerialName("book_count")
    val bookCount: Long = 0,
)
