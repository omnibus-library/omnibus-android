package com.omnibus.omnibus.data.suggestions

import android.util.Log
import com.omnibus.omnibus.data.library.remote.LibraryJson
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.suggestions.remote.SuggestionsResponseDto
import com.omnibus.omnibus.di.IoDispatcher
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/**
 * Default [SuggestionsRepository]. Every failure becomes a result, so no exception leaves this class, except
 * cancellation, which always propagates.
 */
class DefaultSuggestionsRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val omnibusHttpClient: OmnibusHttpClient,
) : SuggestionsRepository {

    override suspend fun getSuggestions(bookUuid: String): SuggestionsResult = withContext(ioDispatcher) {
        val builder = omnibusHttpClient.request(EBOOKS_PATH) ?: return@withContext SuggestionsResult.NoServer
        // addPathSegment encodes the uuid, so a reserved character cannot add a path segment.
        val url = builder.build().url.newBuilder()
            .addPathSegment(bookUuid)
            .addPathSegment("suggestions")
            .build()
        val request = builder.url(url).get().build()

        try {
            omnibusHttpClient.execute(request).use { response ->
                if (response.isSuccessful) {
                    val answer = LibraryJson
                        .decodeFromString<SuggestionsResponseDto>(response.body.string())
                        .toAnswer()
                    if (answer == null) {
                        SuggestionsResult.Failure(SuggestionsError.UnexpectedResponse)
                    } else {
                        SuggestionsResult.Success(answer)
                    }
                } else {
                    SuggestionsResult.Failure(errorOf(response.code))
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server for $EBOOKS_PATH", ex)
            SuggestionsResult.Failure(SuggestionsError.Unreachable)
        } catch (ex: IllegalArgumentException) {
            // SerializationException is a subtype, so a body that doesn't decode lands here too.
            Log.w(TAG, "Unexpected response for $EBOOKS_PATH", ex)
            SuggestionsResult.Failure(SuggestionsError.UnexpectedResponse)
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure reading $EBOOKS_PATH", ex)
            SuggestionsResult.Failure(SuggestionsError.UnexpectedResponse)
        }
    }

    override suspend fun getCover(coverPath: String): SuggestionCoverResult = withContext(ioDispatcher) {
        // Checked before any request, so the bearer token never goes to a path this app did not expect.
        val relative = relativeCoverPath(coverPath) ?: return@withContext SuggestionCoverResult.NotFound
        val builder = omnibusHttpClient.request(relative) ?: return@withContext SuggestionCoverResult.Failure

        try {
            omnibusHttpClient.execute(builder.get().build()).use { response ->
                when {
                    response.isSuccessful -> SuggestionCoverResult.Found(
                        bytes = response.body.bytes(),
                        mimeType = response.header("Content-Type"),
                    )
                    response.code == HTTP_NOT_FOUND -> SuggestionCoverResult.NotFound
                    else -> SuggestionCoverResult.Failure
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Log.w(TAG, "Unable to load the suggestion cover", ex)
            SuggestionCoverResult.Failure
        }
    }

    private fun errorOf(code: Int): SuggestionsError =
        if (code == HTTP_UNAUTHORIZED) SuggestionsError.SessionExpired else SuggestionsError.Http(code)

    internal companion object {
        const val TAG = "DefaultSuggestionsRepository"

        const val EBOOKS_PATH = "api/ebooks"

        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_NOT_FOUND = 404

        /** Only `/api/suggestions/{uuid}/{rank}/cover`, with no separators or query in the uuid segment. */
        private val COVER_PATH = Regex("""^/?api/suggestions/([^/\\?#]+)/([0-9]+)/cover$""")

        /**
         * [coverPath] without its leading slash, so it resolves under the saved base URL (which keeps a proxy
         * prefix). Null when [coverPath] is not a suggestion cover path, so no request is made.
         */
        fun relativeCoverPath(coverPath: String): String? {
            val match = COVER_PATH.matchEntire(coverPath) ?: return null
            // OkHttp treats an encoded %2e as a dot, so a uuid of %2e%2e must be rejected like `..`.
            val uuid = match.groupValues[1].replace("%2e", ".", ignoreCase = true)
            if (uuid == "." || uuid == "..") return null
            return coverPath.removePrefix("/")
        }
    }
}
