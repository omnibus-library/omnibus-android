package com.omnibus.omnibus.feature.bookdetail.wishlist

import com.omnibus.omnibus.data.wishlist.WishlistRemovalResult
import com.omnibus.omnibus.data.wishlist.WishlistRepository
import com.omnibus.omnibus.data.wishlist.WishlistState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [WishlistRepository] for device tests, provided through [LocalWishlistRepository]. The androidTest copy of
 * the JVM fake, since the test source set is not visible here. A successful removal sets the state to
 * [WishlistState.Absent], as production does. Calls run in the caller, with no HTTP.
 *
 * Unlike production, collecting [getWishlistFlow] does not fetch. Call [seed] or [refresh] first. A uuid never seeded
 * stays [WishlistState.Unknown], which is the never-answering mode.
 */
internal class FakeWishlistRepository : WishlistRepository {

    private val states = mutableMapOf<String, MutableStateFlow<WishlistState>>()

    private fun stateFor(uuid: String): MutableStateFlow<WishlistState> =
        states.getOrPut(uuid) { MutableStateFlow(WishlistState.Unknown) }

    /** Every uuid passed to [getWishlistFlow], in call order. */
    val flowCalls = mutableListOf<String>()

    /** Every uuid passed to [refresh], in call order. */
    val refreshCalls = mutableListOf<String>()

    /** Every uuid passed to [removeFromWishlist], in call order. */
    val removeCalls = mutableListOf<String>()

    /** The next refresh's answer. A refresh applies the head when present, and otherwise leaves the state alone. */
    val refreshResults = ArrayDeque<WishlistState>()

    /** The next removal's result. When empty, a removal succeeds with `bookDeleted = false`. */
    val removalResults = ArrayDeque<WishlistRemovalResult>()

    /** When non-empty, the next removal waits on the head before taking its result. */
    val removalGates = ArrayDeque<CompletableDeferred<Unit>>()

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
        refreshResults.removeFirstOrNull()?.let { stateFor(bookUuid).value = it }
    }

    override suspend fun removeFromWishlist(bookUuid: String): WishlistRemovalResult {
        removeCalls += bookUuid
        removalGates.removeFirstOrNull()?.await()
        val result = removalResults.removeFirstOrNull() ?: WishlistRemovalResult.Removed(bookDeleted = false)
        if (result is WishlistRemovalResult.Removed) stateFor(bookUuid).value = WishlistState.Absent
        return result
    }
}
