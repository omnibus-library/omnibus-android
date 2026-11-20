package com.omnibus.omnibus.data.search

import android.util.Log
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.search.remote.PaletteJson
import com.omnibus.omnibus.data.search.remote.PaletteResultsDto
import com.omnibus.omnibus.di.IoDispatcher
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString

/**
 * Default [LibrarySearchRepository]. Every failure becomes a [LibrarySearchResult], so no exception leaves this class,
 * except cancellation, which always propagates.
 */
class DefaultLibrarySearchRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val omnibusHttpClient: OmnibusHttpClient,
) : LibrarySearchRepository {

    override suspend fun searchBooks(query: String): LibrarySearchResult = withContext(ioDispatcher) {
        val builder = omnibusHttpClient.request(PALETTE_PATH) ?: return@withContext LibrarySearchResult.NoServer
        val url = builder.build().url.newBuilder().addQueryParameter("q", query).build()
        val request = builder.url(url).build()
        try {
            omnibusHttpClient.execute(request).use { response ->
                when {
                    response.isSuccessful -> LibrarySearchResult.Success(
                        PaletteJson.decodeFromString<PaletteResultsDto>(response.body.string()).toDomain(),
                    )
                    response.code == HTTP_UNAUTHORIZED -> LibrarySearchResult.Failure(LibrarySearchError.SessionExpired)
                    else -> LibrarySearchResult.Failure(LibrarySearchError.Http(response.code))
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server for GET $PALETTE_PATH", ex)
            LibrarySearchResult.Failure(LibrarySearchError.Unreachable)
        } catch (ex: IllegalArgumentException) {
            // SerializationException is a subtype, so a body that doesn't decode lands here too.
            Log.w(TAG, "Unexpected response to GET $PALETTE_PATH", ex)
            LibrarySearchResult.Failure(LibrarySearchError.UnexpectedResponse)
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure for GET $PALETTE_PATH", ex)
            LibrarySearchResult.Failure(LibrarySearchError.UnexpectedResponse)
        }
    }

    internal companion object {
        const val TAG = "DefaultLibrarySearchRepository"

        /** Relative, so it resolves beneath the server's base URL. */
        const val PALETTE_PATH = "api/search/palette"
        const val HTTP_UNAUTHORIZED = 401
    }
}
