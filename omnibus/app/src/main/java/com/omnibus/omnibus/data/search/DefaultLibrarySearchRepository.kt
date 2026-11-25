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
 * Default [LibrarySearchRepository]. Both searches share one request, so every failure becomes a result and no
 * exception leaves this class, except cancellation, which always propagates.
 */
class DefaultLibrarySearchRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val omnibusHttpClient: OmnibusHttpClient,
) : LibrarySearchRepository {

    override suspend fun searchBooks(query: String): LibrarySearchResult = when (val fetch = fetchPalette(query)) {
        is PaletteFetch.Decoded -> LibrarySearchResult.Success(fetch.dto.toDomain())
        PaletteFetch.NoServer -> LibrarySearchResult.NoServer
        is PaletteFetch.Failed -> LibrarySearchResult.Failure(fetch.error)
    }

    override suspend fun searchLibrary(query: String): PaletteSearchResult = when (val fetch = fetchPalette(query)) {
        is PaletteFetch.Decoded -> PaletteSearchResult.Success(fetch.dto.toResults())
        PaletteFetch.NoServer -> PaletteSearchResult.NoServer
        is PaletteFetch.Failed -> PaletteSearchResult.Failure(fetch.error)
    }

    /** One `GET api/search/palette` for [query], with its status and exceptions mapped to what the caller can show. */
    private suspend fun fetchPalette(query: String): PaletteFetch = withContext(ioDispatcher) {
        val builder = omnibusHttpClient.request(PALETTE_PATH) ?: return@withContext PaletteFetch.NoServer
        val url = builder.build().url.newBuilder().addQueryParameter("q", query).build()
        val request = builder.url(url).build()
        try {
            omnibusHttpClient.execute(request).use { response ->
                when {
                    response.isSuccessful -> PaletteFetch.Decoded(
                        PaletteJson.decodeFromString<PaletteResultsDto>(response.body.string()),
                    )
                    response.code == HTTP_UNAUTHORIZED -> PaletteFetch.Failed(LibrarySearchError.SessionExpired)
                    else -> PaletteFetch.Failed(LibrarySearchError.Http(response.code))
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server for GET $PALETTE_PATH", ex)
            PaletteFetch.Failed(LibrarySearchError.Unreachable)
        } catch (ex: IllegalArgumentException) {
            // SerializationException is a subtype, so a body that doesn't decode lands here too.
            Log.w(TAG, "Unexpected response to GET $PALETTE_PATH", ex)
            PaletteFetch.Failed(LibrarySearchError.UnexpectedResponse)
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure for GET $PALETTE_PATH", ex)
            PaletteFetch.Failed(LibrarySearchError.UnexpectedResponse)
        }
    }

    /** What one palette request came back with, before either search maps it to its result. */
    private sealed interface PaletteFetch {
        data class Decoded(val dto: PaletteResultsDto) : PaletteFetch
        data object NoServer : PaletteFetch
        data class Failed(val error: LibrarySearchError) : PaletteFetch
    }

    internal companion object {
        const val TAG = "DefaultLibrarySearchRepository"

        /** Relative, so it resolves beneath the server's base URL. */
        const val PALETTE_PATH = "api/search/palette"
        const val HTTP_UNAUTHORIZED = 401
    }
}
