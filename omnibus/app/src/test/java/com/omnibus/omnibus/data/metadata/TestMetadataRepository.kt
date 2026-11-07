package com.omnibus.omnibus.data.metadata

import com.omnibus.omnibus.testing.unscripted
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation

/** In-memory [MetadataRepository] for tests. Records every call and answers from scripts. */
class TestMetadataRepository : MetadataRepository {

    /** One call to [replaceList]. */
    data class Write(val bookUuid: String, val kind: MetadataListKind, val values: List<String>)

    /** Every [replaceList] call, in order. */
    val writes = mutableListOf<Write>()

    /** Every [getVocabulary] call, in order. */
    val vocabularyRequests = mutableListOf<MetadataListKind>()

    /** Answers a write that is not held. */
    var writeResult: (Write) -> MetadataWriteResult = { unscripted("replaceList") }

    /** The answer to [getVocabulary] per kind. A kind not set never answers. */
    val vocabulary = mutableMapOf<MetadataListKind, VocabularyResult>()

    private val writeHolds = ArrayDeque<CompletableDeferred<MetadataWriteResult>>()
    private val vocabularyHolds = mutableMapOf<MetadataListKind, CompletableDeferred<VocabularyResult>>()

    /** Holds the answer of the next [replaceList] call until the test completes the returned deferred. */
    fun holdWrite(): CompletableDeferred<MetadataWriteResult> =
        CompletableDeferred<MetadataWriteResult>().also { writeHolds.addLast(it) }

    /** Holds the answer of the next [getVocabulary] call for [kind] until the test completes the returned deferred. */
    fun holdVocabulary(kind: MetadataListKind): CompletableDeferred<VocabularyResult> =
        CompletableDeferred<VocabularyResult>().also { vocabularyHolds[kind] = it }

    override suspend fun replaceList(
        bookUuid: String,
        kind: MetadataListKind,
        values: List<String>,
    ): MetadataWriteResult {
        val write = Write(bookUuid, kind, values)
        writes += write
        writeHolds.removeFirstOrNull()?.let { return it.await() }
        return writeResult(write)
    }

    override suspend fun getVocabulary(kind: MetadataListKind): VocabularyResult {
        vocabularyRequests += kind
        vocabularyHolds.remove(kind)?.let { return it.await() }
        return vocabulary[kind] ?: awaitCancellation()
    }
}
