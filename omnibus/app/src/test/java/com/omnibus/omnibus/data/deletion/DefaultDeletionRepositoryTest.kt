package com.omnibus.omnibus.data.deletion

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.TestClientInfo
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.data.network.TestAuthTokenProvider
import com.omnibus.omnibus.data.network.TestServerBaseUrlProvider
import com.omnibus.omnibus.testing.dropped
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.reply
import com.omnibus.omnibus.testing.unmockAndroidLog
import com.omnibus.omnibus.testing.withMockServer
import com.omnibus.omnibus.testing.withScriptedServer
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeTypeOf
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import io.kotest.property.exhaustive.of
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl

/** A manifest body with two files, one copy and some impact, in the server's field names. */
private const val FULL_MANIFEST = """
{
  "files": [
    {"id": 1, "format": "epub", "filename": "a.epub", "ordinal": 0, "label": "Read copy", "size_bytes": 2048},
    {"id": 2, "format": "pdf", "filename": "b.pdf", "ordinal": 1, "label": null, "size_bytes": 0}
  ],
  "copies": [
    {"id": 5, "isbn": "9780000000001", "checked_in_at": 1700000000, "note": "shelf 3"}
  ],
  "impact": {
    "highlights": 4, "journal_entries": 1, "bookmarks": 2, "reading_sessions": 3,
    "listening_sessions": 0, "ratings": 6, "shelves": 7
  }
}
"""

private fun MockWebServer.nextRequest(): RecordedRequest =
    checkNotNull(takeRequest(5, TimeUnit.SECONDS)) { "No request reached the server" }

