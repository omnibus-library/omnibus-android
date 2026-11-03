package com.omnibus.omnibus.data.wishlist

import android.util.Log
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.wishlist.remote.WishlistEntryDto
import com.omnibus.omnibus.data.wishlist.remote.WishlistJson
import com.omnibus.omnibus.data.wishlist.remote.WishlistRemovalDto
import com.omnibus.omnibus.di.ApplicationScope
import com.omnibus.omnibus.di.IoDispatcher
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Request

/**
 * Default [WishlistRepository]. Keeps one hot state per book uuid. Requests run in the application scope, so leaving
 * the page cannot cancel a removal. Every failure becomes a result, so no exception leaves this class except
 * cancellation.
 */
@Singleton
class DefaultWishlistRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val omnibusHttpClient: OmnibusHttpClient,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : WishlistRepository {

    /** One book's state. [state], [removalSeq] and [inFlightFetch] are guarded by [lock]. */
    private class Entry {
        val lock = Any()
        val state = MutableStateFlow<WishlistState>(WishlistState.Unknown)

        /** Bumped by every successful removal, so a GET that started before it cannot bring the entry back. */
        var removalSeq = 0L

        var inFlightFetch: Deferred<WishlistState>? = null
    }

    private val entries = ConcurrentHashMap<String, Entry>()

    private fun entry(uuid: String): Entry = entries.computeIfAbsent(uuid) { Entry() }

    override fun getWishlistFlow(bookUuid: String): Flow<WishlistState> {
        val entry = entry(bookUuid)
        return entry.state.onStart {
            val value = entry.state.value
            if (value == WishlistState.Unknown || value == WishlistState.Failed) startFetch(bookUuid, entry)
        }
    }

    override fun cachedState(bookUuid: String): WishlistState =
        entries[bookUuid]?.state?.value ?: WishlistState.Unknown

    override suspend fun refresh(bookUuid: String) {
        startFetch(bookUuid, entry(bookUuid)).await()
    }

    /** Starts a GET, or returns the one already in flight. It runs in the application scope, not the caller's. */
    private fun startFetch(uuid: String, entry: Entry): Deferred<WishlistState> {
        val deferred = synchronized(entry.lock) {
            entry.inFlightFetch?.takeIf { !it.isCompleted }?.let { return it }
            applicationScope.async(start = CoroutineStart.LAZY) {
                val seqAtStart = synchronized(entry.lock) { entry.removalSeq }
                val outcome = get(uuid)
                synchronized(entry.lock) {
                    // A failed read keeps a known answer, and an answer from before a removal is stale.
                    val keepsKnownAnswer = outcome == WishlistState.Failed &&
                        entry.state.value.let { it is WishlistState.Present || it == WishlistState.Absent }
                    if (entry.removalSeq == seqAtStart && !keepsKnownAnswer) entry.state.value = outcome
                }
                outcome
            }.also { entry.inFlightFetch = it }
        }
        deferred.invokeOnCompletion {
            synchronized(entry.lock) {
                if (entry.inFlightFetch === deferred) entry.inFlightFetch = null
            }
        }
        deferred.start()
        return deferred
    }

    override suspend fun removeFromWishlist(bookUuid: String): WishlistRemovalResult {
        if (bookUuid.isBlank()) return WishlistRemovalResult.Failed(WishlistError.BookNotFound)
        val entry = entry(bookUuid)
        return applicationScope.async {
            delete(bookUuid).also { result ->
                if (result is WishlistRemovalResult.Removed) {
                    synchronized(entry.lock) {
                        entry.removalSeq++
                        entry.state.value = WishlistState.Absent
                    }
                }
            }
        }.await()
    }

    /** One GET of the book's entry. A `null` body means the book is not on the wishlist. */
    private suspend fun get(uuid: String): WishlistState = withContext(ioDispatcher) {
        if (uuid.isBlank()) return@withContext WishlistState.Failed
        val builder = omnibusHttpClient.request(WISHLIST_PATH) ?: return@withContext WishlistState.Failed
        val request = builder.url(wishlistUrl(builder, uuid)).get().build()

        try {
            omnibusHttpClient.execute(request).use { response ->
                if (!response.isSuccessful) {
                    WishlistState.Failed
                } else {
                    val dto = WishlistJson.decodeFromString<WishlistEntryDto?>(response.body.string())
                    dto?.let { WishlistState.Present(it.toDomain()) } ?: WishlistState.Absent
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server to read a wishlist entry", ex)
            WishlistState.Failed
        } catch (ex: IllegalArgumentException) {
            // SerializationException is a subtype, so a body that doesn't decode lands here too.
            Log.w(TAG, "Unexpected response to a wishlist read", ex)
            WishlistState.Failed
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure reading a wishlist entry", ex)
            WishlistState.Failed
        }
    }

    /** One DELETE of the book's entry. A 2xx answer says whether the server also deleted the book. */
    private suspend fun delete(uuid: String): WishlistRemovalResult = withContext(ioDispatcher) {
        if (uuid.isBlank()) return@withContext WishlistRemovalResult.Failed(WishlistError.BookNotFound)
        val builder = omnibusHttpClient.request(WISHLIST_PATH)
            ?: return@withContext WishlistRemovalResult.Failed(WishlistError.NoServer)
        val request: Request = builder.url(wishlistUrl(builder, uuid)).delete().build()

        try {
            omnibusHttpClient.execute(request).use { response ->
                when {
                    response.isSuccessful -> {
                        val dto = WishlistJson.decodeFromString<WishlistRemovalDto>(response.body.string())
                        WishlistRemovalResult.Removed(dto.bookDeleted)
                    }
                    response.code == HTTP_UNAUTHORIZED -> WishlistRemovalResult.Failed(WishlistError.SessionExpired)
                    response.code == HTTP_NOT_FOUND -> WishlistRemovalResult.Failed(WishlistError.BookNotFound)
                    else -> WishlistRemovalResult.Failed(WishlistError.Http(response.code))
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server to remove a wishlist entry", ex)
            WishlistRemovalResult.Failed(WishlistError.Unreachable)
        } catch (ex: IllegalArgumentException) {
            Log.w(TAG, "Unexpected response to a wishlist removal", ex)
            WishlistRemovalResult.Failed(WishlistError.UnexpectedResponse)
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure removing a wishlist entry", ex)
            WishlistRemovalResult.Failed(WishlistError.UnexpectedResponse)
        }
    }

    /** The `api/physical/{uuid}/wishlist` URL. OkHttp encodes the uuid as one path segment. */
    private fun wishlistUrl(builder: Request.Builder, uuid: String): HttpUrl =
        builder.build().url.newBuilder()
            .addPathSegment(uuid)
            .addPathSegment("wishlist")
            .build()

    internal companion object {
        const val TAG = "DefaultWishlistRepository"

        /** Relative, so it resolves beneath the server's base URL. */
        const val WISHLIST_PATH = "api/physical"

        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_NOT_FOUND = 404
    }
}
