package com.omnibus.omnibus.data.bookmarks

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.TestClientInfo
import com.omnibus.omnibus.data.network.TestAuthTokenProvider
import com.omnibus.omnibus.data.network.TestServerBaseUrlProvider
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.di.ClientIdSource
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
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

private const val UUID = "book-1"
private const val POSITION = "epubcfi(/6/4!/4/2:0)"
private const val NOW_MS = 1_700_000_123_456L
private const val NOW_SECONDS = 1_700_000_123L

/** A server row as the app reads it. A null [clientId] or [title] is left out, as the server would for a row made elsewhere. */
private fun row(id: Long, clientId: String?, title: String? = null): String {
    val client = clientId?.let { ",\"client_id\":\"$it\"" }.orEmpty()
    val titled = title?.let { ",\"title\":\"$it\"" }.orEmpty()
    return """{"id":$id,"book_uuid":"$UUID","position":"$POSITION"$client$titled}"""
}

/** The domain bookmark [row] decodes to. */
private fun saved(id: Long, clientId: String?, title: String? = null) =
    Bookmark(id, clientId, UUID, POSITION, title, null, null, null)

/** The optimistic row an add shows before the server answers, stamped with the time the test clock reports. */
private fun optimistic(clientId: String, title: String? = "Chapter One") =
    Bookmark(null, clientId, UUID, POSITION, title, null, null, NOW_SECONDS)

/** A draft for the book at [POSITION]. */
private fun draft(title: String? = "Chapter One") = BookmarkDraft.create(UUID, POSITION, title)!!

private fun loaded(vararg bookmarks: Bookmark) = BookmarksState.Loaded(bookmarks.toList())

private fun reply(code: Int, body: String = ""): MockResponse = MockResponse.Builder().code(code).body(body).build()

/** A transport failure: the socket closes after the request arrives. */
private fun dropped(): MockResponse = MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()

/** The JSON object a create request sent. */
private fun RecordedRequest.json(): JsonObject = Json.parseToJsonElement(body!!.utf8()).jsonObject

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

/** Collects a book's flow in the background, recording every emission in order. */
private class Observed(repository: BookmarksRepository, uuid: String, scope: CoroutineScope) {
    val states = CopyOnWriteArrayList<BookmarksState>()
    private val job: Job = scope.launch { repository.getBookmarksFlow(uuid).collect { states += it } }

    /** Waits until the latest emission is [expected]. */
    suspend fun awaitLast(expected: BookmarksState) {
        withTimeout(5_000) {
            while (states.lastOrNull() != expected) delay(10)
        }
    }

    fun stop() = job.cancel()
}

