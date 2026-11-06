package com.omnibus.omnibus.data.quotecard

import java.io.File
import kotlinx.coroutines.CompletableDeferred

/**
 * In-memory [QuoteCardRepository] for consumers' tests. [saveCard] records its input and answers [saveResult], after
 * waiting on [saveGate] when one is set.
 */
class FakeQuoteCardRepository : QuoteCardRepository {

    /** The bytes of every save, in call order. */
    val saved = mutableListOf<ByteArray>()

    /** What [saveCard] returns. Null reads as a failed write. */
    var saveResult: File? = File("omnibus-quote.png")

    /** When set, a save waits here after recording its input, so a test can observe the call in flight. */
    var saveGate: CompletableDeferred<Unit>? = null

    override suspend fun saveCard(png: ByteArray): File? {
        saved += png
        saveGate?.await()
        return saveResult
    }
}
