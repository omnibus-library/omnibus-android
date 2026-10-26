package com.omnibus.omnibus.data.cover

/** In-memory [CoverRepository] for tests. */
class FakeCoverRepository(
    /** What [thumbnail] returns. */
    var result: CoverImageResult = CoverImageResult.NotFound,
) : CoverRepository {

    /** The `(uuid, size)` pairs [thumbnail] was asked for, in order. */
    val requests = mutableListOf<Pair<String, CoverSize>>()

    override suspend fun thumbnail(uuid: String, size: CoverSize): CoverImageResult {
        requests += uuid to size
        return result
    }

    override suspend fun invalidate(uuid: String) = Unit

    override suspend fun clear() = Unit
}