class DefaultDeletionRepositoryTest : DescribeSpec({

    fun sut(
        baseUrl: HttpUrl? = null,
        applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        ioDispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    ): DefaultDeletionRepository = DefaultDeletionRepository(
        ioDispatcher = ioDispatcher,
        omnibusHttpClient = OmnibusHttpClient(
            baseUrlProvider = TestServerBaseUrlProvider(baseUrl),
            authTokenProvider = TestAuthTokenProvider(),
            omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
            authClientMetadataInterceptor = AuthClientMetadataInterceptor(TestClientInfo()),
        ),
        applicationScope = applicationScope,
    )

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("reading the deletion manifest") {
        it("#121 TC-18: GETs the book's deletion manifest") {
            withMockServer { server ->
                val uuid = randomString()
                server.enqueue(reply(200, "{}"))

                sut(server.url("/")).fetchManifest(uuid)

                val request = server.nextRequest()
                request.method shouldBe "GET"
                request.url.encodedPath shouldBe "/api/books/$uuid/deletion-manifest"
            }
        }

        context("when the server answers with a full body") {
            it("#121 TC-18: maps the files, copies and impact") {
                withMockServer { server ->
                    server.enqueue(reply(200, FULL_MANIFEST))

                    val result = sut(server.url("/")).fetchManifest(randomString())

                    result.shouldBeTypeOf<DeletionManifestResult.Success>().manifest shouldBe DeletionManifest(
                        files = listOf(
                            DeletableFile(id = 1, format = "epub", filename = "a.epub", label = "Read copy", sizeBytes = 2048),
                            DeletableFile(id = 2, format = "pdf", filename = "b.pdf", label = null, sizeBytes = 0),
                        ),
                        copies = listOf(DeletableCopy(id = 5, isbn = "9780000000001", note = "shelf 3")),
                        impact = DeletionImpact(
                            highlights = 4,
                            journalEntries = 1,
                            bookmarks = 2,
                            readingSessions = 3,
                            listeningSessions = 0,
                            reviews = 6,
                            shelves = 7,
                        ),
                    )
                }
            }
        }

        context("when the body has unknown keys with fields missing") {
            it("#121 TC-19: takes the defaults") {
                withMockServer { server ->
                    server.enqueue(
                        reply(
                            200,
                            """{"files":[{"id":1,"filename":"a.epub","extra":true,"hash":"x"}],"impact":{"highlights":1,"unknown":4},"other":"x"}""",
                        ),
                    )

                    val manifest = sut(server.url("/")).fetchManifest(randomString())
                        .shouldBeTypeOf<DeletionManifestResult.Success>().manifest

                    manifest shouldBe DeletionManifest(
                        files = listOf(DeletableFile(id = 1, format = "", filename = "a.epub", label = null, sizeBytes = 0)),
                        copies = emptyList(),
                        impact = DeletionImpact(highlights = 1),
                    )
                }
            }
        }

        context("when the body is an empty object") {
            it("#121 TC-19: gives an empty manifest") {
                withMockServer { server ->
                    server.enqueue(reply(200, "{}"))

                    val manifest = sut(server.url("/")).fetchManifest(randomString())
                        .shouldBeTypeOf<DeletionManifestResult.Success>().manifest

                    manifest shouldBe DeletionManifest(emptyList(), emptyList(), DeletionImpact())
                }
            }
        }

        context("when the uuid has reserved characters") {
            it("#121 TC-20: sends it as one encoded path segment") {
                withMockServer { server ->
                    server.enqueue(reply(200, "{}"))

                    sut(server.url("/")).fetchManifest("a/b c")

                    server.nextRequest().url.encodedPath shouldBe "/api/books/a%2Fb%20c/deletion-manifest"
                }
            }
        }

        context("when the uuid is blank") {
            it("#121 TC-20: fails with BookNotFound for empty and whitespace uuids") {
                checkAll(Exhaustive.of("", "   ")) { uuid ->
                    sut().fetchManifest(uuid) shouldBe DeletionManifestResult.Failed(DeletionError.BookNotFound)
                }
            }
        }

        context("when the read fails") {
            it("#121 TC-23: maps each status and dropped connection to its error") {
                withScriptedServer {
                    val failures = listOf(
                        reply(401) to DeletionError.SessionExpired,
                        reply(403) to DeletionError.Forbidden,
                        reply(404) to DeletionError.BookNotFound,
                        reply(500) to DeletionError.Http(500),
                        dropped() to DeletionError.Unreachable,
                    )
                    checkAll(Exhaustive.collection(failures)) { (failure, error) ->
                        script.answer(failure)

                        sut(server.url("/")).fetchManifest(randomString()) shouldBe DeletionManifestResult.Failed(error)
                    }
                }
            }
        }

        context("when a 200 doesn't decode") {
            it("#121 TC-25: fails with UnexpectedResponse for each bad body") {
                withMockServer { server ->
                    checkAll(Exhaustive.of("not json", """{"files":3}""")) { body ->
                        server.enqueue(reply(200, body))

                        sut(server.url("/")).fetchManifest(randomString()) shouldBe
                            DeletionManifestResult.Failed(DeletionError.UnexpectedResponse)
                    }
                }
            }
        }

        context("when no server is saved") {
            it("#121 TC-25: fails with NoServer") {
                sut().fetchManifest(randomString()) shouldBe DeletionManifestResult.Failed(DeletionError.NoServer)
            }
        }
    }

    describe("deleting files and copies") {
        it("#121 TC-21: POSTs JSON to the book's delete-files path") {
            withMockServer { server ->
                val uuid = randomString()
                server.enqueue(reply(200, "{}"))

                sut(server.url("/")).deleteItems(uuid, setOf(randomLong()), emptySet())

                val request = server.nextRequest()
                request.method shouldBe "POST"
                request.url.encodedPath shouldBe "/api/books/$uuid/delete-files"
                request.headers["Content-Type"].orEmpty() shouldContain "application/json"
            }
        }

        it("#121 TC-21: sends both keys with sorted ids, for files only, copies only and neither") {
            withMockServer { server ->
                val bodies = listOf(
                    Triple(setOf(2L, 1L), emptySet<Long>(), """{"file_ids":[1,2],"copy_ids":[]}"""),
                    Triple(emptySet<Long>(), setOf(5L), """{"file_ids":[],"copy_ids":[5]}"""),
                    Triple(emptySet<Long>(), emptySet<Long>(), """{"file_ids":[],"copy_ids":[]}"""),
                )
                checkAll(Exhaustive.collection(bodies)) { (files, copies, expected) ->
                    server.enqueue(reply(200, "{}"))

                    sut(server.url("/")).deleteItems(randomString(), files, copies)

                    server.nextRequest().body?.utf8() shouldBe expected
                }
            }
        }

        context("when the server answers 2xx") {
            it("#121 TC-22: maps the deleted ids and book_deleted, across full, extended and empty bodies") {
                withMockServer { server ->
                    val answers = listOf(
                        """{"deleted_file_ids":[1,2],"deleted_copy_ids":[5],"book_deleted":true}""" to
                            DeleteItemsResult.Deleted(listOf(1, 2), listOf(5), bookDeleted = true),
                        """{"deleted_file_ids":[3],"deleted_copy_ids":[],"book_deleted":false,"extra":{"x":1}}""" to
                            DeleteItemsResult.Deleted(listOf(3), emptyList(), bookDeleted = false),
                        "{}" to DeleteItemsResult.Deleted(emptyList(), emptyList(), bookDeleted = false),
                    )
                    checkAll(Exhaustive.collection(answers)) { (body, expected) ->
                        server.enqueue(reply(200, body))

                        sut(server.url("/")).deleteItems(randomString(), emptySet(), emptySet()) shouldBe expected
                    }
                }
            }
        }

        context("when the uuid has reserved characters") {
            it("#121 TC-20: sends it as one encoded path segment") {
                withMockServer { server ->
                    server.enqueue(reply(200, "{}"))

                    sut(server.url("/")).deleteItems("a/b c", setOf(randomLong()), emptySet())

                    server.nextRequest().url.encodedPath shouldBe "/api/books/a%2Fb%20c/delete-files"
                }
            }
        }

        context("when the uuid is blank") {
            it("#121 TC-20: fails with BookNotFound for empty and whitespace uuids") {
                checkAll(Exhaustive.of("", "   ")) { uuid ->
                    sut().deleteItems(uuid, setOf(randomLong()), emptySet()) shouldBe
                        DeleteItemsResult.Failed(DeletionError.BookNotFound)
                }
            }
        }

        context("when the delete fails") {
            it("#121 TC-24: maps each status and dropped connection to its error") {
                withScriptedServer {
                    val failures = listOf(
                        reply(401) to DeletionError.SessionExpired,
                        reply(403) to DeletionError.Forbidden,
                        reply(404) to DeletionError.BookNotFound,
                        reply(422) to DeletionError.ItemNotFound,
                        reply(500) to DeletionError.Http(500),
                        dropped() to DeletionError.Unreachable,
                    )
                    checkAll(Exhaustive.collection(failures)) { (failure, error) ->
                        script.answer(failure)

                        sut(server.url("/")).deleteItems(randomString(), setOf(randomLong()), emptySet()) shouldBe
                            DeleteItemsResult.Failed(error)
                    }
                }
            }
        }

        context("when a 2xx doesn't decode") {
            it("#121 TC-25: fails with UnexpectedResponse for each bad body") {
                withMockServer { server ->
                    checkAll(Exhaustive.of("not json", """{"deleted_file_ids":3}""")) { body ->
                        server.enqueue(reply(200, body))

                        sut(server.url("/")).deleteItems(randomString(), setOf(randomLong()), emptySet()) shouldBe
                            DeleteItemsResult.Failed(DeletionError.UnexpectedResponse)
                    }
                }
            }
        }

        context("when no server is saved") {
            it("#121 TC-25: fails with NoServer") {
                sut().deleteItems(randomString(), setOf(randomLong()), emptySet()) shouldBe
                    DeleteItemsResult.Failed(DeletionError.NoServer)
            }
        }

        context("when the caller is cancelled mid-request") {
            it("#121 TC-26: lets the POST finish in the application scope") {
                withMockServer { server ->
                    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                    val callerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                    try {
                        val gate = CountDownLatch(1)
                        server.dispatcher = object : Dispatcher() {
                            override fun dispatch(request: RecordedRequest): MockResponse {
                                gate.await(5, TimeUnit.SECONDS)
                                return reply(200, "{}")
                            }
                        }
                        val repository = sut(server.url("/"), applicationScope, Dispatchers.IO)

                        val caller = callerScope.launch {
                            repository.deleteItems(randomString(), setOf(randomLong()), emptySet())
                        }
                        server.nextRequest()
                        val post = applicationScope.coroutineContext.job.children.single()
                        caller.cancelAndJoin()
                        gate.countDown()

                        withTimeout(5_000) { post.join() }
                        post.isCancelled shouldBe false
                    } finally {
                        applicationScope.cancel()
                        callerScope.cancel()
                    }
                }
            }
        }
    }
})
