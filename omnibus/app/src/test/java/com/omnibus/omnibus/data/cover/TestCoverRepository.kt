package com.omnibus.omnibus.data.cover

/** In-memory [CoverRepository] for tests. */
class TestCoverRepository(
    /** What [thumbnail] returns. */
    var result: CoverImageResult = CoverImageResult.NotFound,
) : CoverRepository {

    /** The `(uuid, size)` pairs [thumbnail] was asked for, in order. */
    val requests = mutableListOf<Pair<String, CoverSize>>()

    override suspend fun thumbnail(uuid: String, size: CoverSize): CoverImageResult {
        requests += uuid to size
        return result
    }

    /** The uuids [invalidate] was called for, in order. */
    val invalidated = mutableListOf<String>()

    override suspend fun invalidate(uuid: String) {
        invalidated += uuid
    }

    override suspend fun clear() = Unit
}
