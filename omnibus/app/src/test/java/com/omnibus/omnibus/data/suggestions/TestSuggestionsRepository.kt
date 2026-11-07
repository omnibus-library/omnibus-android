package com.omnibus.omnibus.data.suggestions

import com.omnibus.omnibus.testing.randomLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation

/** A [SuggestionsRepository] that answers from a queue, repeating its last answer once the queue is empty, and records every call. With nothing queued, a read never answers. */
class TestSuggestionsRepository(vararg answers: SuggestionsResult) : SuggestionsRepository {

    private val queue = ArrayDeque(answers.toList())
    private var last: SuggestionsResult? = null

    /** The clock stamped on each read; tests set `fake.now = { testScheduler.currentTime }`. */
    var now: () -> Long = { randomLong() }

    /** When set, each read suspends on it before answering. */
    var gate: CompletableDeferred<Unit>? = null

    /** The [now] of each read, in order. */
    val requestTimes = mutableListOf<Long>()

    /** How many reads were made. */
    val requests: Int get() = requestTimes.size

    /** The book uuids read, in order. */
    val requestedUuids = mutableListOf<String>()

    /** Answers [getCover] for a path with no entry in [coverResults]. */
    var coverResult: SuggestionCoverResult? = null

    /** Per-path cover answers. */
    val coverResults = mutableMapOf<String, SuggestionCoverResult>()

    /** The cover paths asked for, in order. */
    val coverPaths = mutableListOf<String>()

    /** Adds [results] to the end of the queue. */
    fun enqueue(vararg results: SuggestionsResult) {
        queue.addAll(results)
    }

    override suspend fun getSuggestions(bookUuid: String): SuggestionsResult {
        requestedUuids += bookUuid
        requestTimes += now()
        gate?.await()
        queue.removeFirstOrNull()?.let { last = it }
        return last ?: awaitCancellation()
    }

    override suspend fun getCover(coverPath: String): SuggestionCoverResult {
        coverPaths += coverPath
        return coverResults[coverPath] ?: coverResult ?: awaitCancellation()
    }
}

/** A successful read whose answer is [SuggestionsAnswer.Pending]. */
val pending: SuggestionsResult = SuggestionsResult.Success(SuggestionsAnswer.Pending)

/** A successful read whose answer is a [SuggestionsAnswer.Ready] of [items], in that order. */
fun ready(vararg items: BookSuggestion): SuggestionsResult =
    SuggestionsResult.Success(SuggestionsAnswer.Ready(items.toList()))
