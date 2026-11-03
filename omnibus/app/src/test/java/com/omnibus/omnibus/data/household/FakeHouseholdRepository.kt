package com.omnibus.omnibus.data.household

import android.graphics.Bitmap

/** In-memory [HouseholdRepository] for consumers' tests. */
class FakeHouseholdRepository : HouseholdRepository {

    /** The next outcomes of [getHousehold], in order. When empty, a read answers an empty [HouseholdResult.Success]. */
    val results = ArrayDeque<HouseholdResult>()

    /** The uuid of every [getHousehold] call, in call order. */
    val calls = mutableListOf<String>()

    /** The avatar [getAvatar] answers for each user id. Missing ids have none. */
    val avatars = mutableMapOf<Long, Bitmap>()

    override suspend fun getHousehold(bookUuid: String): HouseholdResult {
        calls += bookUuid
        return results.removeFirstOrNull() ?: HouseholdResult.Success(emptyList())
    }

    override suspend fun getAvatar(userId: Long, maxEdgePx: Int): Bitmap? = avatars[userId]
}
