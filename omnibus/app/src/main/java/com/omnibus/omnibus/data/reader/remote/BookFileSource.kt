package com.omnibus.omnibus.data.reader.remote

import android.util.Log
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.reader.BookFileResult
import com.omnibus.omnibus.data.reader.local.LocalBookFiles
import com.omnibus.omnibus.di.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** Opens a book's EPUB for streaming into the reader. */
fun interface BookFileSource {

    /**
     * Opens the [uuid] book from the downloaded copy when there is one, otherwise from the server.
     * Failures come back as a [BookFileResult.Failure]; nothing is thrown except cancellation.
     */
    suspend fun open(uuid: String): BookFileResult
}

/** Default [BookFileSource]: streams `GET api/ebooks/{uuid}/file` with the bearer token attached. */
class DefaultBookFileSource @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val omnibusHttpClient: OmnibusHttpClient,
    private val localBookFiles: LocalBookFiles,
) : BookFileSource {

    override suspend fun open(uuid: String): BookFileResult =
        withContext(ioDispatcher) {
            openLocal(uuid) ?: openRemote(uuid)
        }

    private fun openLocal(uuid: String): BookFileResult.Opened? {
        val file = localBookFiles.find(uuid) ?: return null
        return try {
            BookFileResult.Opened(file.inputStream(), file.length())
        } catch (e: IOException) {
            // A download that has gone missing falls back to the server.
            Log.w(TAG, "Couldn't open the downloaded copy of $uuid", e)
            null
        }
    }

    private suspend fun openRemote(uuid: String): BookFileResult {
        val request = omnibusHttpClient.request(filePath(uuid))
            ?.header("Accept", ACCEPT)
            ?.build() ?: return BookFileResult.NoServer
        return try {
            val response = omnibusHttpClient.execute(request)
            when {
                response.isSuccessful ->
                    // Closing the stream closes the body, which closes the response.
                    BookFileResult.Opened(response.body.byteStream(), response.body.contentLength())
                else -> response.use { failure(it.code) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't reach the server for $uuid", e)
            BookFileResult.Error
        } catch (t: Throwable) {
            Log.e(TAG, "Unexpected failure opening $uuid", t)
            BookFileResult.Error
        }
    }

    private fun failure(code: Int): BookFileResult.Failure =
        when (code) {
            401 -> BookFileResult.Unauthenticated
            404 -> BookFileResult.NotFound
            else -> BookFileResult.Error
        }

    private companion object {
        const val TAG = "BookFileSource"
        const val ACCEPT = "application/epub+zip,*/*;q=0.8"

        fun filePath(uuid: String) = "api/ebooks/$uuid/file"
    }
}
