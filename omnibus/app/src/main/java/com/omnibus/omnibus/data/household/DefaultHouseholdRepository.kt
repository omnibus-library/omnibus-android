package com.omnibus.omnibus.data.household

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.omnibus.omnibus.data.household.remote.BookHouseholdReaderDto
import com.omnibus.omnibus.data.household.remote.HouseholdJson
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.di.IoDispatcher
import java.io.IOException
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer

/** Default [HouseholdRepository]. Every failure becomes a result, so nothing leaves it except cancellation. */
@Singleton
class DefaultHouseholdRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val omnibusHttpClient: OmnibusHttpClient,
) : HouseholdRepository {

    /** Decoded avatars by user id and requested edge, bounded by bytes. */
    private val avatars = LinkedHashMap<String, Bitmap>(16, 0.75f, true)
    private var avatarBytes = 0

    override suspend fun getHousehold(bookUuid: String): HouseholdResult = withContext(ioDispatcher) {
        if (bookUuid.isBlank()) return@withContext HouseholdResult.NotFound
        val builder = omnibusHttpClient.request(BOOKS_PATH) ?: return@withContext HouseholdResult.NotFound
        val url = builder.build().url.newBuilder()
            .addPathSegment(bookUuid)
            .addPathSegment(HOUSEHOLD_SEGMENT)
            .build()
        val request = builder.url(url).get().build()

        try {
            omnibusHttpClient.execute(request).use { response ->
                when {
                    response.isSuccessful -> {
                        val rows = HouseholdJson.decodeFromString(
                            ListSerializer(BookHouseholdReaderDto.serializer()),
                            response.body.string(),
                        )
                        HouseholdResult.Success(rows.mapNotNull { it.toDomain() })
                    }
                    response.code == HTTP_NOT_FOUND -> HouseholdResult.NotFound
                    else -> HouseholdResult.Failed
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server to read the household", ex)
            HouseholdResult.Failed
        } catch (ex: IllegalArgumentException) {
            // SerializationException is a subtype, so a body that doesn't decode lands here too.
            Log.w(TAG, "Unexpected response to a household read", ex)
            HouseholdResult.Failed
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure reading the household", ex)
            HouseholdResult.Failed
        }
    }

    override suspend fun getAvatar(userId: Long, maxEdgePx: Int): Bitmap? = withContext(ioDispatcher) {
        val edge = maxEdgePx.coerceAtLeast(1)
        val key = "$userId@$edge"
        cachedAvatar(key)?.let { return@withContext it }
        val builder = omnibusHttpClient.request(USERS_PATH) ?: return@withContext null
        val url = builder.build().url.newBuilder()
            .addPathSegment(userId.toString())
            .addPathSegment(AVATAR_SEGMENT)
            .build()
        val request = builder.url(url).get().build()

        try {
            omnibusHttpClient.execute(request).use { response ->
                if (!response.isSuccessful) return@use null
                decode(response.body.bytes(), edge)?.also { cacheAvatar(key, it) }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server to load an avatar", ex)
            null
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure loading an avatar", ex)
            null
        }
    }

    private fun cachedAvatar(key: String): Bitmap? = synchronized(avatars) { avatars[key] }

    /** Keeps [bitmap], dropping the least recently used avatars until the cache is back under its byte limit. */
    private fun cacheAvatar(key: String, bitmap: Bitmap) = synchronized(avatars) {
        avatars.put(key, bitmap)?.let { avatarBytes -= it.byteCount }
        avatarBytes += bitmap.byteCount
        val oldest = avatars.entries.iterator()
        while (avatarBytes > AVATAR_CACHE_BYTES && avatars.size > 1 && oldest.hasNext()) {
            val entry = oldest.next()
            if (entry.value === bitmap) continue
            avatarBytes -= entry.value.byteCount
            oldest.remove()
        }
    }

    /** Decodes [bytes], sampled down so the long edge stays at least [edge] pixels, or null when they are not an image. */
    private fun decode(bytes: ByteArray, edge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        if (longEdge <= 0) return null
        var sample = 1
        while (longEdge / (sample * 2) >= edge) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    internal companion object {
        const val TAG = "DefaultHouseholdRepository"

        /** Relative, so they resolve beneath the server's base URL. */
        const val BOOKS_PATH = "api/books"
        const val USERS_PATH = "api/users"
        const val HOUSEHOLD_SEGMENT = "household"
        const val AVATAR_SEGMENT = "avatar"

        const val HTTP_NOT_FOUND = 404
        const val AVATAR_CACHE_BYTES = 4 * 1024 * 1024
    }
}
