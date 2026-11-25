package com.omnibus.omnibus.data.series

import android.util.Log
import com.omnibus.omnibus.data.library.remote.LibraryJson
import com.omnibus.omnibus.data.library.remote.SeriesBooksResponseDto
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.series.remote.SeriesDetailDto
import com.omnibus.omnibus.data.series.remote.SeriesJson
import com.omnibus.omnibus.di.IoDispatcher
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/**
 * Default [SeriesRepository]. Every failure becomes a result, so no exception leaves this class,
 * except cancellation, which always propagates.
 */
class DefaultSeriesRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val omnibusHttpClient: OmnibusHttpClient,
) : SeriesRepository {

    override suspend fun getSeries(seriesId: Long): SeriesResult = when (val read = read(seriesId) { body ->
        SeriesJson.decodeFromString<SeriesDetailDto>(body).books.map { it.toMember() }.inSeriesOrder()
    }) {
        is Read.Ok -> SeriesResult.Success(read.value)
        is Read.Failed -> SeriesResult.Failure(read.error)
        Read.NoServer -> SeriesResult.NoServer
    }

    override suspend fun getSeriesDetails(seriesId: Long): SeriesDetailsResult = when (val read = read(seriesId) { body ->
        LibraryJson.decodeFromString<SeriesBooksResponseDto>(body).let { dto ->
            SeriesDetails(dto.name, dto.books.map { it.toDomain() })
        }
    }) {
        is Read.Ok -> SeriesDetailsResult.Success(read.value)
        is Read.Failed -> SeriesDetailsResult.Failure(read.error)
        Read.NoServer -> SeriesDetailsResult.NoServer
    }

    /** One GET of series [seriesId], reduced to [decode]'s value or a [SeriesError]. */
    private suspend fun <T> read(seriesId: Long, decode: (body: String) -> T): Read<T> = withContext(ioDispatcher) {
        val builder = omnibusHttpClient.request(SERIES_PATH) ?: return@withContext Read.NoServer
        val url = builder.build().url.newBuilder().addPathSegment(seriesId.toString()).build()
        val request = builder.url(url).get().build()

        try {
            omnibusHttpClient.execute(request).use { response ->
                when {
                    response.isSuccessful -> Read.Ok(decode(response.body.string()))
                    response.code == HTTP_UNAUTHORIZED -> Read.Failed(SeriesError.SessionExpired)
                    response.code == HTTP_NOT_FOUND -> Read.Failed(SeriesError.NotFound)
                    else -> Read.Failed(SeriesError.Http(response.code))
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server for series $seriesId", ex)
            Read.Failed(SeriesError.Unreachable)
        } catch (ex: IllegalArgumentException) {
            // SerializationException is a subtype, so a body that doesn't decode lands here too.
            Log.w(TAG, "Unexpected series response", ex)
            Read.Failed(SeriesError.UnexpectedResponse)
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure reading series $seriesId", ex)
            Read.Failed(SeriesError.UnexpectedResponse)
        }
    }

    /** The outcome of one series GET, before the caller maps it to its own result type. */
    private sealed interface Read<out T> {
        data class Ok<out T>(val value: T) : Read<T>
        data class Failed(val error: SeriesError) : Read<Nothing>
        data object NoServer : Read<Nothing>
    }

    internal companion object {
        const val TAG = "DefaultSeriesRepository"
        const val SERIES_PATH = "api/series"
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_NOT_FOUND = 404
    }
}
