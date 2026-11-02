package com.omnibus.omnibus.data.bookprogress

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart

/**
 * In-memory [BookProgressRepository] for the ViewModel tests. Progress is live: set [progressFor] to emit, and each
 * collection is recorded. Chapters and sessions answer from scripted queues, then from empty successes.
 */
class FakeBookProgressRepository : BookProgressRepository {

    private val progress = mutableMapOf<String, MutableStateFlow<BookProgressResult?>>()

    /** The live progress for [bookUuid]. Set `value` to emit; null emits nothing. */
    fun progressFor(bookUuid: String): MutableStateFlow<BookProgressResult?> =
        progress.getOrPut(bookUuid) { MutableStateFlow(null) }

    /** The uuid of every collection of [getBookProgressFlow], in order. */
    val progressCollections = mutableListOf<String>()

    /** The next [getChapters] answers. When empty, an empty Success. */
    val chaptersResults = ArrayDeque<BookChaptersResult>()

    /** The uuid of every [getChapters] call, in call order. */
    val chaptersCalls = mutableListOf<String>()

    /** The next [getSessions] answers. When empty, Success with no sessions. */
    val sessionsResults = ArrayDeque<ReadingSessionsResult>()

    /** The uuid of every [getSessions] call, in call order. */
    val sessionsCalls = mutableListOf<String>()

    override fun getBookProgressFlow(bookUuid: String): Flow<BookProgressResult> =
        progressFor(bookUuid).onStart { progressCollections += bookUuid }.filterNotNull()

    override suspend fun getChapters(bookUuid: String): BookChaptersResult {
        chaptersCalls += bookUuid
        return chaptersResults.removeFirstOrNull() ?: BookChaptersResult.Success(emptyList(), emptyList(), null, null)
    }

    override suspend fun getSessions(bookUuid: String): ReadingSessionsResult {
        sessionsCalls += bookUuid
        return sessionsResults.removeFirstOrNull() ?: ReadingSessionsResult.Success(emptyList())
    }
}
