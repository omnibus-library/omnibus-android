package com.omnibus.omnibus.data.bookprogress

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.FakeClientInfo
import com.omnibus.omnibus.data.network.FakeAuthTokenProvider
import com.omnibus.omnibus.data.network.FakeServerBaseUrlProvider
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.data.progress.FakeProgressRepository
import com.omnibus.omnibus.data.progress.ReadingPosition
import com.omnibus.omnibus.feature.bookdetail.progress.BookChapter
import com.omnibus.omnibus.feature.bookdetail.progress.ReadingSession
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okhttp3.HttpUrl

private const val UUID = "book-1"
private const val PROGRESS = "/api/progress/book-1"
private const val ALIGNMENT = "/api/books/book-1/alignment"
private const val MANIFEST = "/api/audiobooks/book-1/manifest"
private const val SESSIONS = "/api/stats/sessions"

/** One epub record and one audio record, as the server writes them. */
private const val PROGRESS_BODY = """{"book_uuid":"book-1","records":[
    {"book_uuid":"book-1","format":"epub","epub_cfi":"y","progress_percent":40.0,"updated_at":100,"client_updated_at":100},
    {"book_uuid":"book-1","format":"audio","audio_position_seconds":60.0,"updated_at":90,"total_duration_seconds":600.0,"book_file_id":7}
  ],"furthest":"epub"}"""

private const val ALIGNMENT_ONE_FILE = """{"audio_files":[{"book_file_id":7,"duration_seconds":1000.0,"chapter_starts":[0.0,250.0]}]}"""

private fun reply(code: Int, body: String = ""): MockResponse = MockResponse.Builder().code(code).body(body).build()

/** A response whose connection drops before any byte of it arrives. */
private fun dropped(): MockResponse = MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()

/** A session page with one session and the cursor [next]. */
private fun sessionPage(next: String?): String {
    val cursor = next?.let { "\"$it\"" } ?: "null"
    return """{"entries":[{"book_uuid":"book-1","format":"epub","started_at":1,"ended_at":2,"seconds":1}],"next_before":$cursor}"""
}

/** Answers each path's requests from its own queue, in order. A request with nothing queued gets a 500. */
private class ScriptedDispatcher : Dispatcher() {
    private val steps = mutableMapOf<String, ArrayDeque<MockResponse>>()

    fun answer(path: String, response: MockResponse) {
        synchronized(steps) { steps.getOrPut(path) { ArrayDeque() }.addLast(response) }
    }

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        return synchronized(steps) { steps[path]?.removeFirstOrNull() } ?: reply(500, "unscripted $path")
    }
}

