package com.omnibus.omnibus.data.authors

import android.util.Log
import com.omnibus.omnibus.data.authors.remote.AuthorDetailDto
import com.omnibus.omnibus.data.authors.remote.AuthorSummaryDto
import com.omnibus.omnibus.data.library.remote.LibraryJson
import com.omnibus.omnibus.data.metadata.VocabularyEntry
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.di.IoDispatcher
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/**
 * Default [AuthorsRepository]. Every failure becomes a result, so no exception leaves this class, except
 * cancellation, which always propagates.
 */
class DefaultAuthorsRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val omnibusHttpClient: OmnibusHttpClient,
) : AuthorsRepository {

    override suspend fun getAuthor(authorId: Long): AuthorResult = withContext(ioDispatcher) {
        val builder = omnibusHttpClient.request(AUTHORS_PATH) ?: return@withContext AuthorResult.NoServer
        val url = builder.build().url.newBuilder().addPathSegment(authorId.toString()).build()
        val request = builder.url(url).get().build()

        try {
            omnibusHttpClient.execute(request).use { response ->
                if (response.isSuccessful) {
                    val dto = LibraryJson.decodeFromString<AuthorDetailDto>(response.body.string())
                    AuthorResult.Success(dto.toDomain())
                } else {
                    AuthorResult.Failure(errorOf(response.code))
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server for $AUTHORS_PATH", ex)
            AuthorResult.Failure(AuthorsError.Unreachable)
        } catch (ex: IllegalArgumentException) {
            // SerializationException is a subtype, so a body that doesn't decode lands here too.
            Log.w(TAG, "Unexpected response for $AUTHORS_PATH", ex)
            AuthorResult.Failure(AuthorsError.UnexpectedResponse)
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure reading $AUTHORS_PATH", ex)
            AuthorResult.Failure(AuthorsError.UnexpectedResponse)
        }
    }

    override suspend fun getAuthors(): AuthorListResult = withContext(ioDispatcher) {
        val builder = omnibusHttpClient.request(AUTHORS_PATH) ?: return@withContext AuthorListResult.NoServer
        val request = builder.get().build()

        try {
            omnibusHttpClient.execute(request).use { response ->
                if (response.isSuccessful) {
                    val entries = LibraryJson.decodeFromString<List<AuthorSummaryDto>>(response.body.string())
                        .filter { it.name.isNotBlank() }
                        .map { VocabularyEntry(name = it.name, count = it.bookCount) }
                    AuthorListResult.Success(entries)
                } else {
                    AuthorListResult.Failure(errorOf(response.code))
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server for $AUTHORS_PATH", ex)
            AuthorListResult.Failure(AuthorsError.Unreachable)
        } catch (ex: IllegalArgumentException) {
            Log.w(TAG, "Unexpected response for $AUTHORS_PATH", ex)
            AuthorListResult.Failure(AuthorsError.UnexpectedResponse)
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure reading $AUTHORS_PATH", ex)
            AuthorListResult.Failure(AuthorsError.UnexpectedResponse)
        }
    }

    private fun errorOf(code: Int): AuthorsError = when (code) {
        HTTP_UNAUTHORIZED -> AuthorsError.SessionExpired
        HTTP_NOT_FOUND -> AuthorsError.NotFound
        else -> AuthorsError.Http(code)
    }

    internal companion object {
        const val TAG = "DefaultAuthorsRepository"

        const val AUTHORS_PATH = "api/authors"

        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_NOT_FOUND = 404
    }
}
