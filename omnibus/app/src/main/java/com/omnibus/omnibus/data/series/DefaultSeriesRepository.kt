package com.omnibus.omnibus.data.series

import android.util.Log
import com.omnibus.omnibus.data.metadata.VocabularyEntry
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.series.remote.SeriesDetailDto
import com.omnibus.omnibus.data.series.remote.SeriesJson
import com.omnibus.omnibus.data.series.remote.SeriesSummaryDto
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

    override suspend fun getSeries(seriesId: Long): SeriesResult = withContext(ioDispatcher) {
        val builder = omnibusHttpClient.request(SERIES_PATH) ?: return@withContext SeriesResult.NoServer
        val url = builder.build().url.newBuilder().addPathSegment(seriesId.toString()).build()
        val request = builder.url(url).get().build()

        try {
            omnibusHttpClient.execute(request).use { response ->
                when {
                    response.isSuccessful -> SeriesResult.Success(
                        SeriesJson.decodeFromString<SeriesDetailDto>(response.body.string())
                            .books.map { it.toMember() }.inSeriesOrder(),
                    )
                    response.code == HTTP_UNAUTHORIZED -> SeriesResult.Failure(SeriesError.SessionExpired)
                    response.code == HTTP_NOT_FOUND -> SeriesResult.Failure(SeriesError.NotFound)
                    else -> SeriesResult.Failure(SeriesError.Http(response.code))
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server for series $seriesId", ex)
            SeriesResult.Failure(SeriesError.Unreachable)
        } catch (ex: IllegalArgumentException) {
            // SerializationException is a subtype, so a body that doesn't decode lands here too.
            Log.w(TAG, "Unexpected series response", ex)
            SeriesResult.Failure(SeriesError.UnexpectedResponse)
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure reading series $seriesId", ex)
            SeriesResult.Failure(SeriesError.UnexpectedResponse)
        }
    }

    override suspend fun getSeriesList(): SeriesListResult = withContext(ioDispatcher) {
        val builder = omnibusHttpClient.request(SERIES_PATH) ?: return@withContext SeriesListResult.NoServer
        val request = builder.get().build()

        try {
            omnibusHttpClient.execute(request).use { response ->
                when {
                    response.isSuccessful -> SeriesListResult.Success(
                        SeriesJson.decodeFromString<List<SeriesSummaryDto>>(response.body.string())
                            .filter { it.name.isNotBlank() }
                            .map { VocabularyEntry(name = it.name, count = it.bookCount) },
                    )
                    response.code == HTTP_UNAUTHORIZED -> SeriesListResult.Failure(SeriesError.SessionExpired)
                    response.code == HTTP_NOT_FOUND -> SeriesListResult.Failure(SeriesError.NotFound)
                    else -> SeriesListResult.Failure(SeriesError.Http(response.code))
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server for $SERIES_PATH", ex)
            SeriesListResult.Failure(SeriesError.Unreachable)
        } catch (ex: IllegalArgumentException) {
            Log.w(TAG, "Unexpected series list response", ex)
            SeriesListResult.Failure(SeriesError.UnexpectedResponse)
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure reading the series list", ex)
            SeriesListResult.Failure(SeriesError.UnexpectedResponse)
        }
    }

    internal companion object {
        const val TAG = "DefaultSeriesRepository"
        const val SERIES_PATH = "api/series"
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_NOT_FOUND = 404
    }
}
