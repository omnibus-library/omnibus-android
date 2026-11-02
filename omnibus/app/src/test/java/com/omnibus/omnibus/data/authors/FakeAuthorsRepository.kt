package com.omnibus.omnibus.data.authors

/** An [AuthorsRepository] that answers from [results], and records the author ids asked for. */
class FakeAuthorsRepository(
    var result: AuthorResult = AuthorResult.NoServer,
) : AuthorsRepository {

    /** The author ids passed to [getAuthor], in order. */
    val requested = mutableListOf<Long>()

    override suspend fun getAuthor(authorId: Long): AuthorResult {
        requested += authorId
        return result
    }
}
