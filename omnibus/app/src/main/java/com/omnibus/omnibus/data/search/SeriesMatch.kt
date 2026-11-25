package com.omnibus.omnibus.data.search

/** A series whose name matches a search: how many books it has and, when known, who wrote it. */
data class SeriesMatch(val id: Long, val name: String, val bookCount: Int, val authorDisplay: String?)
