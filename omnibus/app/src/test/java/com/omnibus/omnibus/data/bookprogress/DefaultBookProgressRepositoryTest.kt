package com.omnibus.omnibus.data.bookprogress

import app.cash.turbine.test
import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.TestClientInfo
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.data.network.TestAuthTokenProvider
import com.omnibus.omnibus.data.network.TestServerBaseUrlProvider
import com.omnibus.omnibus.data.progress.ProgressRepository
import com.omnibus.omnibus.data.progress.ReadingPosition
import com.omnibus.omnibus.data.progress.TestProgressRepository
import com.omnibus.omnibus.feature.bookdetail.progress.BookChapter
import com.omnibus.omnibus.feature.bookdetail.progress.ReadingSession
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okhttp3.HttpUrl

private const val SESSIONS = "/api/stats/sessions"

private fun progressPath(uuid: String) = "/api/progress/$uuid"

private fun alignmentPath(uuid: String) = "/api/books/$uuid/alignment"

private fun manifestPath(uuid: String) = "/api/audiobooks/$uuid/manifest"

/** One epub record and one audio record, as the server writes them. */
private fun progressBody(uuid: String) = """{"book_uuid":"$uuid","records":[
    {"book_uuid":"$uuid","format":"epub","epub_cfi":"y","progress_percent":40.0,"updated_at":100,"client_updated_at":100},
    {"book_uuid":"$uuid","format":"audio","audio_position_seconds":60.0,"updated_at":90,"total_duration_seconds":600.0,"book_file_id":7}
  ],"furthest":"epub"}"""

/** The epub record [progressBody] decodes to. */
private fun serverReading(uuid: String) = readingRecord(40.0, 100, cfi = "y", uuid = uuid)

/** The audio record [progressBody] decodes to. */
private fun serverListening(uuid: String) =
    listeningRecord(60.0, 90, totalSeconds = 600.0, clientUpdatedAt = null, uuid = uuid)

private const val ALIGNMENT_ONE_FILE = """{"audio_files":[{"book_file_id":7,"duration_seconds":1000.0,"chapter_starts":[0.0,250.0]}]}"""

private const val ALIGNMENT_TWO_FILES = """{"book_file_id":7,"duration_seconds":1000.0,"chapter_starts":[0.0,250.0]},
    {"book_file_id":8,"duration_seconds":2000.0,"chapter_starts":[0.0,500.0]}"""

private fun reply(code: Int, body: String = ""): MockResponse = MockResponse.Builder().code(code).body(body).build()

/** A response whose connection drops before any byte of it arrives. */
private fun dropped(): MockResponse = MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()

/** A session page with one session and the cursor [next]. */
private fun sessionPage(uuid: String, next: String?): String {
    val cursor = next?.let { "\"$it\"" } ?: "null"
    return """{"entries":[{"book_uuid":"$uuid","format":"epub","started_at":1,"ended_at":2,"seconds":1}],"next_before":$cursor}"""
}

/** Every failure a read maps to an error of its own. */
private fun readFailures(): List<Pair<MockResponse, BookProgressError>> = listOf(
    reply(401) to BookProgressError.SessionExpired,
    reply(404) to BookProgressError.NotFound,
    reply(500) to BookProgressError.Http(500),
    dropped() to BookProgressError.Unreachable,
    reply(200, "{bad") to BookProgressError.UnexpectedResponse,
)

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

private inline fun <T> withServer(block: (MockWebServer, ScriptedDispatcher) -> T): T {
    val script = ScriptedDispatcher()
    return MockWebServer().use { server ->
        server.dispatcher = script
        server.start()
        block(server, script)
    }
}

