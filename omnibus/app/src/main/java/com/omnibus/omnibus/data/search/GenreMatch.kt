package com.omnibus.omnibus.data.search

/** A genre whose name matches a search, with how many books are filed under it. */
data class GenreMatch(val name: String, val bookCount: Int)
