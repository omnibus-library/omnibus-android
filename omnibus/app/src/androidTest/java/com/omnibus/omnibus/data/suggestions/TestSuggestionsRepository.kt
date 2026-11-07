package com.omnibus.omnibus.data.suggestions

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The on-device copy of the JVM fake. It answers from a queue, repeating its last answer once the queue is empty, and
 * records every call. Calls arrive from the main thread (sections) and Coil's dispatcher (covers), so it is thread-safe.
 */
class TestSuggestionsRepository(vararg answers: SuggestionsResult) : SuggestionsRepository {

    private val queue = ArrayDeque(answers.toList())
    private var last: SuggestionsResult = SuggestionsResult.Failure(SuggestionsError.Unreachable)

    /** The clock stamped on each read. Unused on device, kept so the two fakes share one API. */
    @Volatile
    var now: () -> Long = { 0L }

    /** When set, each read suspends on it before answering. */
    @Volatile
    var gate: CompletableDeferred<Unit>? = null

    /** The [now] of each read, in order. */
    val requestTimes: MutableList<Long> = CopyOnWriteArrayList()

    /** How many reads were made. */
    val requests: Int get() = requestTimes.size

    /** The book uuids read, in order. */
    val requestedUuids: MutableList<String> = CopyOnWriteArrayList()

    /** Answers [getCover] for a path with no entry in [coverResults]. */
    @Volatile
    var coverResult: SuggestionCoverResult = SuggestionCoverResult.NotFound

    /** Per-path cover answers. */
    val coverResults: MutableMap<String, SuggestionCoverResult> = ConcurrentHashMap()

    /** The cover paths asked for, in order. */
    val coverPaths: MutableList<String> = CopyOnWriteArrayList()

    /** Adds [results] to the end of the queue. */
    fun enqueue(vararg results: SuggestionsResult) {
        synchronized(this) { queue.addAll(results) }
    }

    override suspend fun getSuggestions(bookUuid: String): SuggestionsResult {
        requestedUuids += bookUuid
        requestTimes += now()
        gate?.await()
        return synchronized(this) {
            queue.removeFirstOrNull()?.let { last = it }
            last
        }
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
