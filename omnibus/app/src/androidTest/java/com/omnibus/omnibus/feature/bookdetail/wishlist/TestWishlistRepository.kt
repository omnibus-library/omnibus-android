package com.omnibus.omnibus.feature.bookdetail.wishlist

import com.omnibus.omnibus.data.wishlist.WishlistRemovalResult
import com.omnibus.omnibus.data.wishlist.WishlistRepository
import com.omnibus.omnibus.data.wishlist.WishlistState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [WishlistRepository] for device tests, provided through [LocalWishlistRepository]. The androidTest copy of
 * the JVM fake, since the test source set is not visible here. A successful removal sets the state to
 * [WishlistState.Absent], as production does. Calls run in the caller, with no HTTP.
 *
 * Unlike production, collecting [getWishlistFlow] does not fetch. Call [seed] first. A uuid never seeded stays
 * [WishlistState.Unknown], which is the never-answering mode.
 */
internal class TestWishlistRepository : WishlistRepository {

    private val states = mutableMapOf<String, MutableStateFlow<WishlistState>>()

    private fun stateFor(uuid: String): MutableStateFlow<WishlistState> =
        states.getOrPut(uuid) { MutableStateFlow(WishlistState.Unknown) }

    /** Every uuid passed to [getWishlistFlow], in call order. */
    val flowCalls = mutableListOf<String>()

    /** Every uuid passed to [refresh], in call order. */
    val refreshCalls = mutableListOf<String>()

    /** Sets [bookUuid]'s state as if the server had answered it. */
    fun seed(bookUuid: String, state: WishlistState) {
        stateFor(bookUuid).value = state
    }

    override fun getWishlistFlow(bookUuid: String): Flow<WishlistState> {
        flowCalls += bookUuid
        return stateFor(bookUuid)
    }

    override fun cachedState(bookUuid: String): WishlistState = stateFor(bookUuid).value

    override suspend fun refresh(bookUuid: String) {
        refreshCalls += bookUuid
    }

    override suspend fun removeFromWishlist(bookUuid: String): WishlistRemovalResult {
        stateFor(bookUuid).value = WishlistState.Absent
        return WishlistRemovalResult.Removed(bookDeleted = false)
    }
}
