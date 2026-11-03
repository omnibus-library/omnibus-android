package com.omnibus.omnibus.data.merge

import android.util.Log
import com.omnibus.omnibus.data.library.remote.BookDto
import com.omnibus.omnibus.data.merge.remote.MergeBooksRequestDto
import com.omnibus.omnibus.data.merge.remote.MergeBooksResponseDto
import com.omnibus.omnibus.data.merge.remote.MergeJson
import com.omnibus.omnibus.data.merge.remote.UndoMergeRequestDto
import com.omnibus.omnibus.data.merge.remote.UndoMergeResponseDto
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.di.IoDispatcher
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject

/**
 * Default [MergeRepository]. Every failure becomes a result, so no exception leaves this class, except cancellation,
 * which always propagates.
 */
class DefaultMergeRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val omnibusHttpClient: OmnibusHttpClient,
) : MergeRepository {

    override suspend fun searchCandidates(query: String): MergeCandidatesResult {
        // The server rejects an empty query, so a blank one is answered without a request.
        if (query.isBlank()) return MergeCandidatesResult.Success(emptyList())

        return when (val read = call(CANDIDATES_PATH, query = query, body = null) { body ->
            MergeJson.decodeFromString<List<BookDto>>(body)
                .map { it.toDomain() }
                .filter { !it.uniqueIdentifier.isNullOrBlank() }
        }) {
            is Outcome.Ok -> MergeCandidatesResult.Success(read.value)
            is Outcome.Failed -> MergeCandidatesResult.Failure(read.error)
            Outcome.NoServer -> MergeCandidatesResult.NoServer
        }
    }

    override suspend fun mergeBooks(sourceUuid: String, targetUuid: String): MergeBooksResult {
        // A blank uuid would address a different route, so it is answered without a request.
        if (sourceUuid.isBlank() || targetUuid.isBlank()) return MergeBooksResult.Failure(MergeError.NotFound)

        val body = MergeBooksRequestDto(sourceUuid = sourceUuid, targetUuid = targetUuid).toJsonRequest()
        return when (val write = call(MERGE_PATH, query = null, body = body) { response ->
            MergeJson.decodeFromString<MergeBooksResponseDto>(response)
        }) {
            is Outcome.Ok -> MergeBooksResult.Success(
                MergeReceipt(mergeLogId = write.value.mergeLogId, targetUuid = write.value.targetUuid),
            )
            is Outcome.Failed -> MergeBooksResult.Failure(write.error)
            Outcome.NoServer -> MergeBooksResult.NoServer
        }
    }

    override suspend fun undoMerge(mergeLogId: Long): UndoMergeResult {
        // The server has no merge with an id below 1, so it is answered without a request.
        if (mergeLogId <= 0) return UndoMergeResult.Failure(MergeError.NotFound)

        val body = UndoMergeRequestDto(mergeLogId = mergeLogId).toJsonRequest()
        return when (val write = call(UNDO_PATH, query = null, body = body) { response ->
            MergeJson.decodeFromString<UndoMergeResponseDto>(response)
        }) {
            is Outcome.Ok -> UndoMergeResult.Success(write.value.restoredUuid)
            is Outcome.Failed -> UndoMergeResult.Failure(write.error)
            Outcome.NoServer -> UndoMergeResult.NoServer
        }
    }

    /**
     * One request, reduced to a value or a [MergeError]. A GET when [body] is null, else a POST. [query] is added as the
     * `q` parameter, so the value is encoded once and can't add parameters.
     */
    private suspend fun <T> call(
        path: String,
        query: String?,
        body: RequestBody?,
        decode: (String) -> T,
    ): Outcome<T> = withContext(ioDispatcher) {
        val builder = omnibusHttpClient.request(path) ?: return@withContext Outcome.NoServer
        if (query != null) {
            builder.url(builder.build().url.newBuilder().addQueryParameter(QUERY, query).build())
        }
        val request = if (body == null) builder.get().build() else builder.post(body).build()

        try {
            omnibusHttpClient.execute(request).use { response ->
                if (response.isSuccessful) {
                    Outcome.Ok(decode(response.body.string()))
                } else {
                    Outcome.Failed(errorOf(response))
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server for $path", ex)
            Outcome.Failed(MergeError.Unreachable)
        } catch (ex: IllegalArgumentException) {
            // SerializationException is a subtype, so a body that doesn't decode lands here too.
            Log.w(TAG, "Unexpected response for $path", ex)
            Outcome.Failed(MergeError.UnexpectedResponse)
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure for $path", ex)
            Outcome.Failed(MergeError.UnexpectedResponse)
        }
    }

    /** Maps a non-2xx response to its error. The body is read only for the server's message. */
    private fun errorOf(response: Response): MergeError = when (response.code) {
        HTTP_UNAUTHORIZED -> MergeError.SessionExpired
        HTTP_FORBIDDEN -> MergeError.Forbidden
        HTTP_NOT_FOUND -> MergeError.NotFound
        HTTP_CONFLICT -> MergeError.Conflict(serverMessage(response.body.string()))
        else -> MergeError.Http(response.code, serverMessage(response.body.string()))
    }

    /** The server's own text: the JSON `error`, `message` or `detail` string, else the plain-text body. */
    private fun serverMessage(body: String): String? {
        val json = try {
            Json.parseToJsonElement(body) as? JsonObject
        } catch (ex: SerializationException) {
            null
        }
        return json
            ?.let { obj ->
                SERVER_MESSAGE_KEYS.firstNotNullOfOrNull { key ->
                    (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
                        ?.takeIf { it.isNotBlank() }
                }
            }
            ?: body.trim().take(MAX_SERVER_MESSAGE_LENGTH).takeIf { it.isNotBlank() }
    }

    /** The outcome of one request before it is turned into a read's or write's own result type. */
    private sealed interface Outcome<out T> {
        data class Ok<out T>(val value: T) : Outcome<T>
        data class Failed(val error: MergeError) : Outcome<Nothing>
        data object NoServer : Outcome<Nothing>
    }

    internal companion object {
        const val TAG = "DefaultMergeRepository"

        const val CANDIDATES_PATH = "api/books/merge/candidates"
        const val MERGE_PATH = "api/books/merge"
        const val UNDO_PATH = "api/books/merge/undo"
        const val QUERY = "q"

        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        const val HTTP_CONFLICT = 409

        const val MAX_SERVER_MESSAGE_LENGTH = 300
        val SERVER_MESSAGE_KEYS = listOf("error", "message", "detail")
    }
}
