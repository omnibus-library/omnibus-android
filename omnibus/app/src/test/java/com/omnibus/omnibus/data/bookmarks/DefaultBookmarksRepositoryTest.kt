package com.omnibus.omnibus.data.bookmarks

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.TestClientInfo
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.data.network.TestAuthTokenProvider
import com.omnibus.omnibus.data.network.TestServerBaseUrlProvider
import com.omnibus.omnibus.di.ClientIdSource
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.testing.TestTimeSource
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okhttp3.HttpUrl

private val AWAIT_TIMEOUT = 5.seconds

private fun reply(code: Int, body: String = ""): MockResponse = MockResponse.Builder().code(code).body(body).build()

/** A transport failure: the socket closes after the request arrives. */
private fun dropped(): MockResponse = MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()

private fun loaded(vararg bookmarks: Bookmark) = BookmarksState.Loaded(bookmarks.toList())

/** A row the server has saved for [bookUuid], with no percent so it maps back to itself exactly. */
private fun savedRow(bookUuid: String, clientId: String = randomString(8..16)): Bookmark =
    randomBookmark().copy(id = randomLong(), clientId = clientId, bookUuid = bookUuid, fraction = null)

/** This bookmark as the server writes it. A null field is left out, as the server would for a row made elsewhere. */
private fun Bookmark.json(): String = buildList {
    add("\"id\":$id")
    add("\"book_uuid\":\"$bookUuid\"")
    add("\"position\":\"$position\"")
    clientId?.let { add("\"client_id\":\"$it\"") }
    title?.let { add("\"title\":\"$it\"") }
    chapterTitle?.let { add("\"chapter_title\":\"$it\"") }
    createdAtSeconds?.let { add("\"created_at\":$it") }
}.joinToString(",", "{", "}")

private fun List<Bookmark>.json(): String = joinToString(",", "[", "]") { it.json() }

/** The row an add shows before the server answers. */
private fun optimistic(draft: BookmarkDraft, clientId: String, nowMillis: Long) = Bookmark(
    id = null,
    clientId = clientId,
    bookUuid = draft.bookUuid,
    position = draft.position,
    title = draft.title,
    chapterTitle = null,
    fraction = null,
    createdAtSeconds = nowMillis / 1000,
)

/**
 * Answers requests from a script, per HTTP method, in order. A step can hold its response until the test releases its
 * [CountDownLatch]. A request with no step gets a 500, so an unexpected call fails the test instead of hanging.
 */
private class ScriptedDispatcher : Dispatcher() {
    private class Step(val response: MockResponse, val gate: CountDownLatch?)

    private val steps = mutableMapOf<String, ArrayDeque<Step>>()

    fun answer(method: String, response: MockResponse, gate: CountDownLatch? = null) {
        synchronized(steps) { steps.getOrPut(method) { ArrayDeque() }.addLast(Step(response, gate)) }
    }

