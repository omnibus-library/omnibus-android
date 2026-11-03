package com.omnibus.omnibus.data.suggestions

import kotlinx.coroutines.CompletableDeferred

/** A [SuggestionsRepository] that answers from a queue, repeating its last answer once the queue is empty, and records every call. */
class FakeSuggestionsRepository(vararg answers: SuggestionsResult) : SuggestionsRepository {

    private val queue = ArrayDeque(answers.toList())
    private var last: SuggestionsResult = SuggestionsResult.Failure(SuggestionsError.Unreachable)

    /** The clock stamped on each read; tests set `fake.now = { testScheduler.currentTime }`. */
    var now: () -> Long = { 0L }

    /** When set, each read suspends on it before answering. */
    var gate: CompletableDeferred<Unit>? = null

    /** The [now] of each read, in order. */
    val requestTimes = mutableListOf<Long>()

    /** How many reads were made. */
    val requests: Int get() = requestTimes.size

    /** The book uuids read, in order. */
    val requestedUuids = mutableListOf<String>()

    /** Answers [getCover] for a path with no entry in [coverResults]. */
    var coverResult: SuggestionCoverResult = SuggestionCoverResult.NotFound

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
        return last
    }

    override suspend fun getCover(coverPath: String): SuggestionCoverResult {
        coverPaths += coverPath
        return coverResults[coverPath] ?: coverResult
    }
}

/** A successful read whose answer is [SuggestionsAnswer.Pending]. */
val pending: SuggestionsResult = SuggestionsResult.Success(SuggestionsAnswer.Pending)

/** A successful read whose answer is a [SuggestionsAnswer.Ready] of [items], in that order. */
fun ready(vararg items: BookSuggestion): SuggestionsResult =
    SuggestionsResult.Success(SuggestionsAnswer.Ready(items.toList()))

/** A suggestion for test data. The URL defaults to a safe Hardcover page for [id]. */
fun suggestion(
    id: Long,
    url: String = "https://hardcover.app/books/b$id",
    title: String = "Book $id",
    author: String = "Author $id",
    coverPath: String? = null,
): BookSuggestion = BookSuggestion(id, url, title, author, coverPath)