class DefaultBookProgressRepositoryTest : DescribeSpec({

    fun sut(
        baseUrl: HttpUrl? = null,
        device: ProgressRepository = TestProgressRepository(),
    ): DefaultBookProgressRepository = DefaultBookProgressRepository(
        ioDispatcher = Dispatchers.Unconfined,
        omnibusHttpClient = OmnibusHttpClient(
            baseUrlProvider = TestServerBaseUrlProvider(baseUrl),
            authTokenProvider = TestAuthTokenProvider(),
            omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
            authClientMetadataInterceptor = AuthClientMetadataInterceptor(TestClientInfo()),
        ),
        progressRepository = device,
    )

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("observing a book's progress") {
        context("when the device has a position") {
            it("TC-52: emits the device position, then the server's records merged with it") {
                withServer { server, script ->
                    val uuid = randomString()
                    val device = TestProgressRepository().apply {
                        stored[uuid] = ReadingPosition(uuid, cfi = "x", percent = 50, clientUpdatedAt = 200)
                    }
                    val local = readingRecord(50.0, 200, cfi = "x", uuid = uuid)
                    script.answer(progressPath(uuid), reply(200, progressBody(uuid)))

                    sut(server.url("/"), device).getBookProgressFlow(uuid).test {
                        awaitItem() shouldBe BookProgressResult.Success(listOf(local), fromServer = false)
                        awaitItem() shouldBe
                            BookProgressResult.Success(listOf(local, serverListening(uuid)), fromServer = true)
                        awaitComplete()
                    }
                }
            }

            context("when no server is saved") {
                it("TC-57: reports NoServer with the device position") {
                    val uuid = randomString()
                    val device = TestProgressRepository().apply {
                        stored[uuid] = ReadingPosition(uuid, cfi = "x", percent = 50, clientUpdatedAt = 200)
                    }

                    sut(device = device).getBookProgressFlow(uuid).last() shouldBe BookProgressResult.Failed(
                        BookProgressError.NoServer,
                        listOf(readingRecord(50.0, 200, cfi = "x", uuid = uuid)),
                    )
                }
            }
        }

        it("TC-52: reads api/progress/{uuid} for every format") {
            withServer { server, script ->
                val uuid = randomString()
                script.answer(progressPath(uuid), reply(200, progressBody(uuid)))

                sut(server.url("/")).getBookProgressFlow(uuid).toList()

                val request = server.takeRequest()
                request.url.encodedPath shouldBe progressPath(uuid)
                request.url.queryParameter("format") shouldBe null
            }
        }

        context("when the server has no record of the book") {
            it("TC-53: gives no records") {
                withServer { server, script ->
                    val uuid = randomString()
                    script.answer(progressPath(uuid), reply(200, "null"))

                    sut(server.url("/")).getBookProgressFlow(uuid).last() shouldBe
                        BookProgressResult.Success(emptyList(), fromServer = true)
                }
            }
        }

        context("when a record has a format the app doesn't know") {
            it("TC-53: drops that record") {
                withServer { server, script ->
                    val uuid = randomString()
                    val body = """{"book_uuid":"$uuid","records":[
                        {"book_uuid":"$uuid","format":"mobi","updated_at":5},
                        {"book_uuid":"$uuid","format":"epub","progress_percent":10.0,"updated_at":6,"client_updated_at":6}
                      ]}"""
                    script.answer(progressPath(uuid), reply(200, body))

                    sut(server.url("/")).getBookProgressFlow(uuid).last() shouldBe BookProgressResult.Success(
                        listOf(readingRecord(10.0, 6, cfi = null, uuid = uuid)),
                        fromServer = true,
                    )
                }
            }
        }

        context("when an earlier read succeeded") {
            it("TC-55: starts from the last known records") {
                withServer { server, script ->
                    val uuid = randomString()
                    val repository = sut(server.url("/"))
                    script.answer(progressPath(uuid), reply(200, progressBody(uuid)))
                    repository.getBookProgressFlow(uuid).toList()

                    repository.getBookProgressFlow(uuid).first() shouldBe BookProgressResult.Success(
                        listOf(serverReading(uuid), serverListening(uuid)),
                        fromServer = false,
                    )
                }
            }

            context("when the next read fails") {
                it("TC-55: reports the failure with the last known records") {
                    withServer { server, script ->
                        val uuid = randomString()
                        val repository = sut(server.url("/"))
                        script.answer(progressPath(uuid), reply(200, progressBody(uuid)))
                        script.answer(progressPath(uuid), reply(500))
                        repository.getBookProgressFlow(uuid).toList()

                        repository.getBookProgressFlow(uuid).last() shouldBe BookProgressResult.Failed(
                            BookProgressError.Http(500),
                            listOf(serverReading(uuid), serverListening(uuid)),
                        )
                    }
                }
            }
        }

        context("when the read fails") {
            it("TC-56: maps each status and unusable body to its error") {
                withServer { server, script ->
                    checkAll(Exhaustive.collection(readFailures())) { (response, error) ->
                        val uuid = randomString()
                        script.answer(progressPath(uuid), response)

                        sut(server.url("/")).getBookProgressFlow(uuid).last() shouldBe
                            BookProgressResult.Failed(error, emptyList())
                    }
                }
            }
        }

        context("when the uuid is blank") {
            it("TC-57: emits only NotFound") {
                sut().getBookProgressFlow(" ").test {
                    awaitItem() shouldBe BookProgressResult.Failed(BookProgressError.NotFound, emptyList())
                    awaitComplete()
                }
            }
        }
    }

    describe("reading a book's chapters") {
        context("when the book has only an ebook") {
            it("TC-58: gives the alignment's chapters in order, with blank titles as null") {
                withServer { server, script ->
                    val uuid = randomString()
                    script.answer(
                        alignmentPath(uuid),
                        reply(
                            200,
                            """{"ebook":{"chapters":[{"title":"One","percent":0.0},{"title":"","percent":12.5},{"title":"Three","percent":40.0}]},"audio_files":[]}""",
                        ),
                    )

                    sut(server.url("/")).getChapters(uuid) shouldBe BookChaptersResult.Success(
                        ebookChapters = listOf(
                            BookChapter(0, "One", 0f),
                            BookChapter(1, null, .125f),
                            BookChapter(2, "Three", .4f),
                        ),
                        audioChapters = emptyList(),
                        audioFileId = null,
                        audioTotalSeconds = null,
                    )
                }
            }
        }

        context("when the alignment names a listened file") {
            it("TC-59: gives the audio chapters from that file's manifest") {
                withServer { server, script ->
                    val uuid = randomString()
                    script.answer(
                        alignmentPath(uuid),
                        reply(200, """{"audio_files":[$ALIGNMENT_TWO_FILES],"listening":{"book_file_id":8}}"""),
                    )
                    script.answer(
                        manifestPath(uuid),
                        reply(
                            200,
                            """{"mode":"file","book_file_id":8,"total_duration_seconds":2000.0,"chapters":[
                                {"ordinal":1,"title":"A","start_seconds":0.0,"duration_seconds":500.0},
                                {"ordinal":2,"title":"","start_seconds":500.0,"duration_seconds":1500.0}
                              ]}""",
                        ),
                    )

                    sut(server.url("/")).getChapters(uuid) shouldBe BookChaptersResult.Success(
                        ebookChapters = emptyList(),
                        audioChapters = listOf(BookChapter(0, "A", 0f), BookChapter(1, null, .25f)),
                        audioFileId = 8L,
                        audioTotalSeconds = 2000L,
                    )
                    server.takeRequest()
                    server.takeRequest().url.queryParameter("file_id") shouldBe "8"
                }
            }
        }

        context("when the alignment names no listened file") {
            it("TC-59: reads the manifest of the first file") {
                withServer { server, script ->
                    val uuid = randomString()
                    script.answer(alignmentPath(uuid), reply(200, """{"audio_files":[$ALIGNMENT_TWO_FILES]}"""))
                    script.answer(manifestPath(uuid), reply(404))

                    sut(server.url("/")).getChapters(uuid)

                    server.takeRequest()
                    server.takeRequest().url.queryParameter("file_id") shouldBe "7"
                }
            }
        }

        context("when the manifest gives no usable chapters") {
            it("TC-60: falls back to the alignment's file for each failure and chapterless manifest") {
                withServer { server, script ->
                    val manifests = listOf(
                        reply(404),
                        reply(500),
                        dropped(),
                        reply(200, """{"mode":"hls","playlist_url":"https://x/y.m3u8","parts":[]}"""),
                    )
                    checkAll(Exhaustive.collection(manifests)) { manifest ->
                        val uuid = randomString()
                        script.answer(alignmentPath(uuid), reply(200, ALIGNMENT_ONE_FILE))
                        script.answer(manifestPath(uuid), manifest)

                        sut(server.url("/")).getChapters(uuid) shouldBe BookChaptersResult.Success(
                            ebookChapters = emptyList(),
                            audioChapters = listOf(BookChapter(0, null, 0f), BookChapter(1, null, .25f)),
                            audioFileId = 7L,
                            audioTotalSeconds = 1000L,
                        )
                    }
                }
            }
        }

        context("when the alignment read fails") {
            it("TC-61: maps each status and unusable body to its error") {
                withServer { server, script ->
                    checkAll(Exhaustive.collection(readFailures())) { (response, error) ->
                        val uuid = randomString()
                        script.answer(alignmentPath(uuid), response)

                        sut(server.url("/")).getChapters(uuid) shouldBe BookChaptersResult.Failed(error)
                    }
                }
            }
        }

        context("when no server is saved") {
            it("TC-61: fails with NoServer") {
                sut().getChapters(randomString()) shouldBe BookChaptersResult.Failed(BookProgressError.NoServer)
            }
        }

        context("when the uuid is blank") {
            it("TC-61: fails with NotFound") {
                sut().getChapters(" ") shouldBe BookChaptersResult.Failed(BookProgressError.NotFound)
            }
        }
    }

    describe("reading a book's sessions") {
        context("when the server has one page") {
            it("TC-62: maps each session in the server's order, with negative seconds as zero") {
                withServer { server, script ->
                    val uuid = randomString()
                    script.answer(
                        SESSIONS,
                        reply(
                            200,
                            """{"entries":[
                                {"book_uuid":"$uuid","format":"epub","started_at":1000,"ended_at":1100,"seconds":100},
                                {"book_uuid":"$uuid","format":"audio","started_at":500,"ended_at":600,"seconds":-5}
                              ],"next_before":null}""",
                        ),
                    )

                    sut(server.url("/")).getSessions(uuid) shouldBe ReadingSessionsResult.Success(
                        listOf(
                            ReadingSession(1_000_000L, 100L, null, null),
                            ReadingSession(500_000L, 0L, null, null),
                        ),
                    )
                }
            }

            it("TC-62: asks for the book's sessions at the page limit") {
                withServer { server, script ->
                    val uuid = randomString()
                    script.answer(SESSIONS, reply(200, sessionPage(uuid, null)))

                    sut(server.url("/")).getSessions(uuid)

                    val url = server.takeRequest().url
                    url.encodedPath shouldBe SESSIONS
                    url.queryParameter("book") shouldBe uuid
                    url.queryParameter("limit") shouldBe "100"
                }
            }
        }

        context("when the server has several pages") {
            it("TC-63: follows each cursor to the last page") {
                withServer { server, script ->
                    val uuid = randomString()
                    val firstCursor = randomString()
                    val secondCursor = randomString()
                    script.answer(SESSIONS, reply(200, sessionPage(uuid, firstCursor)))
                    script.answer(SESSIONS, reply(200, sessionPage(uuid, secondCursor)))
                    script.answer(SESSIONS, reply(200, sessionPage(uuid, null)))

                    val result = sut(server.url("/")).getSessions(uuid).shouldBeTypeOf<ReadingSessionsResult.Success>()

                    result.sessions.size shouldBe 3
                    List(3) { server.takeRequest().url.queryParameter("before") } shouldBe
                        listOf(null, firstCursor, secondCursor)
                }
            }
        }

        context("when the server never stops offering pages") {
            it("TC-63: stops reading at 12 pages") {
                withServer { server, script ->
                    val uuid = randomString()
                    repeat(13) { script.answer(SESSIONS, reply(200, sessionPage(uuid, randomString()))) }

                    val result = sut(server.url("/")).getSessions(uuid).shouldBeTypeOf<ReadingSessionsResult.Success>()

                    result.sessions.size shouldBe 12
                }
            }
        }

        context("when a later page fails") {
            it("TC-65: fails the whole read") {
                withServer { server, script ->
                    val uuid = randomString()
                    script.answer(SESSIONS, reply(200, sessionPage(uuid, randomString())))
                    script.answer(SESSIONS, reply(500))

                    sut(server.url("/")).getSessions(uuid) shouldBe
                        ReadingSessionsResult.Failed(BookProgressError.Http(500))
                }
            }
        }

        context("when the first page fails") {
            it("TC-65: maps each status and unusable body to its error") {
                withServer { server, script ->
                    checkAll(Exhaustive.collection(readFailures())) { (response, error) ->
                        script.answer(SESSIONS, response)

                        sut(server.url("/")).getSessions(randomString()) shouldBe ReadingSessionsResult.Failed(error)
                    }
                }
            }
        }

        context("when no server is saved") {
            it("TC-65: fails with NoServer") {
                sut().getSessions(randomString()) shouldBe ReadingSessionsResult.Failed(BookProgressError.NoServer)
            }
        }

        context("when the uuid is blank") {
            it("TC-65: fails with NotFound") {
                sut().getSessions(" ") shouldBe ReadingSessionsResult.Failed(BookProgressError.NotFound)
            }
        }
    }
})