    override fun dispatch(request: RecordedRequest): MockResponse {
        val method = request.method.orEmpty()
        val step = synchronized(steps) { steps[method]?.removeFirstOrNull() }
            ?: return reply(500, "unscripted $method")
        step.gate?.await(5, TimeUnit.SECONDS)
        return step.response
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

/** Runs [block] with a scope on real threads, for a test that holds requests open. The scope ends with the block. */
private inline fun <T> withThreads(block: (CoroutineScope) -> T): T {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    return try {
        block(scope)
    } finally {
        scope.cancel()
    }
}

/** A scripted server plus a scope on real threads, for a test that holds requests open. Both end with the block. */
private inline fun <T> withThreadedServer(block: (MockWebServer, ScriptedDispatcher, CoroutineScope) -> T): T =
    withServer { server, script -> withThreads { scope -> block(server, script, scope) } }

/** Waits for the next request the server received, in arrival order. */
private fun MockWebServer.nextRequest(): RecordedRequest =
    checkNotNull(takeRequest(5, TimeUnit.SECONDS)) { "No request reached the server" }

private fun RecordedRequest.json(): JsonObject = Json.parseToJsonElement(body!!.utf8()).jsonObject

/** Consumes states until [expected], which a real-thread fetch may reach after other states or none. */
private suspend fun ReceiveTurbine<BookmarksState>.awaitState(expected: BookmarksState) {
    while (awaitItem() != expected) Unit
}

class DefaultBookmarksRepositoryTest : DescribeSpec({

    fun sut(
        baseUrl: HttpUrl? = null,
        applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        ioDispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
        timeSource: TimeSource = TestTimeSource(randomLong()),
        clientIdSource: ClientIdSource = ClientIdSource { randomString(8..16) },
    ): DefaultBookmarksRepository = DefaultBookmarksRepository(
        ioDispatcher = ioDispatcher,
        omnibusHttpClient = OmnibusHttpClient(
            baseUrlProvider = TestServerBaseUrlProvider(baseUrl),
            authTokenProvider = TestAuthTokenProvider(),
            omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
            authClientMetadataInterceptor = AuthClientMetadataInterceptor(TestClientInfo()),
        ),
        applicationScope = applicationScope,
        timeSource = timeSource,
        clientIdSource = clientIdSource,
    )

    /** A repository whose HTTP runs on real threads, so a test can hold a request open. */
    fun threadedSut(
        server: MockWebServer,
        scope: CoroutineScope,
        nowMillis: Long = randomLong(),
        clientId: String = randomString(8..16),
    ): DefaultBookmarksRepository =
        sut(server.url("/"), scope, Dispatchers.IO, TestTimeSource(nowMillis), ClientIdSource { clientId })

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("adding a bookmark") {
        context("when the server accepts the bookmark") {
            it("TC-15: sends one flat body with the minted id and the clock in seconds") {
                withServer { server, script ->
                    val nowMillis = randomLong()
                    val clientId = randomString()
                    val title = randomString()
                    val draft = BookmarkDraft.create(randomString(), randomString(), title)!!
                    script.answer("POST", reply(200, savedRow(draft.bookUuid, clientId).json()))

                    sut(
                        server.url("/"),
                        timeSource = TestTimeSource(nowMillis),
                        clientIdSource = ClientIdSource { clientId },
                    ).addBookmark(draft)

                    val request = server.nextRequest()
                    request.method shouldBe "POST"
                    request.url.encodedPath shouldBe "/api/bookmarks"
                    val body = request.json()
                    body.keys shouldBe setOf("book_uuid", "position", "title", "client_id", "client_created_at")
                    body["book_uuid"]!!.jsonPrimitive.content shouldBe draft.bookUuid
                    body["position"]!!.jsonPrimitive.content shouldBe draft.position
                    body["title"]!!.jsonPrimitive.content shouldBe title
                    body["client_id"]!!.jsonPrimitive.content shouldBe clientId
                    body["client_created_at"]!!.jsonPrimitive.long shouldBe nowMillis / 1000
                }
            }

            it("TC-15: returns the saved row") {
                withServer { server, script ->
                    val draft = randomBookmarkDraft()
                    val row = savedRow(draft.bookUuid)
                    script.answer("POST", reply(200, row.json()))

                    sut(server.url("/")).addBookmark(draft) shouldBe BookmarkWriteResult.Saved(row)
                }
            }

            it("TC-18: shows the optimistic row until the server row replaces it") {
                withThreadedServer { server, script, scope ->
                    val nowMillis = randomLong()
                    val clientId = randomString(8..16)
                    val draft = randomBookmarkDraft()
                    val existing = savedRow(draft.bookUuid)
                    val saved = savedRow(draft.bookUuid, clientId)
                    val repository = threadedSut(server, scope, nowMillis, clientId)
                    val gate = CountDownLatch(1)
                    script.answer("GET", reply(200, listOf(existing).json()))
                    script.answer("POST", reply(200, saved.json()), gate)

                    repository.getBookmarksFlow(draft.bookUuid).test(timeout = AWAIT_TIMEOUT) {
                        awaitState(loaded(existing))

                        val add = scope.async { repository.addBookmark(draft) }
                        awaitItem() shouldBe loaded(existing, optimistic(draft, clientId, nowMillis))

                        gate.countDown()
                        awaitItem() shouldBe loaded(existing, saved)
                        add.await()
                    }
                }
            }
        }

        context("when the draft has no title") {
            it("TC-15: leaves the title out of the body") {
                withServer { server, script ->
                    val draft = BookmarkDraft.create(randomString(), randomString(), null)!!
                    script.answer("POST", reply(200, savedRow(draft.bookUuid).json()))

                    sut(server.url("/")).addBookmark(draft)

                    server.nextRequest().json().keys shouldBe
                        setOf("book_uuid", "position", "client_id", "client_created_at")
                }
            }
        }

        context("when the add fails") {
            it("TC-19: returns the typed error for each answer") {
                withServer { server, script ->
                    val failures = listOf(
                        dropped() to BookmarkError.Unreachable,
                        reply(400) to BookmarkError.Rejected,
                        reply(401) to BookmarkError.SessionExpired,
                        reply(404) to BookmarkError.BookNotFound,
                        reply(500) to BookmarkError.Http(500),
                        reply(200, "{not json") to BookmarkError.UnexpectedResponse,
                    )
                    checkAll(Exhaustive.collection(failures)) { (answer, error) ->
                        script.answer("POST", answer)

                        sut(server.url("/")).addBookmark(randomBookmarkDraft()) shouldBe BookmarkWriteResult.Failed(error)
                    }
                }
            }

            it("TC-19: removes the optimistic row") {
                withThreadedServer { server, script, scope ->
                    val nowMillis = randomLong()
                    val clientId = randomString(8..16)
                    val draft = randomBookmarkDraft()
                    val repository = threadedSut(server, scope, nowMillis, clientId)
                    val gate = CountDownLatch(1)
                    script.answer("GET", reply(200, "[]"))
                    script.answer("POST", reply(500), gate)

                    repository.getBookmarksFlow(draft.bookUuid).test(timeout = AWAIT_TIMEOUT) {
                        awaitState(loaded())

                        val add = scope.async { repository.addBookmark(draft) }
                        awaitItem() shouldBe loaded(optimistic(draft, clientId, nowMillis))

                        gate.countDown()
                        awaitItem() shouldBe loaded()
                        add.await()
                    }
                }
            }
        }

        context("when no server is saved") {
            it("TC-19: returns NoServer") {
                sut().addBookmark(randomBookmarkDraft()) shouldBe BookmarkWriteResult.Failed(BookmarkError.NoServer)
            }
        }

        context("when an earlier write for the book is still in flight") {
            it("TC-33: holds the next POST until the earlier one answers") {
                withThreadedServer { server, script, scope ->
                    val draft = randomBookmarkDraft()
                    val repository = threadedSut(server, scope)
                    val gate = CountDownLatch(1)
                    script.answer("POST", reply(200, savedRow(draft.bookUuid).json()), gate)
                    script.answer("POST", reply(200, savedRow(draft.bookUuid).json()))

                    val first = scope.async { repository.addBookmark(draft) }
                    server.nextRequest()
                    val second = scope.async { repository.addBookmark(draft) }

                    // An absence can only be observed over a bounded wait. A correct build always passes it.
                    server.takeRequest(200, TimeUnit.MILLISECONDS).shouldBeNull()

                    gate.countDown()
                    first.await()
                    second.await()
                }
            }
        }

        context("when the caller is cancelled mid-request") {
            it("TC-39: still saves the row") {
                withThreadedServer { server, script, scope ->
                    val nowMillis = randomLong()
                    val clientId = randomString(8..16)
                    val draft = randomBookmarkDraft()
                    val saved = savedRow(draft.bookUuid, clientId)
                    val repository = threadedSut(server, scope, nowMillis, clientId)
                    val gate = CountDownLatch(1)
                    script.answer("GET", reply(200, "[]"))
                    script.answer("POST", reply(200, saved.json()), gate)

                    repository.getBookmarksFlow(draft.bookUuid).test(timeout = AWAIT_TIMEOUT) {
                        awaitState(loaded())

                        val caller = scope.async { repository.addBookmark(draft) }
                        awaitItem() shouldBe loaded(optimistic(draft, clientId, nowMillis))
                        caller.cancelAndJoin()

                        gate.countDown()
                        awaitItem() shouldBe loaded(saved)
                    }
                }
            }
        }

        context("when the first fetch hasn't landed") {
            it("TC-38: keeps the row across the late list") {
                withThreadedServer { server, script, scope ->
                    val nowMillis = randomLong()
                    val clientId = randomString(8..16)
                    val draft = randomBookmarkDraft()
                    val saved = savedRow(draft.bookUuid, clientId)
                    val repository = threadedSut(server, scope, nowMillis, clientId)
                    val getGate = CountDownLatch(1)
                    val postGate = CountDownLatch(1)
                    script.answer("GET", reply(200, "[]"), getGate)
                    script.answer("POST", reply(200, saved.json()), postGate)

                    repository.getBookmarksFlow(draft.bookUuid).test(timeout = AWAIT_TIMEOUT) {
                        awaitItem() shouldBe BookmarksState.Loading

                        val add = scope.async { repository.addBookmark(draft) }
                        awaitItem() shouldBe loaded(optimistic(draft, clientId, nowMillis))

                        postGate.countDown()
                        awaitItem() shouldBe loaded(saved)
                        add.await()

                        getGate.countDown()
                        repository.refresh(draft.bookUuid)
                        expectNoEvents()
                    }
                }
            }
        }

        context("when a fetch lands while the add is pending") {
            it("TC-34: keeps the optimistic row") {
                withThreadedServer { server, script, scope ->
                    val nowMillis = randomLong()
                    val clientId = randomString(8..16)
                    val draft = randomBookmarkDraft()
                    val existing = savedRow(draft.bookUuid)
                    val repository = threadedSut(server, scope, nowMillis, clientId)
                    val gate = CountDownLatch(1)
                    script.answer("GET", reply(200, listOf(existing).json()))
                    script.answer("POST", reply(200, savedRow(draft.bookUuid, clientId).json()), gate)

                    repository.getBookmarksFlow(draft.bookUuid).test(timeout = AWAIT_TIMEOUT) {
                        awaitState(loaded(existing))

                        val add = scope.async { repository.addBookmark(draft) }
                        awaitItem() shouldBe loaded(existing, optimistic(draft, clientId, nowMillis))

                        script.answer("GET", reply(200, listOf(existing).json()))
                        repository.refresh(draft.bookUuid)
                        expectNoEvents()

                        gate.countDown()
                        awaitItem()
                        add.await()
                    }
                }
            }
        }

        context("when a fetch that started before the add includes the saved row") {
            it("TC-36: shows the bookmark once") {
                withThreadedServer { server, script, scope ->
                    val clientId = randomString(8..16)
                    val draft = randomBookmarkDraft()
                    val saved = savedRow(draft.bookUuid, clientId)
                    val repository = threadedSut(server, scope, clientId = clientId)
                    val gate = CountDownLatch(1)
                    script.answer("GET", reply(200, "[]"))
                    script.answer("GET", reply(200, listOf(saved).json()), gate)
                    script.answer("POST", reply(200, saved.json()))

                    repository.getBookmarksFlow(draft.bookUuid).test(timeout = AWAIT_TIMEOUT) {
                        awaitState(loaded())
                        server.nextRequest()
                        val refresh = scope.async { repository.refresh(draft.bookUuid) }
                        server.nextRequest()

                        repository.addBookmark(draft)
                        awaitState(loaded(saved))

                        gate.countDown()
                        refresh.await()
                        expectNoEvents()
                    }
                }
            }
        }
    }

    describe("reading the bookmarks") {
        context("when the first fetch is in flight") {
            it("TC-21: shows Loading until the rows arrive in the server's order") {
                withThreadedServer { server, script, scope ->
                    val uuid = randomString(8..16)
                    val rows = listOf(savedRow(uuid), savedRow(uuid), savedRow(uuid))
                    val repository = threadedSut(server, scope)
                    val gate = CountDownLatch(1)
                    script.answer("GET", reply(200, rows.json()), gate)

                    repository.getBookmarksFlow(uuid).test(timeout = AWAIT_TIMEOUT) {
                        awaitItem() shouldBe BookmarksState.Loading

                        gate.countDown()
                        awaitItem() shouldBe BookmarksState.Loaded(rows)
                    }
                }
            }
        }

        it("TC-21: sends the uuid as one encoded path segment") {
            withServer { server, script ->
                script.answer("GET", reply(200, "[]"))

                sut(server.url("/")).refresh("book 1/é")

                val request = server.nextRequest()
                request.method shouldBe "GET"
                request.url.encodedPath shouldBe "/api/bookmarks/book/book%201%2F%C3%A9"
            }
        }

        context("when the server answers with an empty array") {
            it("TC-21: reads as a loaded empty list") {
                withServer { server, script ->
                    script.answer("GET", reply(200, "[]"))

                    sut(server.url("/")).getBookmarksFlow(randomString()).first() shouldBe loaded()
                }
            }
        }

        context("when a list is already loaded") {
            it("TC-22: refetches on a new collection without dropping the list") {
                withThreadedServer { server, script, scope ->
                    val uuid = randomString(8..16)
                    val first = savedRow(uuid)
                    val second = savedRow(uuid)
                    val repository = threadedSut(server, scope)
                    script.answer("GET", reply(200, listOf(first).json()))
                    repository.getBookmarksFlow(uuid).test(timeout = AWAIT_TIMEOUT) {
                        awaitState(loaded(first))
                    }
                    val gate = CountDownLatch(1)
                    script.answer("GET", reply(200, listOf(first, second).json()), gate)

                    repository.getBookmarksFlow(uuid).test(timeout = AWAIT_TIMEOUT) {
                        awaitItem() shouldBe loaded(first)

                        gate.countDown()
                        awaitItem() shouldBe loaded(first, second)
                    }
                }
            }

            it("TC-25: keeps the list through a refetch that fails, for a bad status and a bad body") {
                withServer { server, script ->
                    checkAll(Exhaustive.collection(listOf(reply(500), reply(200, "{not json")))) { failure ->
                        val uuid = randomString(8..16)
                        val row = savedRow(uuid)
                        val repository = sut(server.url("/"))
                        script.answer("GET", reply(200, listOf(row).json()))

                        repository.getBookmarksFlow(uuid).test(timeout = AWAIT_TIMEOUT) {
                            awaitItem() shouldBe loaded(row)

                            script.answer("GET", failure)
                            repository.refresh(uuid)

                            expectNoEvents()
                        }
                    }
                }
            }
        }

        context("when a fetch is already in flight") {
            it("TC-23: joins it from every collector and refresh") {
                withThreadedServer { server, script, scope ->
                    val uuid = randomString(8..16)
                    val row = savedRow(uuid)
                    val repository = threadedSut(server, scope)
                    val gate = CountDownLatch(1)
                    script.answer("GET", reply(200, listOf(row).json()), gate)

                    repository.getBookmarksFlow(uuid).test(timeout = AWAIT_TIMEOUT) {
                        awaitItem() shouldBe BookmarksState.Loading
                        repository.getBookmarksFlow(uuid).first()
                        // UNDISPATCHED runs each refresh up to its wait on the fetch, so both have joined by now.
                        val refreshes = List(2) {
                            scope.async(start = CoroutineStart.UNDISPATCHED) { repository.refresh(uuid) }
                        }

                        gate.countDown()
                        refreshes.forEach { it.await() }
                        awaitItem() shouldBe loaded(row)
                    }
                    server.requestCount shouldBe 1
                }
            }
        }

        context("when the first fetch fails") {
            it("TC-24: reads as Failed for each bad status and body") {
                withServer { server, script ->
                    val failures = listOf(
                        reply(500),
                        dropped(),
                        reply(200, "{}"),
                        reply(200, "{not json"),
                        reply(200, """[{"id":"abc"}]"""),
                    )
                    checkAll(Exhaustive.collection(failures)) { failure ->
                        script.answer("GET", failure)

                        sut(server.url("/")).getBookmarksFlow(randomString()).first() shouldBe BookmarksState.Failed
                    }
                }
            }

            it("TC-26: shows Loading then the list on a retry") {
                withThreadedServer { server, script, scope ->
                    val uuid = randomString(8..16)
                    val row = savedRow(uuid)
                    val repository = threadedSut(server, scope)
                    val gate = CountDownLatch(1)
                    script.answer("GET", reply(500))

                    repository.getBookmarksFlow(uuid).test(timeout = AWAIT_TIMEOUT) {
                        awaitState(BookmarksState.Failed)
                        script.answer("GET", reply(200, listOf(row).json()), gate)

                        val refresh = scope.async { repository.refresh(uuid) }
                        awaitItem() shouldBe BookmarksState.Loading

                        gate.countDown()
                        awaitItem() shouldBe loaded(row)
                        refresh.await()
                    }
                }
            }
        }

        context("when no server is saved") {
            it("TC-24: reads as Failed") {
                sut().getBookmarksFlow(randomString()).first() shouldBe BookmarksState.Failed
            }
        }
    }

    describe("removing a bookmark") {
        it("TC-29: addresses the DELETE by client id, else numeric id, as one encoded segment") {
            withServer { server, script ->
                val cases = listOf(
                    randomBookmark().copy(id = randomLong(), clientId = "client-a") to "/api/bookmarks/client-a",
                    randomBookmark().copy(id = 42L, clientId = null) to "/api/bookmarks/42",
                    randomBookmark().copy(id = null, clientId = "a b/c?d") to "/api/bookmarks/a%20b%2Fc%3Fd",
                )
                checkAll(Exhaustive.collection(cases)) { (bookmark, path) ->
                    script.answer("DELETE", reply(204))

                    sut(server.url("/")).removeBookmark(bookmark)

                    val request = server.nextRequest()
                    request.method shouldBe "DELETE"
                    request.url.encodedPath shouldBe path
                }
            }
        }

        context("when the DELETE is in flight") {
            it("TC-28: hides the row") {
                withThreadedServer { server, script, scope ->
                    val uuid = randomString(8..16)
                    val removed = savedRow(uuid)
                    val kept = savedRow(uuid)
                    val repository = threadedSut(server, scope)
                    val gate = CountDownLatch(1)
                    script.answer("GET", reply(200, listOf(removed, kept).json()))
                    script.answer("DELETE", reply(204), gate)

                    repository.getBookmarksFlow(uuid).test(timeout = AWAIT_TIMEOUT) {
                        awaitState(loaded(removed, kept))

                        val remove = scope.async { repository.removeBookmark(removed) }
                        awaitItem() shouldBe loaded(kept)

                        gate.countDown()
                        remove.await() shouldBe BookmarkWriteResult.Removed
                    }
                }
            }
        }

        context("when the server has no such bookmark") {
            it("TC-30: counts it as removed") {
                withServer { server, script ->
                    val bookmark = savedRow(randomString(8..16))
                    script.answer("DELETE", reply(404))

                    sut(server.url("/")).removeBookmark(bookmark) shouldBe BookmarkWriteResult.Removed
                }
            }
        }

        context("when the DELETE fails") {
            it("TC-31: returns the typed error for each answer") {
                withServer { server, script ->
                    val failures = listOf(
                        reply(401) to BookmarkError.SessionExpired,
                        reply(500) to BookmarkError.Http(500),
                        reply(400) to BookmarkError.Rejected,
                        dropped() to BookmarkError.Unreachable,
                    )
                    checkAll(Exhaustive.collection(failures)) { (answer, error) ->
                        script.answer("DELETE", answer)

                        sut(server.url("/")).removeBookmark(savedRow(randomString(8..16))) shouldBe
                            BookmarkWriteResult.Failed(error)
                    }
                }
            }

            it("TC-31: puts the row back where it was") {
                withThreadedServer { server, script, scope ->
                    val uuid = randomString(8..16)
                    val before = savedRow(uuid)
                    val removed = savedRow(uuid)
                    val after = savedRow(uuid)
                    val repository = threadedSut(server, scope)
                    val gate = CountDownLatch(1)
                    script.answer("GET", reply(200, listOf(before, removed, after).json()))
                    script.answer("DELETE", reply(500), gate)

                    repository.getBookmarksFlow(uuid).test(timeout = AWAIT_TIMEOUT) {
                        awaitState(loaded(before, removed, after))

                        val remove = scope.async { repository.removeBookmark(removed) }
                        awaitItem() shouldBe loaded(before, after)

                        gate.countDown()
                        awaitItem() shouldBe loaded(before, removed, after)
                        remove.await()
                    }
                }
            }
        }

        context("when no server is saved") {
            it("TC-31: returns NoServer") {
                sut().removeBookmark(savedRow(randomString(8..16))) shouldBe
                    BookmarkWriteResult.Failed(BookmarkError.NoServer)
            }
        }

        context("when the bookmark has neither a client id nor a numeric id") {
            it("TC-14: returns UnexpectedResponse") {
                sut().removeBookmark(randomBookmark().copy(id = null, clientId = null)) shouldBe
                    BookmarkWriteResult.Failed(BookmarkError.UnexpectedResponse)
            }
        }

        context("when the book uuid is blank") {
            it("returns BookNotFound") {
                sut().removeBookmark(savedRow(bookUuid = "  ")) shouldBe
                    BookmarkWriteResult.Failed(BookmarkError.BookNotFound)
            }
        }

        context("when the row's create is still in flight") {
            it("TC-32: leaves nothing once both writes finish") {
                withThreadedServer { server, script, scope ->
                    val nowMillis = randomLong()
                    val clientId = randomString(8..16)
                    val draft = randomBookmarkDraft()
                    val repository = threadedSut(server, scope, nowMillis, clientId)
                    val row = optimistic(draft, clientId, nowMillis)
                    val gate = CountDownLatch(1)
                    script.answer("GET", reply(200, "[]"))
                    script.answer("POST", reply(200, savedRow(draft.bookUuid, clientId).json()), gate)
                    script.answer("DELETE", reply(204))

                    repository.getBookmarksFlow(draft.bookUuid).test(timeout = AWAIT_TIMEOUT) {
                        awaitState(loaded())

                        val add = scope.async { repository.addBookmark(draft) }
                        awaitItem() shouldBe loaded(row)

                        val remove = scope.async { repository.removeBookmark(row) }
                        awaitItem() shouldBe loaded()

                        gate.countDown()
                        add.await()
                        remove.await()
                        expectNoEvents()
                    }
                }
            }
        }

        context("when a fetch that still lists the row lands during the DELETE") {
            it("TC-35: keeps the row hidden") {
                withThreadedServer { server, script, scope ->
                    val uuid = randomString(8..16)
                    val removed = savedRow(uuid)
                    val kept = savedRow(uuid)
                    val rows = listOf(removed, kept).json()
                    val repository = threadedSut(server, scope)
                    val gate = CountDownLatch(1)
                    script.answer("GET", reply(200, rows))
                    script.answer("DELETE", reply(204), gate)

                    repository.getBookmarksFlow(uuid).test(timeout = AWAIT_TIMEOUT) {
                        awaitState(loaded(removed, kept))

                        val remove = scope.async { repository.removeBookmark(removed) }
                        awaitItem() shouldBe loaded(kept)

                        script.answer("GET", reply(200, rows))
                        repository.refresh(uuid)
                        gate.countDown()
                        remove.await()

                        expectNoEvents()
                    }
                }
            }
        }

        context("when a fetch that started before the DELETE lands after it") {
            it("TC-35: doesn't bring the row back") {
                withThreadedServer { server, script, scope ->
                    val uuid = randomString(8..16)
                    val row = savedRow(uuid)
                    val repository = threadedSut(server, scope)
                    val gate = CountDownLatch(1)
                    script.answer("GET", reply(200, listOf(row).json()))
                    script.answer("GET", reply(200, listOf(row).json()), gate)
                    script.answer("DELETE", reply(204))

                    repository.getBookmarksFlow(uuid).test(timeout = AWAIT_TIMEOUT) {
                        awaitState(loaded(row))
                        server.nextRequest()
                        val refresh = scope.async { repository.refresh(uuid) }
                        server.nextRequest()

                        repository.removeBookmark(row)
                        awaitState(loaded())

                        gate.countDown()
                        refresh.await()
                        expectNoEvents()
                    }
                }
            }
        }
    }
})