class DefaultBookmarksRepositoryTest : DescribeSpec({

    lateinit var server: MockWebServer
    lateinit var script: ScriptedDispatcher
    val scopes = mutableListOf<CoroutineScope>()
    var minted = 0

    /** A new scope that is cancelled after the test. */
    fun newScope(dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher).also { scopes += it }

    /**
     * A repository reading [urls] (the default is the test server). [held] runs HTTP on real threads, which lets a test
     * hold a request open. Otherwise everything runs unconfined on the calling thread. Client ids are "client-1", "client-2"
     * and so on, and every write is stamped with [NOW_MS].
     */
    fun repositoryFor(
        held: Boolean = false,
        urls: TestServerBaseUrlProvider = TestServerBaseUrlProvider(baseUrl = server.url("/")),
    ): DefaultBookmarksRepository {
        val scope = newScope(if (held) Dispatchers.Default else Dispatchers.Unconfined)
        return DefaultBookmarksRepository(
            ioDispatcher = if (held) Dispatchers.IO else Dispatchers.Unconfined,
            omnibusHttpClient = OmnibusHttpClient(
                baseUrlProvider = urls,
                authTokenProvider = TestAuthTokenProvider(token = "secret"),
                omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
                authClientMetadataInterceptor = AuthClientMetadataInterceptor(TestClientInfo()),
            ),
            applicationScope = scope,
            timeSource = TimeSource { NOW_MS },
            clientIdSource = ClientIdSource { "client-${++minted}" },
        )
    }

    /** Waits for the next request the server received, in arrival order. */
    fun nextRequest(): RecordedRequest =
        checkNotNull(server.takeRequest(5, TimeUnit.SECONDS)) { "No request reached the server" }

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    beforeEach {
        minted = 0
        script = ScriptedDispatcher()
        server = MockWebServer().apply {
            dispatcher = script
            start()
        }
    }

    afterEach {
        scopes.forEach { it.cancel() }
        scopes.clear()
        server.close()
    }

    describe("adding a bookmark") {

        // TC-15: one flat body with the minted id and the clock in seconds; a null title is left out; the saved row comes back.
        it("TC-15: add sends one flat create body and returns the saved row") {
            script.answer("POST", reply(200, row(7, "client-1", title = "Chapter One")))
            val repository = repositoryFor()

            repository.addBookmark(draft("Chapter One")) shouldBe
                BookmarkWriteResult.Saved(saved(7, "client-1", title = "Chapter One"))

            val first = nextRequest()
            first.method shouldBe "POST"
            first.url.encodedPath shouldBe "/api/bookmarks"
            first.headers["Authorization"] shouldBe "Bearer secret"
            first.json().keys shouldBe setOf("book_uuid", "position", "title", "client_id", "client_created_at")
            first.json()["book_uuid"]!!.jsonPrimitive.content shouldBe UUID
            first.json()["position"]!!.jsonPrimitive.content shouldBe POSITION
            first.json()["title"]!!.jsonPrimitive.content shouldBe "Chapter One"
            first.json()["client_id"]!!.jsonPrimitive.content shouldBe "client-1"
            first.json()["client_created_at"]!!.jsonPrimitive.long shouldBe NOW_SECONDS

            script.answer("POST", reply(200, row(8, "client-2")))
            repository.addBookmark(draft(null)) shouldBe BookmarkWriteResult.Saved(saved(8, "client-2"))

            val second = nextRequest()
            second.json().keys shouldBe setOf("book_uuid", "position", "client_id", "client_created_at")
            second.json()["client_id"]!!.jsonPrimitive.content shouldBe "client-2"
        }

        // TC-18: the optimistic row shows before the answer; the saved row takes its place with no duplicate.
        it("TC-18: an add shows the optimistic row first, then the server row replaces it") {
            val repository = repositoryFor(held = true)
            script.answer("GET", reply(200, "[${row(1, "client-a")}]"))
            val observed = Observed(repository, UUID, newScope(Dispatchers.Default))
            observed.awaitLast(loaded(saved(1, "client-a")))
            nextRequest()

            val gate = CountDownLatch(1)
            script.answer("POST", reply(200, row(7, "client-1", title = "Chapter One")), gate)
            val add = newScope(Dispatchers.Default).async { repository.addBookmark(draft("Chapter One")) }
            nextRequest()
            observed.awaitLast(loaded(saved(1, "client-a"), optimistic("client-1")))

            gate.countDown()
            add.await() shouldBe BookmarkWriteResult.Saved(saved(7, "client-1", title = "Chapter One"))
            observed.awaitLast(loaded(saved(1, "client-a"), saved(7, "client-1", title = "Chapter One")))
        }

        // TC-19: each failure cause rolls the optimistic row back and returns its own typed error; nothing is thrown.
        it("TC-19: every failed add rolls back and returns its typed error") {
            val failures = listOf(
                { dropped() } to BookmarkError.Unreachable,
                { reply(400) } to BookmarkError.Rejected,
                { reply(401) } to BookmarkError.SessionExpired,
                { reply(404) } to BookmarkError.BookNotFound,
                { reply(500) } to BookmarkError.Http(500),
                { reply(200, "{not json") } to BookmarkError.UnexpectedResponse,
            )
            failures.forEach { (answer, error) ->
                val repository = repositoryFor()
                script.answer("GET", reply(200, "[]"))
                val observed = Observed(repository, UUID, newScope(Dispatchers.Unconfined))
                observed.awaitLast(loaded())
                script.answer("POST", answer())

                repository.addBookmark(draft()) shouldBe BookmarkWriteResult.Failed(error)
                observed.states.last() shouldBe loaded()
            }

            // No server: the list never loaded, so the state stays Failed and the add is refused without a request.
            val offline = TestServerBaseUrlProvider(baseUrl = null)
            val repository = repositoryFor(urls = offline)
            val observed = Observed(repository, UUID, newScope(Dispatchers.Unconfined))
            observed.awaitLast(BookmarksState.Failed)
            repository.addBookmark(draft()) shouldBe BookmarkWriteResult.Failed(BookmarkError.NoServer)
            observed.states.last() shouldBe BookmarksState.Failed
        }

        // TC-33: the second POST waits for the first to finish, and each body carries the id minted for it.
        it("TC-33: writes for one book are sent one at a time in call order") {
            val repository = repositoryFor(held = true)
            script.answer("GET", reply(200, "[]"))
            val observed = Observed(repository, UUID, newScope(Dispatchers.Default))
            observed.awaitLast(loaded())
            nextRequest()

            val gate = CountDownLatch(1)
            script.answer("POST", reply(200, row(7, "client-1")), gate)
            script.answer("POST", reply(200, row(8, "client-2")))
            val scope = newScope(Dispatchers.Default)
            val first = scope.async { repository.addBookmark(draft(null)) }
            val firstPost = nextRequest()
            val second = scope.async { repository.addBookmark(draft(null)) }
            delay(200) // the second POST must wait behind the first, which is still held
            server.requestCount shouldBe 2

            gate.countDown()
            first.await() shouldBe BookmarkWriteResult.Saved(saved(7, "client-1"))
            second.await() shouldBe BookmarkWriteResult.Saved(saved(8, "client-2"))
            firstPost.json()["client_id"]!!.jsonPrimitive.content shouldBe "client-1"
            nextRequest().json()["client_id"]!!.jsonPrimitive.content shouldBe "client-2"
        }
    }

    describe("reading the bookmarks") {

        // TC-21: Loading first, then the rows in server order; the GET carries the encoded uuid and the token.
        it("TC-21: the first collection fetches and shows rows in server order") {
            val repository = repositoryFor(held = true)
            val gate = CountDownLatch(1)
            script.answer(
                "GET",
                reply(200, "[${row(9, "client-9")},${row(2, "client-2")},${row(5, null)}]"),
                gate,
            )
            val observed = Observed(repository, "book 1/é", newScope(Dispatchers.Default))
            val request = nextRequest()
            observed.awaitLast(BookmarksState.Loading)
            gate.countDown()

            observed.awaitLast(loaded(saved(9, "client-9"), saved(2, "client-2"), saved(5, null)))
            observed.states.first() shouldBe BookmarksState.Loading
            request.method shouldBe "GET"
            request.url.encodedPath shouldBe "/api/bookmarks/book/book%201%2F%C3%A9"
            request.headers["Authorization"] shouldBe "Bearer secret"

            // An empty array is a loaded empty list, not a failure.
            script.answer("GET", reply(200, "[]"))
            repositoryFor().getBookmarksFlow(UUID).first() shouldBe loaded()
        }

        // TC-22: a new collection refetches, and the list already on screen stays until the new rows land.
        it("TC-22: every new collection refetches without dropping the loaded list") {
            val repository = repositoryFor(held = true)
            script.answer("GET", reply(200, "[${row(1, "client-1")}]"))
            val first = Observed(repository, UUID, newScope(Dispatchers.Default))
            first.awaitLast(loaded(saved(1, "client-1")))
            nextRequest()
            first.stop()

            val gate = CountDownLatch(1)
            script.answer("GET", reply(200, "[${row(1, "client-1")},${row(2, "client-2")}]"), gate)
            val second = Observed(repository, UUID, newScope(Dispatchers.Default))
            nextRequest()
            second.awaitLast(loaded(saved(1, "client-1")))

            gate.countDown()
            second.awaitLast(loaded(saved(1, "client-1"), saved(2, "client-2")))
            second.states.any { it == BookmarksState.Loading } shouldBe false
            server.requestCount shouldBe 2
        }

        // TC-23: a second collector and two refreshes join the GET in flight: one request, one result.
        it("TC-23: a fetch already in flight is joined") {
            val repository = repositoryFor(held = true)
            val scope = newScope(Dispatchers.Default)
            val gate = CountDownLatch(1)
            script.answer("GET", reply(200, "[${row(1, "client-1")}]"), gate)
            val first = Observed(repository, UUID, newScope(Dispatchers.Default))
            nextRequest()

            val second = Observed(repository, UUID, newScope(Dispatchers.Default))
            val refreshA = scope.async { repository.refresh(UUID) }
            val refreshB = scope.async { repository.refresh(UUID) }
            delay(200) // give every caller time to join the GET in flight before it completes
            gate.countDown()
            refreshA.await()
            refreshB.await()

            first.awaitLast(loaded(saved(1, "client-1")))
            second.awaitLast(loaded(saved(1, "client-1")))
            server.requestCount shouldBe 1
        }

        // TC-24: every failure answer from the first fetch reads as Failed, nothing is thrown, and refresh returns normally.
        it("TC-24: a failed first fetch reads as Failed") {
            val failures = listOf(
                { reply(500) },
                { dropped() },
                { reply(200, "{}") },
                { reply(200, "{not json") },
                { reply(200, """[{"id":"abc"}]""") },
            )
            failures.forEach { answer ->
                val repository = repositoryFor()
                script.answer("GET", answer())
                repository.getBookmarksFlow(UUID).first() shouldBe BookmarksState.Failed

                script.answer("GET", reply(500))
                repository.refresh(UUID)
            }

            val repository = repositoryFor(urls = TestServerBaseUrlProvider(baseUrl = null))
            repository.getBookmarksFlow(UUID).first() shouldBe BookmarksState.Failed
            repository.refresh(UUID)
        }

        // TC-25: a refetch that fails, or answers with a body that does not decode, leaves the loaded list alone.
        it("TC-25: a failed refetch keeps the loaded list") {
            val repository = repositoryFor()
            script.answer("GET", reply(200, "[${row(1, "client-1")}]"))
            val observed = Observed(repository, UUID, newScope(Dispatchers.Unconfined))
            observed.awaitLast(loaded(saved(1, "client-1")))
            val mark = observed.states.size

            script.answer("GET", reply(500))
            repository.refresh(UUID)
            script.answer("GET", reply(200, "{not json"))
            repository.refresh(UUID)

            observed.states.drop(mark).shouldBeEmpty()
            observed.states.last() shouldBe loaded(saved(1, "client-1"))
        }

        // TC-26: a retry from Failed goes Loading, then the list.
        it("TC-26: refresh from Failed shows Loading then Loaded") {
            val repository = repositoryFor(held = true)
            script.answer("GET", reply(500))
            val observed = Observed(repository, UUID, newScope(Dispatchers.Default))
            observed.awaitLast(BookmarksState.Failed)
            nextRequest()

            val mark = observed.states.size
            val gate = CountDownLatch(1)
            script.answer("GET", reply(200, "[${row(1, "client-1")}]"), gate)
            val refresh = newScope(Dispatchers.Default).async { repository.refresh(UUID) }
            nextRequest()
            observed.awaitLast(BookmarksState.Loading)
            gate.countDown()
            refresh.await()

            observed.awaitLast(loaded(saved(1, "client-1")))
            observed.states.drop(mark) shouldBe listOf(BookmarksState.Loading, loaded(saved(1, "client-1")))
        }
    }

    describe("removing a bookmark") {

        // TC-28: the row leaves at once, the DELETE goes by client id, and a 204 counts as removed.
        it("TC-28: remove hides the row at once and deletes by client id") {
            val repository = repositoryFor(held = true)
            script.answer("GET", reply(200, "[${row(1, "client-a")},${row(2, "client-b")}]"))
            val observed = Observed(repository, UUID, newScope(Dispatchers.Default))
            observed.awaitLast(loaded(saved(1, "client-a"), saved(2, "client-b")))
            nextRequest()

            val gate = CountDownLatch(1)
            script.answer("DELETE", reply(204), gate)
            val remove = newScope(Dispatchers.Default).async { repository.removeBookmark(saved(1, "client-a")) }
            val delete = nextRequest()
            delete.method shouldBe "DELETE"
            delete.url.encodedPath shouldBe "/api/bookmarks/client-a"
            observed.awaitLast(loaded(saved(2, "client-b")))

            gate.countDown()
            remove.await() shouldBe BookmarkWriteResult.Removed
            observed.states.last() shouldBe loaded(saved(2, "client-b"))
        }

        // TC-29: without a client id the numeric id is the path; a client id with reserved characters is one encoded segment.
        it("TC-29: remove falls back to the numeric id and encodes the segment") {
            val repository = repositoryFor()
            script.answer("DELETE", reply(204))
            script.answer("DELETE", reply(204))

            repository.removeBookmark(Bookmark(42L, null, UUID, POSITION, null, null, null, null)) shouldBe
                BookmarkWriteResult.Removed
            nextRequest().url.encodedPath shouldBe "/api/bookmarks/42"

            repository.removeBookmark(Bookmark(null, "a b/c?d", UUID, POSITION, null, null, null, null)) shouldBe
                BookmarkWriteResult.Removed
            nextRequest().url.encodedPath shouldBe "/api/bookmarks/a%20b%2Fc%3Fd"
        }

        // TC-30: the server has no such bookmark, so the row is gone either way.
        it("TC-30: a 404 on delete counts as removed") {
            val repository = repositoryFor()
            script.answer("GET", reply(200, "[${row(1, "client-a")}]"))
            val observed = Observed(repository, UUID, newScope(Dispatchers.Unconfined))
            observed.awaitLast(loaded(saved(1, "client-a")))

            script.answer("DELETE", reply(404))
            repository.removeBookmark(saved(1, "client-a")) shouldBe BookmarkWriteResult.Removed
            observed.states.last() shouldBe loaded()
        }

        // TC-31: a failed delete puts the row back where it was, with the typed error for its cause.
        it("TC-31: every failed remove puts the row back with its typed error") {
            val a = saved(1, "client-1")
            val b = saved(2, "client-2")
            val c = saved(3, "client-3")
            val failures = listOf(
                { reply(401) } to BookmarkError.SessionExpired,
                { reply(500) } to BookmarkError.Http(500),
                { reply(400) } to BookmarkError.Rejected,
                { dropped() } to BookmarkError.Unreachable,
            )
            failures.forEach { (answer, error) ->
                val repository = repositoryFor()
                script.answer("GET", reply(200, "[${row(1, "client-1")},${row(2, "client-2")},${row(3, "client-3")}]"))
                val observed = Observed(repository, UUID, newScope(Dispatchers.Unconfined))
                observed.awaitLast(loaded(a, b, c))

                script.answer("DELETE", answer())
                repository.removeBookmark(b) shouldBe BookmarkWriteResult.Failed(error)
                observed.states.last() shouldBe loaded(a, b, c)
            }

            // No server: the row was loaded before the server went away, so it comes back where it was.
            val urls = TestServerBaseUrlProvider(baseUrl = server.url("/"))
            val repository = repositoryFor(urls = urls)
            script.answer("GET", reply(200, "[${row(1, "client-1")},${row(2, "client-2")},${row(3, "client-3")}]"))
            val observed = Observed(repository, UUID, newScope(Dispatchers.Unconfined))
            observed.awaitLast(loaded(a, b, c))
            urls.baseUrl = null
            repository.removeBookmark(b) shouldBe BookmarkWriteResult.Failed(BookmarkError.NoServer)
            observed.states.last() shouldBe loaded(a, b, c)
        }

        // TC-32: an add then a remove of the same new row: POST first, then DELETE of its client id, and nothing is left.
        it("TC-32: add then remove reaches the server in call order and leaves nothing") {
            val repository = repositoryFor(held = true)
            script.answer("GET", reply(200, "[]"))
            val observed = Observed(repository, UUID, newScope(Dispatchers.Default))
            observed.awaitLast(loaded())
            nextRequest()

            val gate = CountDownLatch(1)
            script.answer("POST", reply(200, row(7, "client-1", title = "Chapter One")), gate)
            script.answer("DELETE", reply(204))
            val scope = newScope(Dispatchers.Default)
            val add = scope.async { repository.addBookmark(draft()) }
            val post = nextRequest()
            observed.awaitLast(loaded(optimistic("client-1")))

            val remove = scope.async { repository.removeBookmark(optimistic("client-1")) }
            delay(200) // the DELETE must wait behind the POST that is still held
            server.requestCount shouldBe 2

            gate.countDown()
            add.await()
            remove.await() shouldBe BookmarkWriteResult.Removed
            post.method shouldBe "POST"
            val delete = nextRequest()
            delete.method shouldBe "DELETE"
            delete.url.encodedPath shouldBe "/api/bookmarks/client-1"
            observed.states.last() shouldBe loaded()
        }

        // TC-34: a fetch that lands while an add is pending keeps the optimistic row, and the saved row takes its place.
        it("TC-34: a fetch landing mid-add keeps the optimistic row") {
            val repository = repositoryFor(held = true)
            script.answer("GET", reply(200, "[${row(1, "client-a")}]"))
            val observed = Observed(repository, UUID, newScope(Dispatchers.Default))
            observed.awaitLast(loaded(saved(1, "client-a")))
            nextRequest()

            val postGate = CountDownLatch(1)
            script.answer("POST", reply(200, row(7, "client-1", title = "Chapter One")), postGate)
            val scope = newScope(Dispatchers.Default)
            val add = scope.async { repository.addBookmark(draft()) }
            nextRequest()
            observed.awaitLast(loaded(saved(1, "client-a"), optimistic("client-1")))

            val getGate = CountDownLatch(1)
            script.answer("GET", reply(200, "[${row(1, "client-a")}]"), getGate)
            val refresh = scope.async { repository.refresh(UUID) }
            nextRequest()
            getGate.countDown()
            refresh.await()
            observed.states.last() shouldBe loaded(saved(1, "client-a"), optimistic("client-1"))

            postGate.countDown()
            add.await()
            observed.awaitLast(loaded(saved(1, "client-a"), saved(7, "client-1", title = "Chapter One")))
        }

        // TC-35: a row being removed stays hidden even when a fetch that still lists it lands; the hidden row then goes.
        it("TC-35: a fetch landing mid-remove keeps the row hidden") {
            val repository = repositoryFor(held = true)
            script.answer("GET", reply(200, "[${row(1, "client-1")},${row(2, "client-2")}]"))
            val observed = Observed(repository, UUID, newScope(Dispatchers.Default))
            observed.awaitLast(loaded(saved(1, "client-1"), saved(2, "client-2")))
            nextRequest()

            val deleteGate = CountDownLatch(1)
            script.answer("DELETE", reply(204), deleteGate)
            val scope = newScope(Dispatchers.Default)
            val remove = scope.async { repository.removeBookmark(saved(1, "client-1")) }
            nextRequest()
            observed.awaitLast(loaded(saved(2, "client-2")))

            val getGate = CountDownLatch(1)
            script.answer("GET", reply(200, "[${row(1, "client-1")},${row(2, "client-2")}]"), getGate)
            val refresh = scope.async { repository.refresh(UUID) }
            nextRequest()
            getGate.countDown()
            refresh.await()
            observed.states.last() shouldBe loaded(saved(2, "client-2"))

            deleteGate.countDown()
            remove.await() shouldBe BookmarkWriteResult.Removed
            script.answer("GET", reply(200, "[${row(2, "client-2")}]"))
            repository.refresh(UUID)
            observed.awaitLast(loaded(saved(2, "client-2")))
        }

        // TC-36: a fetch that started before an add and includes the saved row shows that bookmark once.
        it("TC-36: a fetch containing the saved row does not duplicate it") {
            val repository = repositoryFor(held = true)
            script.answer("GET", reply(200, "[]"))
            val observed = Observed(repository, UUID, newScope(Dispatchers.Default))
            observed.awaitLast(loaded())
            nextRequest()

            val getGate = CountDownLatch(1)
            script.answer("GET", reply(200, "[${row(7, "client-1", title = "Chapter One")}]"), getGate)
            val scope = newScope(Dispatchers.Default)
            val refresh = scope.async { repository.refresh(UUID) }
            nextRequest()

            script.answer("POST", reply(200, row(7, "client-1", title = "Chapter One")))
            repository.addBookmark(draft()) shouldBe
                BookmarkWriteResult.Saved(saved(7, "client-1", title = "Chapter One"))

            getGate.countDown()
            refresh.await()
            observed.awaitLast(loaded(saved(7, "client-1", title = "Chapter One")))
            (observed.states.last() as BookmarksState.Loaded).bookmarks.size shouldBe 1
        }

        // TC-37: a failed add stays rolled back when a fetch that started before it lands afterwards.
        it("TC-37: a failed add stays rolled back after a late fetch") {
            val repository = repositoryFor(held = true)
            script.answer("GET", reply(200, "[]"))
            val observed = Observed(repository, UUID, newScope(Dispatchers.Default))
            observed.awaitLast(loaded())
            nextRequest()

            val getGate = CountDownLatch(1)
            script.answer("GET", reply(200, "[]"), getGate)
            val scope = newScope(Dispatchers.Default)
            val refresh = scope.async { repository.refresh(UUID) }
            nextRequest()

            script.answer("POST", reply(500))
            repository.addBookmark(draft()) shouldBe BookmarkWriteResult.Failed(BookmarkError.Http(500))
            observed.states.last() shouldBe loaded()

            getGate.countDown()
            refresh.await()
            observed.states.last() shouldBe loaded()
        }

        // TC-38: an add before the first fetch lands shows its row over Loading, and the late list does not drop it.
        it("TC-38: an add before any fetch publishes the optimistic row") {
            val repository = repositoryFor(held = true)
            val getGate = CountDownLatch(1)
            script.answer("GET", reply(200, "[]"), getGate)
            val observed = Observed(repository, UUID, newScope(Dispatchers.Default))
            nextRequest()
            observed.awaitLast(BookmarksState.Loading)

            val postGate = CountDownLatch(1)
            script.answer("POST", reply(200, row(7, "client-1", title = "Chapter One")), postGate)
            val add = newScope(Dispatchers.Default).async { repository.addBookmark(draft()) }
            nextRequest()
            observed.awaitLast(loaded(optimistic("client-1")))

            postGate.countDown()
            add.await()
            getGate.countDown()
            observed.awaitLast(loaded(saved(7, "client-1", title = "Chapter One")))
            observed.states.first() shouldBe BookmarksState.Loading
        }

        // TC-39: a caller cancelled mid-request does not cancel the write; the saved row still reaches the state.
        it("TC-39: closing the caller mid-request does not cancel the write") {
            val repository = repositoryFor(held = true)
            script.answer("GET", reply(200, "[]"))
            val observed = Observed(repository, UUID, newScope(Dispatchers.Default))
            observed.awaitLast(loaded())
            nextRequest()

            val gate = CountDownLatch(1)
            script.answer("POST", reply(200, row(7, "client-1", title = "Chapter One")), gate)
            val caller = newScope(Dispatchers.Default).async { repository.addBookmark(draft()) }
            nextRequest()
            observed.awaitLast(loaded(optimistic("client-1")))

            caller.cancelAndJoin()
            caller.isCancelled shouldBe true

            gate.countDown()
            observed.awaitLast(loaded(saved(7, "client-1", title = "Chapter One")))
        }
    }
})
