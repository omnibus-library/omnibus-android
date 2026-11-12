package com.omnibus.omnibus.data.authors

/** An [AuthorsRepository] that answers from [results], and records the author ids asked for. */
class TestAuthorsRepository(
    var result: AuthorResult = AuthorResult.NoServer,
) : AuthorsRepository {

    /** The author ids passed to [getAuthor], in order. */
    val requested = mutableListOf<Long>()

    override suspend fun getAuthor(authorId: Long): AuthorResult {
        requested += authorId
        return result
    }

    /** The answer to [getAuthors]. */
    var listResult: AuthorListResult = AuthorListResult.NoServer

    /** How many times [getAuthors] was called. */
    var listCalls = 0

    override suspend fun getAuthors(): AuthorListResult {
        listCalls++
        return listResult
    }
}
