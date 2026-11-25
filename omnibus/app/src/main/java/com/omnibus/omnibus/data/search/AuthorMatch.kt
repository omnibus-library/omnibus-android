package com.omnibus.omnibus.data.search

/** An author whose name matches a search: how many books they have and, when known, one of their titles. */
data class AuthorMatch(val id: Long, val name: String, val bookCount: Int, val leadBookTitle: String?)
