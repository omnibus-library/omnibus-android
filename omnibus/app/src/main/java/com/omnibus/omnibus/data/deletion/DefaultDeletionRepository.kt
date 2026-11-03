package com.omnibus.omnibus.data.deletion

import android.util.Log
import com.omnibus.omnibus.data.deletion.remote.DeleteFilesRequestDto
import com.omnibus.omnibus.data.deletion.remote.DeleteFilesResponseDto
import com.omnibus.omnibus.data.deletion.remote.DeletionJson
import com.omnibus.omnibus.data.deletion.remote.DeletionManifestResponseDto
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.di.ApplicationScope
import com.omnibus.omnibus.di.IoDispatcher
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Default [DeletionRepository]. The delete request runs in the application scope, so leaving the page cannot cancel
 * it. Every failure becomes a result, so no exception leaves this class except cancellation.
 */
@Singleton
class DefaultDeletionRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val omnibusHttpClient: OmnibusHttpClient,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : DeletionRepository {

    override suspend fun fetchManifest(bookUuid: String): DeletionManifestResult {
        if (bookUuid.isBlank()) return DeletionManifestResult.Failed(DeletionError.BookNotFound)
        return withContext(ioDispatcher) {
            val builder = omnibusHttpClient.request(BOOKS_PATH)
                ?: return@withContext DeletionManifestResult.Failed(DeletionError.NoServer)
            val request: Request = builder.url(bookUrl(builder, bookUuid, MANIFEST_SEGMENT)).get().build()

            try {
                omnibusHttpClient.execute(request).use { response ->
                    when {
                        response.isSuccessful -> {
                            val dto = DeletionJson.decodeFromString<DeletionManifestResponseDto>(response.body.string())
                            DeletionManifestResult.Success(dto.toDomain())
                        }
                        response.code == HTTP_UNAUTHORIZED -> DeletionManifestResult.Failed(DeletionError.SessionExpired)
                        response.code == HTTP_FORBIDDEN -> DeletionManifestResult.Failed(DeletionError.Forbidden)
                        response.code == HTTP_NOT_FOUND -> DeletionManifestResult.Failed(DeletionError.BookNotFound)
                        else -> DeletionManifestResult.Failed(DeletionError.Http(response.code))
                    }
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: IOException) {
                Log.w(TAG, "Unable to reach the server to read a book's deletion manifest", ex)
                DeletionManifestResult.Failed(DeletionError.Unreachable)
            } catch (ex: IllegalArgumentException) {
                // SerializationException is a subtype, so a body that doesn't decode lands here too.
                Log.w(TAG, "Unexpected response to a deletion manifest read", ex)
                DeletionManifestResult.Failed(DeletionError.UnexpectedResponse)
            } catch (ex: Exception) {
                Log.e(TAG, "Unexpected failure reading a deletion manifest", ex)
                DeletionManifestResult.Failed(DeletionError.UnexpectedResponse)
            }
        }
    }

    override suspend fun deleteItems(bookUuid: String, fileIds: Set<Long>, copyIds: Set<Long>): DeleteItemsResult {
        if (bookUuid.isBlank()) return DeleteItemsResult.Failed(DeletionError.BookNotFound)
        return applicationScope.async { post(bookUuid, fileIds, copyIds) }.await()
    }

    /** One POST that deletes the picked items. A 2xx answer says what was deleted and whether the book went too. */
    private suspend fun post(uuid: String, fileIds: Set<Long>, copyIds: Set<Long>): DeleteItemsResult =
        withContext(ioDispatcher) {
            val builder = omnibusHttpClient.request(BOOKS_PATH)
                ?: return@withContext DeleteItemsResult.Failed(DeletionError.NoServer)
            val body = DeletionJson
                .encodeToString(DeleteFilesRequestDto(fileIds.sorted(), copyIds.sorted()))
                .toRequestBody("application/json".toMediaType())
            val request: Request = builder.url(bookUrl(builder, uuid, DELETE_SEGMENT)).post(body).build()

            try {
                omnibusHttpClient.execute(request).use { response ->
                    when {
                        response.isSuccessful -> {
                            val dto = DeletionJson.decodeFromString<DeleteFilesResponseDto>(response.body.string())
                            dto.toDomain()
                        }
                        response.code == HTTP_UNAUTHORIZED -> DeleteItemsResult.Failed(DeletionError.SessionExpired)
                        response.code == HTTP_FORBIDDEN -> DeleteItemsResult.Failed(DeletionError.Forbidden)
                        response.code == HTTP_NOT_FOUND -> DeleteItemsResult.Failed(DeletionError.BookNotFound)
                        response.code == HTTP_UNPROCESSABLE -> DeleteItemsResult.Failed(DeletionError.ItemNotFound)
                        else -> DeleteItemsResult.Failed(DeletionError.Http(response.code))
                    }
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: IOException) {
                Log.w(TAG, "Unable to reach the server to delete a book's items", ex)
                DeleteItemsResult.Failed(DeletionError.Unreachable)
            } catch (ex: IllegalArgumentException) {
                Log.w(TAG, "Unexpected response to a deletion request", ex)
                DeleteItemsResult.Failed(DeletionError.UnexpectedResponse)
            } catch (ex: Exception) {
                Log.e(TAG, "Unexpected failure deleting a book's items", ex)
                DeleteItemsResult.Failed(DeletionError.UnexpectedResponse)
            }
        }

    /** The `api/books/{uuid}/{segment}` URL. OkHttp encodes the uuid as one path segment. */
    private fun bookUrl(builder: Request.Builder, uuid: String, segment: String): HttpUrl =
        builder.build().url.newBuilder()
            .addPathSegment(uuid)
            .addPathSegment(segment)
            .build()

    internal companion object {
        const val TAG = "DefaultDeletionRepository"

        /** Relative, so it resolves beneath the server's base URL. */
        const val BOOKS_PATH = "api/books"
        const val MANIFEST_SEGMENT = "deletion-manifest"
        const val DELETE_SEGMENT = "delete-files"

        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        const val HTTP_UNPROCESSABLE = 422
    }
}