class DefaultBookProgressRepositoryTest : DescribeSpec({

    lateinit var server: MockWebServer
    lateinit var script: ScriptedDispatcher
    lateinit var device: FakeProgressRepository

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    beforeEach {
        script = ScriptedDispatcher()
        server = MockWebServer().apply {
            dispatcher = script
            start()
        }
        device = FakeProgressRepository()
    }

    afterEach { server.close() }

    /** A repository over [baseUrl], or with no server when it is null. */
    fun repositoryFor(baseUrl: HttpUrl? = server.url("/")) = DefaultBookProgressRepository(
        ioDispatcher = Dispatchers.Unconfined,
        omnibusHttpClient = OmnibusHttpClient(
            baseUrlProvider = FakeServerBaseUrlProvider(baseUrl = baseUrl),
            authTokenProvider = FakeAuthTokenProvider(token = "secret"),
            omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
            authClientMetadataInterceptor = AuthClientMetadataInterceptor(FakeClientInfo()),
        ),
        progressRepository = device,
    )

    /** The server's epub and audio records, as [PROGRESS_BODY] decodes to. */
    val serverReading = readingRecord(40.0, 100, cfi = "y")
    val serverListening = listeningRecord(60.0, 90, totalSeconds = 600.0, clientUpdatedAt = null)

    describe("the progress flow") {

        it("TC-52: local first, then the server") {
            device.stored[UUID] = ReadingPosition(UUID, cfi = "x", percent = 50, clientUpdatedAt = 200)
            script.answer(PROGRESS, reply(200, PROGRESS_BODY))

            val emitted = repositoryFor().getBookProgressFlow(UUID).toList()

            emitted shouldBe listOf(
                BookProgressResult.Success(listOf(readingRecord(50.0, 200, cfi = "x")), fromServer = false),
                BookProgressResult.Success(
                    listOf(readingRecord(50.0, 200, cfi = "x"), serverListening),
                    fromServer = true,
                ),
            )
            server.requestCount shouldBe 1
            val request = server.takeRequest()
            request.url.encodedPath shouldBe PROGRESS
            request.url.queryParameter("format") shouldBe null
        }

        it("TC-53: an unknown book and an unknown format") {
            script.answer(PROGRESS, reply(200, "null"))
            repositoryFor().getBookProgressFlow(UUID).toList() shouldBe listOf(
                BookProgressResult.Success(emptyList(), fromServer = false),
                BookProgressResult.Success(emptyList(), fromServer = true),
            )

            val withUnknown = """{"book_uuid":"book-1","records":[
                {"book_uuid":"book-1","format":"mobi","updated_at":5},
                {"book_uuid":"book-1","format":"epub","progress_percent":10.0,"updated_at":6,"client_updated_at":6}
              ]}"""
            script.answer(PROGRESS, reply(200, withUnknown))
            repositoryFor().getBookProgressFlow(UUID).toList().last() shouldBe
                BookProgressResult.Success(listOf(readingRecord(10.0, 6, cfi = null)), fromServer = true)
        }

        it("TC-55: a failure keeps the last known records") {
            script.answer(PROGRESS, reply(200, PROGRESS_BODY))
            script.answer(PROGRESS, reply(500))
            val repository = repositoryFor()
            repository.getBookProgressFlow(UUID).toList()

            val second = repository.getBookProgressFlow(UUID).toList()

            second.first() shouldBe BookProgressResult.Success(listOf(serverReading, serverListening), fromServer = false)
            second.last() shouldBe
                BookProgressResult.Failed(BookProgressError.Http(500), listOf(serverReading, serverListening))
        }

        it("TC-56: each progress failure maps to its error, after the device position") {
            val cases = listOf(
                reply(401) to BookProgressError.SessionExpired,
                reply(404) to BookProgressError.NotFound,
                reply(500) to BookProgressError.Http(500),
                dropped() to BookProgressError.Unreachable,
                reply(200, "{bad") to BookProgressError.UnexpectedResponse,
            )
            cases.forEach { (response, error) ->
                script.answer(PROGRESS, response)
                val emitted = repositoryFor().getBookProgressFlow(UUID).toList()

                emitted.first() shouldBe BookProgressResult.Success(emptyList(), fromServer = false)
                emitted.last() shouldBe BookProgressResult.Failed(error, emptyList())
            }
        }

        it("TC-57: no saved server and a blank uuid make no request") {
            device.stored[UUID] = ReadingPosition(UUID, cfi = "x", percent = 50, clientUpdatedAt = 200)
            val local = readingRecord(50.0, 200, cfi = "x")
            repositoryFor(baseUrl = null).getBookProgressFlow(UUID).toList() shouldBe listOf(
                BookProgressResult.Success(listOf(local), fromServer = false),
                BookProgressResult.Failed(BookProgressError.NoServer, listOf(local)),
            )
            server.requestCount shouldBe 0

            device.pendingLocal = CompletableDeferred()
            withTimeout(2_000) { repositoryFor().getBookProgressFlow(" ").toList() } shouldBe
                listOf(BookProgressResult.Failed(BookProgressError.NotFound, emptyList()))
            server.requestCount shouldBe 0
        }
    }

    describe("chapters") {

        it("TC-58: ebook chapters come from the alignment, in order") {
            script.answer(
                ALIGNMENT,
                reply(
                    200,
                    """{"ebook":{"chapters":[{"title":"One","percent":0.0},{"title":"","percent":12.5},{"title":"Three","percent":40.0}]},"audio_files":[]}""",
                ),
            )

            repositoryFor().getChapters(UUID) shouldBe BookChaptersResult.Success(
                ebookChapters = listOf(
                    BookChapter(0, "One", 0f),
                    BookChapter(1, null, .125f),
                    BookChapter(2, "Three", .4f),
                ),
                audioChapters = emptyList(),
                audioFileId = null,
                audioTotalSeconds = null,
            )
            server.requestCount shouldBe 1
        }

        it("TC-59: audio chapters come from the manifest of the listened file") {
            val alignment = """{"audio_files":[
                {"book_file_id":7,"duration_seconds":1000.0,"chapter_starts":[0.0,250.0]},
                {"book_file_id":8,"duration_seconds":2000.0,"chapter_starts":[0.0,500.0]}
              ],"listening":{"book_file_id":8}}"""
            val manifest = """{"mode":"file","book_file_id":8,"total_duration_seconds":2000.0,"chapters":[
                {"ordinal":1,"title":"A","start_seconds":0.0,"duration_seconds":500.0},
                {"ordinal":2,"title":"","start_seconds":500.0,"duration_seconds":1500.0}
              ]}"""
            script.answer(ALIGNMENT, reply(200, alignment))
            script.answer(MANIFEST, reply(200, manifest))

            val result = repositoryFor().getChapters(UUID) as BookChaptersResult.Success
            result.audioFileId shouldBe 8L
            result.audioTotalSeconds shouldBe 2000L
            result.audioChapters shouldBe listOf(BookChapter(0, "A", 0f), BookChapter(1, null, .25f))
            server.takeRequest().url.encodedPath shouldBe ALIGNMENT
            server.takeRequest().url.queryParameter("file_id") shouldBe "8"

            script.answer(ALIGNMENT, reply(200, """{"audio_files":[{"book_file_id":7,"duration_seconds":1000.0,"chapter_starts":[0.0,250.0]},{"book_file_id":8,"duration_seconds":2000.0,"chapter_starts":[0.0,500.0]}]}"""))
            script.answer(MANIFEST, reply(200, """{"mode":"file","book_file_id":7,"total_duration_seconds":1000.0,"chapters":[{"title":"","start_seconds":0.0,"duration_seconds":250.0},{"title":"","start_seconds":250.0,"duration_seconds":750.0}]}"""))
            val firstFile = repositoryFor().getChapters(UUID) as BookChaptersResult.Success
            firstFile.audioFileId shouldBe 7L
            server.takeRequest()
            server.takeRequest().url.queryParameter("file_id") shouldBe "7"
        }

        it("TC-60: a manifest failure falls back to the alignment's file") {
            listOf(reply(404), reply(500), dropped(), reply(200, """{"mode":"hls","playlist_url":"https://x/y.m3u8","parts":[]}""")).forEach { manifest ->
                script.answer(ALIGNMENT, reply(200, ALIGNMENT_ONE_FILE))
                script.answer(MANIFEST, manifest)

                repositoryFor().getChapters(UUID) shouldBe BookChaptersResult.Success(
                    ebookChapters = emptyList(),
                    audioChapters = listOf(BookChapter(0, null, 0f), BookChapter(1, null, .25f)),
                    audioFileId = 7L,
                    audioTotalSeconds = 1000L,
                )
            }
        }

        it("TC-61: chapter errors map to their error, and a blank uuid makes no request") {
            val cases = listOf(
                reply(404) to BookProgressError.NotFound,
                reply(401) to BookProgressError.SessionExpired,
                dropped() to BookProgressError.Unreachable,
                reply(200, "{bad") to BookProgressError.UnexpectedResponse,
            )
            cases.forEach { (response, error) ->
                script.answer(ALIGNMENT, response)
                repositoryFor().getChapters(UUID) shouldBe BookChaptersResult.Failed(error)
            }
            repositoryFor(baseUrl = null).getChapters(UUID) shouldBe BookChaptersResult.Failed(BookProgressError.NoServer)
            repositoryFor().getChapters(" ") shouldBe BookChaptersResult.Failed(BookProgressError.NotFound)
            server.requestCount shouldBe cases.size
        }
    }

    describe("sessions") {

        it("TC-62: a single page maps each session and keeps the server's order") {
            script.answer(
                SESSIONS,
                reply(
                    200,
                    """{"entries":[
                        {"book_uuid":"book-1","format":"epub","started_at":1000,"ended_at":1100,"seconds":100},
                        {"book_uuid":"book-1","format":"audio","started_at":500,"ended_at":600,"seconds":-5}
                      ],"next_before":null}""",
                ),
            )

            repositoryFor().getSessions(UUID) shouldBe ReadingSessionsResult.Success(
                listOf(
                    ReadingSession(1_000_000L, 100L, null, null),
                    ReadingSession(500_000L, 0L, null, null),
                ),
            )
            val request = server.takeRequest()
            request.url.encodedPath shouldBe SESSIONS
            request.url.queryParameter("book") shouldBe UUID
            request.url.queryParameter("limit") shouldBe "100"
            request.url.queryParameter("before") shouldBe null
        }

        it("TC-63: pages follow next_before, and reading stops at 12 pages") {
            script.answer(SESSIONS, reply(200, sessionPage("100:book-1")))
            script.answer(SESSIONS, reply(200, sessionPage("50:book-1")))
            script.answer(SESSIONS, reply(200, sessionPage(null)))
            val chained = repositoryFor().getSessions(UUID) as ReadingSessionsResult.Success
            chained.sessions.size shouldBe 3
            server.takeRequest().url.queryParameter("before") shouldBe null
            server.takeRequest().url.queryParameter("before") shouldBe "100:book-1"
            server.takeRequest().url.queryParameter("before") shouldBe "50:book-1"

            repeat(12) { script.answer(SESSIONS, reply(200, sessionPage("1:book-1"))) }
            val capped = repositoryFor().getSessions(UUID) as ReadingSessionsResult.Success
            capped.sessions.size shouldBe 12
            server.requestCount shouldBe 15
        }

        it("TC-65: any failing session page fails the whole read") {
            script.answer(SESSIONS, reply(200, sessionPage("100:book-1")))
            script.answer(SESSIONS, reply(500))
            repositoryFor().getSessions(UUID) shouldBe ReadingSessionsResult.Failed(BookProgressError.Http(500))

            val firstPageFailures = listOf(
                reply(401) to BookProgressError.SessionExpired,
                reply(404) to BookProgressError.NotFound,
                dropped() to BookProgressError.Unreachable,
                reply(200, "{bad") to BookProgressError.UnexpectedResponse,
            )
            firstPageFailures.forEach { (response, error) ->
                script.answer(SESSIONS, response)
                repositoryFor().getSessions(UUID) shouldBe ReadingSessionsResult.Failed(error)
            }
            repositoryFor(baseUrl = null).getSessions(UUID) shouldBe ReadingSessionsResult.Failed(BookProgressError.NoServer)
            repositoryFor().getSessions(" ") shouldBe ReadingSessionsResult.Failed(BookProgressError.NotFound)
        }
    }
})
