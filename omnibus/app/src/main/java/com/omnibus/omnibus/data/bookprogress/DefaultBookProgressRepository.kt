package com.omnibus.omnibus.data.bookprogress

import android.util.Log
import com.omnibus.omnibus.data.bookprogress.remote.AlignmentDto
import com.omnibus.omnibus.data.bookprogress.remote.AudiobookManifestDto
import com.omnibus.omnibus.data.bookprogress.remote.BookProgressJson
import com.omnibus.omnibus.data.bookprogress.remote.BookProgressRecordsDto
import com.omnibus.omnibus.data.bookprogress.remote.SessionLogPageDto
import com.omnibus.omnibus.data.library.ProgressRecord
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.progress.ProgressRepository
import com.omnibus.omnibus.di.IoDispatcher
import com.omnibus.omnibus.feature.bookdetail.progress.BookChapter
import com.omnibus.omnibus.feature.bookdetail.progress.ReadingSession
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Default [BookProgressRepository]. The progress flow paints from the device's own position, then the server's records
 * merged with it. The last server answer for each book is kept in memory, so a failed read still shows the known place.
 * Every failure becomes a result, so no exception leaves this class except cancellation.
 */
@Singleton
class DefaultBookProgressRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val omnibusHttpClient: OmnibusHttpClient,
    private val progressRepository: ProgressRepository,
) : BookProgressRepository {

    /** The last server records seen for each book uuid, so a failed read can still show them. */
    private val lastServerRecords = ConcurrentHashMap<String, List<ProgressRecord>>()

    override fun getBookProgressFlow(bookUuid: String): Flow<BookProgressResult> = flow {
        if (bookUuid.isBlank()) {
            emit(BookProgressResult.Failed(BookProgressError.NotFound, emptyList()))
            return@flow
        }
        val local = progressRepository.getLocalPosition(bookUuid)
        emit(BookProgressResult.Success(mergeProgressRecords(local, lastServerRecords[bookUuid].orEmpty()), fromServer = false))
        when (val answer = fetch(PROGRESS_PATH, listOf(bookUuid)) { body ->
            BookProgressJson.decodeFromString<BookProgressRecordsDto?>(body)?.records.orEmpty().mapNotNull { it.toDomain() }
        }) {
            is Fetch.Ok -> {
                lastServerRecords[bookUuid] = answer.value
                emit(BookProgressResult.Success(mergeProgressRecords(local, answer.value), fromServer = true))
            }
            is Fetch.Err -> emit(
                BookProgressResult.Failed(answer.error, mergeProgressRecords(local, lastServerRecords[bookUuid].orEmpty())),
            )
        }
    }.flowOn(ioDispatcher)

    override suspend fun getChapters(bookUuid: String): BookChaptersResult {
        if (bookUuid.isBlank()) return BookChaptersResult.Failed(BookProgressError.NotFound)
        val alignment = when (val answer = fetch(BOOKS_PATH, listOf(bookUuid, ALIGNMENT_SEGMENT)) {
            BookProgressJson.decodeFromString<AlignmentDto>(it)
        }) {
            is Fetch.Ok -> answer.value
            is Fetch.Err -> return BookChaptersResult.Failed(answer.error)
        }
        val ebookChapters = alignment.ebook?.chapters.orEmpty().mapIndexed { index, chapter ->
            BookChapter(index, chapter.title.ifBlank { null }, (chapter.percent / 100).toFloat())
        }
        val audioFiles = alignment.audioFiles
        if (audioFiles.isEmpty()) return BookChaptersResult.Success(ebookChapters, emptyList(), null, null)

        val audioFileId = alignment.listening?.bookFileId ?: audioFiles.first().bookFileId
        val file = audioFiles.firstOrNull { it.bookFileId == audioFileId } ?: audioFiles.first()
        // A manifest failure is not an error here: the alignment's own file still gives the total and the chapters.
        val manifest = when (val answer = fetch(
            AUDIOBOOKS_PATH,
            listOf(bookUuid, MANIFEST_SEGMENT),
            listOf(FILE_ID_QUERY to audioFileId.toString()),
        ) { BookProgressJson.decodeFromString<AudiobookManifestDto>(it) }) {
            is Fetch.Ok -> answer.value
            is Fetch.Err -> null
        }

        val manifestTotal = manifest?.totalDurationSeconds?.takeIf { it > 0 }
        val manifestChapters = manifest?.chapters.orEmpty()
        val audioChapters = when {
            manifestTotal != null && manifestChapters.isNotEmpty() -> manifestChapters.mapIndexed { index, chapter ->
                BookChapter(index, chapter.title.ifBlank { null }, (chapter.startSeconds / manifestTotal).toFloat())
            }
            file.durationSeconds > 0 -> file.chapterStarts.mapIndexed { index, start ->
                BookChapter(index, null, (start / file.durationSeconds).toFloat())
            }
            else -> emptyList()
        }
        val audioTotal = manifestTotal ?: file.durationSeconds.takeIf { it > 0 }
        return BookChaptersResult.Success(ebookChapters, audioChapters, audioFileId, audioTotal?.toLong())
    }

    override suspend fun getSessions(bookUuid: String): ReadingSessionsResult {
        if (bookUuid.isBlank()) return ReadingSessionsResult.Failed(BookProgressError.NotFound)
        val sessions = mutableListOf<ReadingSession>()
        var cursor: String? = null
        repeat(MAX_SESSION_PAGES) {
            val query = buildList {
                add(BOOK_QUERY to bookUuid)
                add(LIMIT_QUERY to SESSION_PAGE_LIMIT.toString())
                cursor?.let { add(BEFORE_QUERY to it) }
            }
            val page = when (val answer = fetch(SESSIONS_PATH, emptyList(), query) {
                BookProgressJson.decodeFromString<SessionLogPageDto>(it)
            }) {
                is Fetch.Ok -> answer.value
                is Fetch.Err -> return ReadingSessionsResult.Failed(answer.error)
            }
            sessions += page.entries.map { it.toDomain() }
            cursor = page.nextBefore ?: return ReadingSessionsResult.Success(sessions)
        }
        return ReadingSessionsResult.Success(sessions)
    }

    /** The outcome of one GET: the decoded value, or the error it failed with. */
    private sealed interface Fetch<out T> {
        data class Ok<T>(val value: T) : Fetch<T>
        data class Err(val error: BookProgressError) : Fetch<Nothing>
    }

    /** One GET of [path] with [segments] added to it and [query] parameters, decoded by [decode]. */
    private suspend fun <T> fetch(
        path: String,
        segments: List<String>,
        query: List<Pair<String, String>> = emptyList(),
        decode: (String) -> T,
    ): Fetch<T> = withContext(ioDispatcher) {
        val builder = omnibusHttpClient.request(path) ?: return@withContext Fetch.Err(BookProgressError.NoServer)
        val url = builder.build().url.newBuilder().apply {
            segments.forEach { addPathSegment(it) }
            query.forEach { (name, value) -> addQueryParameter(name, value) }
        }.build()
        try {
            omnibusHttpClient.execute(builder.url(url).get().build()).use { response ->
                when {
                    response.isSuccessful -> Fetch.Ok(decode(response.body.string()))
                    response.code == HTTP_UNAUTHORIZED -> Fetch.Err(BookProgressError.SessionExpired)
                    response.code == HTTP_NOT_FOUND -> Fetch.Err(BookProgressError.NotFound)
                    else -> Fetch.Err(BookProgressError.Http(response.code))
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server to read $path", ex)
            Fetch.Err(BookProgressError.Unreachable)
        } catch (ex: IllegalArgumentException) {
            // SerializationException is a subtype, so a body that doesn't decode lands here too.
            Log.w(TAG, "Unexpected response to a read of $path", ex)
            Fetch.Err(BookProgressError.UnexpectedResponse)
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure reading $path", ex)
            Fetch.Err(BookProgressError.UnexpectedResponse)
        }
    }

    internal companion object {
        const val TAG = "DefaultBookProgressRepository"

        /** Relative, so it resolves beneath the server's base URL. */
        const val PROGRESS_PATH = "api/progress"
        const val BOOKS_PATH = "api/books"
        const val AUDIOBOOKS_PATH = "api/audiobooks"
        const val SESSIONS_PATH = "api/stats/sessions"
        const val ALIGNMENT_SEGMENT = "alignment"
        const val MANIFEST_SEGMENT = "manifest"

        const val BOOK_QUERY = "book"
        const val LIMIT_QUERY = "limit"
        const val BEFORE_QUERY = "before"
        const val FILE_ID_QUERY = "file_id"

        /** The server's page limit for session reads, the most it accepts. */
        const val SESSION_PAGE_LIMIT = 100

        /** The most session pages read, as iOS does (12 pages of 100). */
        const val MAX_SESSION_PAGES = 12

        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_NOT_FOUND = 404
    }
}
