package com.omnibus.omnibus.data.quotecard

import android.util.Log
import com.omnibus.omnibus.data.export.local.ExportFileStore
import com.omnibus.omnibus.di.IoDispatcher
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Default [QuoteCardRepository]. A card is written under the export folders, so it is shared through the export
 * FileProvider and purged with the other exports. No exception leaves this class except cancellation.
 */
class DefaultQuoteCardRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val exportFileStore: ExportFileStore,
) : QuoteCardRepository {

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

        /** A constant, so the shared file's name never comes from a passage. */
        const val CARD_FILE_NAME = "omnibus-quote.png"
    }
}
