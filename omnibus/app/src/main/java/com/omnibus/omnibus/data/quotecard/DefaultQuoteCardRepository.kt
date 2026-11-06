package com.omnibus.omnibus.data.quotecard

import android.util.Log
import com.omnibus.omnibus.data.export.local.ExportFileStore
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.quotecard.remote.CoverPaletteResponseDto
import com.omnibus.omnibus.data.quotecard.remote.QuoteCardJson
import com.omnibus.omnibus.di.IoDispatcher
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Default [QuoteCardRepository]. Cover pairs are optional, so every failure reads as none. A card is written under the
 * export folders, so it is shared through the export FileProvider and purged with the other exports. No exception leaves
 * this class except cancellation.
 */
class DefaultQuoteCardRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val omnibusHttpClient: OmnibusHttpClient,
    private val exportFileStore: ExportFileStore,
) : QuoteCardRepository {

    override suspend fun getCoverPairs(bookUuid: String): List<CoverPair> = withContext(ioDispatcher) {
        if (bookUuid.isBlank()) return@withContext emptyList()
        val builder = omnibusHttpClient.request(PALETTE_PATH_PREFIX) ?: return@withContext emptyList()
        val url = builder.build().url.newBuilder()
            .addPathSegment(bookUuid)
            .addPathSegment("cover")
            .addPathSegment("palette")
            .build()
        try {
            omnibusHttpClient.execute(builder.url(url).get().build()).use { response ->
                if (!response.isSuccessful) {
                    emptyList()
                } else {
                    QuoteCardJson.decodeFromString<CoverPaletteResponseDto>(response.body.string()).toDomain()
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            // An older server answers 404, and any other failure only costs the cover's own gradients.
            Log.w(TAG, "Unable to read the cover palette", ex)
            emptyList()
        }
    }

    override suspend fun saveCard(png: ByteArray): File? = withContext(ioDispatcher) {
        try {
            File(exportFileStore.newFolder(), CARD_FILE_NAME).also { it.writeBytes(png) }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Log.w(TAG, "Unable to write the quote card", ex)
            null
        }
    }

    internal companion object {
        const val TAG = "DefaultQuoteCardRepository"

        /** Relative, so it resolves beneath the server's base URL. */
        const val PALETTE_PATH_PREFIX = "api/books"

        /** A constant, so the shared file's name never comes from a passage. */
        const val CARD_FILE_NAME = "omnibus-quote.png"
    }
}
