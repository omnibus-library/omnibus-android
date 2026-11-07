package com.omnibus.omnibus.data.household

import android.graphics.Bitmap
import kotlinx.coroutines.awaitCancellation

/** In-memory [HouseholdRepository] for consumers' tests. */
class TestHouseholdRepository : HouseholdRepository {

    /** The next outcomes of [getHousehold], in order. When empty, a read never answers. */
    val results = ArrayDeque<HouseholdResult>()

    /** The uuid of every [getHousehold] call, in call order. */
    val calls = mutableListOf<String>()

    /** The avatar [getAvatar] answers for each user id. Missing ids have none. */
    val avatars = mutableMapOf<Long, Bitmap>()

    override suspend fun getHousehold(bookUuid: String): HouseholdResult {
        calls += bookUuid
        return results.removeFirstOrNull() ?: awaitCancellation()
    }

    override suspend fun getAvatar(userId: Long, maxEdgePx: Int): Bitmap? = avatars[userId]
}
