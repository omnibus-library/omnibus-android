package com.omnibus.omnibus.data.quotecard

import com.omnibus.omnibus.testing.unscripted
import java.io.File
import kotlinx.coroutines.awaitCancellation

/**
 * In-memory [QuoteCardRepository] for tests. [getCoverPairs] answers [coverPairs], and stays unanswered until a test
 * sets them. [saveCard] records its input, then answers through [onSave], which a test must script.
 */
class TestQuoteCardRepository : QuoteCardRepository {

    /** What [getCoverPairs] returns. Null leaves the call unanswered. */
    var coverPairs: List<CoverPair>? = null

    /** The uuid of every [getCoverPairs] call, in order. */
    val coverPairCalls = mutableListOf<String>()

    /** The bytes of every save, in call order. */
    val saved = mutableListOf<ByteArray>()

    /** What [saveCard] returns, called after its input is recorded. A test can hold it on a gate to leave a save in flight. */
    var onSave: suspend (ByteArray) -> File? = { unscripted("saveCard") }

    override suspend fun getCoverPairs(bookUuid: String): List<CoverPair> {
        coverPairCalls += bookUuid
        return coverPairs ?: awaitCancellation()
    }

    override suspend fun saveCard(png: ByteArray): File? {
        saved += png
        return onSave(png)
    }
}
