package com.omnibus.omnibus.data.scan

import android.util.Log
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.scan.remote.BookRefDto
import com.omnibus.omnibus.data.scan.remote.CheckInRequestDto
import com.omnibus.omnibus.data.scan.remote.ResolveRequestDto
import com.omnibus.omnibus.data.scan.remote.ScanJson
import com.omnibus.omnibus.data.scan.remote.decodeScanOutcome
import com.omnibus.omnibus.data.wishlist.WishlistRepository
import com.omnibus.omnibus.di.ApplicationScope
import com.omnibus.omnibus.di.IoDispatcher
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Default [ScanRepository]. Every failure becomes a [ScanResult] (no exception leaves this class except cancellation).
 * Check-ins run in the application scope, so leaving the screen cannot cancel one halfway.
 */
class DefaultScanRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val omnibusHttpClient: OmnibusHttpClient,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    private val wishlistRepository: WishlistRepository,
) : ScanRepository {

    override suspend fun resolve(isbn: String): ScanResult<ScanOutcome> =
        post(RESOLVE_PATH, ScanJson.encodeToString(ResolveRequestDto(isbn)), ::decodeScanOutcome)

    override suspend fun checkIn(bookUuid: String, isbn: String?, note: String?): ScanResult<String> =
        applicationScope.async {
            post(CHECK_IN_PATH, ScanJson.encodeToString(CheckInRequestDto(bookUuid, isbn, note))) {
                ScanJson.decodeFromString<BookRefDto>(it).bookUuid
            }.also { result ->
                if (result is ScanResult.Success) {
                    wishlistRepository.invalidate(bookUuid)
                    if (result.value != bookUuid) wishlistRepository.invalidate(result.value)
                }
            }
        }.await()

    private suspend fun <T> post(path: String, json: String, decode: (String) -> T): ScanResult<T> =
        withContext(ioDispatcher) {
            val builder = omnibusHttpClient.request(path) ?: return@withContext ScanResult.NoServer
            val request = builder.post(json.toRequestBody(JSON_MEDIA_TYPE)).build()
            try {
                omnibusHttpClient.execute(request).use { response ->
                    if (response.isSuccessful) {
                        ScanResult.Success(decode(response.body.string()))
                    } else {
                        ScanResult.Failure(errorFor(response.code))
                    }
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: IOException) {
                Log.w(TAG, "Unable to reach the server for $path", ex)
                ScanResult.Failure(ScanError.Unreachable)
            } catch (ex: Exception) {
                Log.w(TAG, "Unexpected response to $path", ex)
                ScanResult.Failure(ScanError.UnexpectedResponse)
            }
        }

    private fun errorFor(code: Int): ScanError = when (code) {
        400 -> ScanError.Rejected
        401 -> ScanError.SessionExpired
        404 -> ScanError.NotFound
        503 -> ScanError.ProvidersUnreachable
        else -> ScanError.Http(code)
    }

    internal companion object {
        const val TAG = "DefaultScanRepository"
        const val RESOLVE_PATH = "api/scan/resolve"
        const val CHECK_IN_PATH = "api/scan/check-in"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
