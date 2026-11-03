package com.omnibus.omnibus.data.wishlist

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.FakeClientInfo
import com.omnibus.omnibus.data.network.FakeAuthTokenProvider
import com.omnibus.omnibus.data.network.FakeServerBaseUrlProvider
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
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
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okhttp3.HttpUrl

private const val UUID = "book-1"

/** `added_at` in the server's examples, in seconds and in millis. */
private const val SECONDS_ADDED = 1_700_000_000L
private const val MILLIS_ADDED = 1_700_000_000_000L

/** A wishlist entry body as the server writes it. A null argument leaves its field out. */
private fun entryBody(source: String? = "scan", addedAt: Long? = SECONDS_ADDED, id: Long = 7L): String {
    val fields = listOfNotNull(
        "\"id\":$id",
        "\"user_id\":1",
        "\"book_uuid\":\"$UUID\"",
        addedAt?.let { "\"added_at\":$it" },
        source?.let { "\"source\":\"$it\"" },
    )
    return fields.joinToString(prefix = "{", postfix = "}", separator = ",")
}

/** The state for the entry [entryBody] returns with its defaults. */
private fun presentDefault(): WishlistState = WishlistState.Present(
    WishlistEntry(id = 7L, addedAtMillis = MILLIS_ADDED, source = WishlistSource.Scan),
)

private fun reply(code: Int, body: String = ""): MockResponse = MockResponse.Builder().code(code).body(body).build()

private fun disconnect(): MockResponse = MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()

/**
 * Answers requests from a script, per HTTP method, in order. A step can hold its response until the test releases
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
private class Observed(repository: WishlistRepository, uuid: String, scope: CoroutineScope) {
    val states = CopyOnWriteArrayList<WishlistState>()
    private val job: Job = scope.launch { repository.getWishlistFlow(uuid).collect { states += it } }

    /** Waits until the latest emission is [expected]. */
    suspend fun awaitLast(expected: WishlistState) {
        withTimeout(5_000) {
            while (states.lastOrNull() != expected) delay(10)
        }
    }

    fun stop() = job.cancel()
}

class DefaultWishlistRepositoryTest : DescribeSpec({

    lateinit var server: MockWebServer
    lateinit var script: ScriptedDispatcher
    lateinit var baseUrlProvider: FakeServerBaseUrlProvider
    val scopes = mutableListOf<CoroutineScope>()

    /** A new scope that is cancelled after the test. */
    fun newScope(dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher).also { scopes += it }

    /**
     * A repository for the test server, or for no server when [baseUrlProvider] has none. [held] runs HTTP on real
     * threads, which lets a test hold a request open. Otherwise everything runs unconfined on the calling thread.
     */
    fun repositoryFor(held: Boolean = false): DefaultWishlistRepository {
        val scope = newScope(if (held) Dispatchers.Default else Dispatchers.Unconfined)
        return DefaultWishlistRepository(
            ioDispatcher = if (held) Dispatchers.IO else Dispatchers.Unconfined,
            omnibusHttpClient = OmnibusHttpClient(
                baseUrlProvider = baseUrlProvider,
                authTokenProvider = FakeAuthTokenProvider(token = "secret"),
                omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
                authClientMetadataInterceptor = AuthClientMetadataInterceptor(FakeClientInfo()),
            ),
            applicationScope = scope,
        )
    }

    /** Waits for the next request the server received, in arrival order. */
    fun nextRequest(): RecordedRequest =
        checkNotNull(server.takeRequest(5, TimeUnit.SECONDS)) { "No request reached the server" }

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    beforeEach {
        script = ScriptedDispatcher()
        server = MockWebServer().apply {
            dispatcher = script
            start()
        }
        baseUrlProvider = FakeServerBaseUrlProvider(baseUrl = server.url("/"))
    }

    afterEach {
        scopes.forEach { it.cancel() }
        scopes.clear()
        server.close()
    }

    describe("reading the wishlist entry") {

        it("#119 TC-1: a present entry maps its id, added_at seconds to millis and source, with one GET") {
            val repository = repositoryFor(held = true)
            val gate = CountDownLatch(1)
            script.answer("GET", reply(200, entryBody()), gate)
            val observed = Observed(repository, UUID, newScope(Dispatchers.Default))

            nextRequest()
            observed.awaitLast(WishlistState.Unknown)
            gate.countDown()
            observed.awaitLast(presentDefault())

            observed.states shouldBe listOf(WishlistState.Unknown, presentDefault())
            server.requestCount shouldBe 1
        }

        it("#119 TC-2: a null body means the book is not on the wishlist") {
            script.answer("GET", reply(200, "null"))

            repositoryFor().getWishlistFlow(UUID).first() shouldBe WishlistState.Absent
        }

        it("#119 TC-3: source tokens map ignoring case, unknown and missing sources give null, and a missing added_at gives null") {
            val repository = repositoryFor()
            val cases = listOf(
                entryBody(source = "detail") to WishlistSource.Detail,
                entryBody(source = "manual") to WishlistSource.Manual,
                entryBody(source = "search") to WishlistSource.Search,
                entryBody(source = "SCAN") to WishlistSource.Scan,
                entryBody(source = "hardcover") to null,
                entryBody(source = null) to null,
            )
            for ((body, source) in cases) {
                script.answer("GET", reply(200, body))
                repository.refresh(UUID)
                repository.getWishlistFlow(UUID).first() shouldBe
                    WishlistState.Present(WishlistEntry(7L, MILLIS_ADDED, source))
            }

            script.answer("GET", reply(200, entryBody(addedAt = null)))
            repository.refresh(UUID)
            repository.getWishlistFlow(UUID).first() shouldBe
                WishlistState.Present(WishlistEntry(7L, addedAtMillis = null, source = WishlistSource.Scan))
        }

        it("#119 TC-4: every failed read ends Failed, and nothing is thrown from the flow or refresh") {
            val failures = listOf(
                reply(401),
                reply(404),
                reply(500),
                disconnect(),
                reply(200, "not json"),
                reply(200, "[1,2]"),
            )
            for (failure in failures) {
                script.answer("GET", failure)
                val repository = repositoryFor()

                repository.getWishlistFlow(UUID).first() shouldBe WishlistState.Failed
                repository.refresh(UUID)
            }

            baseUrlProvider.baseUrl = null
            val before = server.requestCount
            repositoryFor().getWishlistFlow(UUID).first() shouldBe WishlistState.Failed
            server.requestCount shouldBe before
            baseUrlProvider.baseUrl = server.url("/")
        }

        it("#119 TC-5: a blank or whitespace uuid is Failed with no request") {
            val before = server.requestCount
            val repository = repositoryFor()

            repository.getWishlistFlow("").first() shouldBe WishlistState.Failed
            repository.getWishlistFlow("   ").first() shouldBe WishlistState.Failed
            repository.refresh("")
            server.requestCount shouldBe before
        }

        it("#119 TC-6: GET and DELETE use the encoded uuid segment, the wishlist suffix and the bearer token") {
            val repository = repositoryFor()
            script.answer("GET", reply(200, entryBody()))
            repository.refresh("book one")

            val get = nextRequest()
            get.method shouldBe "GET"
            get.url.encodedPath shouldBe "/api/physical/book%20one/wishlist"
            get.headers["Authorization"] shouldBe "Bearer secret"

            script.answer("DELETE", reply(200, "{\"book_deleted\":false}"))
            repository.removeFromWishlist("book one") shouldBe WishlistRemovalResult.Removed(bookDeleted = false)

            val delete = nextRequest()
            delete.method shouldBe "DELETE"
            delete.url.encodedPath shouldBe "/api/physical/book%20one/wishlist"
            delete.headers["Authorization"] shouldBe "Bearer secret"
        }

        it("#119 TC-7: two collectors share one GET while it is in flight, and a late collector reads the cached value") {
            val repository = repositoryFor(held = true)
            val scope = newScope(Dispatchers.Default)
            val gate = CountDownLatch(1)
            script.answer("GET", reply(200, entryBody()), gate)
            val first = Observed(repository, UUID, scope)
            nextRequest()
            val second = Observed(repository, UUID, scope)
            second.awaitLast(WishlistState.Unknown)
            gate.countDown()
            first.awaitLast(presentDefault())
            second.awaitLast(presentDefault())

            val late = Observed(repository, UUID, newScope(Dispatchers.Default))
            late.awaitLast(presentDefault())
            server.requestCount shouldBe 1
        }

        it("#119 TC-8: collecting after a Failed answer starts a new GET, which can succeed") {
            val repository = repositoryFor()
            script.answer("GET", reply(500))
            repository.getWishlistFlow(UUID).first() shouldBe WishlistState.Failed

            script.answer("GET", reply(200, entryBody()))
            repository.getWishlistFlow(UUID).first() shouldBe presentDefault()
            server.requestCount shouldBe 2
        }

        it("#119 TC-9: refresh updates the state, joins a refresh in flight, and a failed refresh keeps the known answer") {
            val repository = repositoryFor()
            script.answer("GET", reply(200, entryBody()))
            repository.refresh(UUID)
            repository.getWishlistFlow(UUID).first() shouldBe presentDefault()

            script.answer("GET", reply(200, "null"))
            repository.refresh(UUID)
            repository.getWishlistFlow(UUID).first() shouldBe WishlistState.Absent

            nextRequest() // the Present read above
            nextRequest() // the null read above

            val held = repositoryFor(held = true)
            val scope = newScope(Dispatchers.Default)
            val gate = CountDownLatch(1)
            script.answer("GET", reply(200, entryBody()), gate)
            val before = server.requestCount
            val first = scope.async { held.refresh(UUID) }
            nextRequest()
            val second = scope.async { held.refresh(UUID) }
            delay(200) // give the second refresh time to join the GET in flight before it completes
            gate.countDown()
            first.await()
            second.await()
            server.requestCount shouldBe before + 1
            held.getWishlistFlow(UUID).first() shouldBe presentDefault()

            script.answer("GET", reply(500))
            held.refresh(UUID)
            held.getWishlistFlow(UUID).first() shouldBe presentDefault()
        }
    }

    describe("removing from the wishlist") {

        it("#119 TC-10: a DELETE returning book_deleted true removes the entry from a Present state") {
            val repository = repositoryFor()
            script.answer("GET", reply(200, entryBody()))
            repository.refresh(UUID)
            val before = server.requestCount

            script.answer("DELETE", reply(200, "{\"book_deleted\":true}"))
            repository.removeFromWishlist(UUID) shouldBe WishlistRemovalResult.Removed(bookDeleted = true)

            repository.getWishlistFlow(UUID).first() shouldBe WishlistState.Absent
            server.requestCount shouldBe before + 1
        }

        it("#119 TC-11: book_deleted false and a body without the field both read as Removed(false)") {
            listOf("{\"book_deleted\":false}", "{}").forEach { body ->
                val repository = repositoryFor()
                script.answer("GET", reply(200, entryBody()))
                repository.refresh(UUID)

                script.answer("DELETE", reply(200, body))
                repository.removeFromWishlist(UUID) shouldBe WishlistRemovalResult.Removed(bookDeleted = false)
                repository.getWishlistFlow(UUID).first() shouldBe WishlistState.Absent
            }
        }

        it("#119 TC-12: every failed DELETE maps to its error, leaves the state Present, and throws nothing") {
            val failures = listOf(
                reply(401) to WishlistError.SessionExpired,
                reply(404) to WishlistError.BookNotFound,
                reply(500) to WishlistError.Http(500),
                disconnect() to WishlistError.Unreachable,
                reply(200, "not json") to WishlistError.UnexpectedResponse,
            )
            for ((failure, error) in failures) {
                val repository = repositoryFor()
                script.answer("GET", reply(200, entryBody()))
                repository.refresh(UUID)

                script.answer("DELETE", failure)
                repository.removeFromWishlist(UUID) shouldBe WishlistRemovalResult.Failed(error)
                repository.getWishlistFlow(UUID).first() shouldBe presentDefault()
            }

            val repository = repositoryFor()
            script.answer("GET", reply(200, entryBody()))
            repository.refresh(UUID)
            baseUrlProvider.baseUrl = null
            repository.removeFromWishlist(UUID) shouldBe WishlistRemovalResult.Failed(WishlistError.NoServer)
            repository.getWishlistFlow(UUID).first() shouldBe presentDefault()
            baseUrlProvider.baseUrl = server.url("/")
        }

        it("#119 TC-13: a GET answered after a removal does not bring the entry back") {
            val repository = repositoryFor(held = true)
            val getGate = CountDownLatch(1)
            script.answer("GET", reply(200, entryBody()), getGate)
            val observed = Observed(repository, UUID, newScope(Dispatchers.Default))
            nextRequest()
            observed.awaitLast(WishlistState.Unknown)

            script.answer("DELETE", reply(200, "{\"book_deleted\":false}"))
            repository.removeFromWishlist(UUID) shouldBe WishlistRemovalResult.Removed(bookDeleted = false)
            observed.awaitLast(WishlistState.Absent)

            // Releases the stale GET. The refresh joins it if it is still in flight, and otherwise reads the book as absent.
            script.answer("GET", reply(200, "null"))
            getGate.countDown()
            repository.refresh(UUID)

            observed.states shouldBe listOf(WishlistState.Unknown, WishlistState.Absent)
            repository.getWishlistFlow(UUID).first() shouldBe WishlistState.Absent
        }

        it("#119 TC-14: a removal the caller cancels still completes in the application scope") {
            val repository = repositoryFor(held = true)
            script.answer("GET", reply(200, entryBody()))
            repository.refresh(UUID)
            nextRequest() // the read above, so the next request is the DELETE

            val gate = CountDownLatch(1)
            script.answer("DELETE", reply(200, "{\"book_deleted\":false}"), gate)
            val observed = Observed(repository, UUID, newScope(Dispatchers.Default))
            observed.awaitLast(presentDefault())
            val caller = newScope(Dispatchers.Default).launch { repository.removeFromWishlist(UUID) }
            nextRequest()
            caller.cancelAndJoin()
            gate.countDown()

            observed.awaitLast(WishlistState.Absent)
        }

        it("#119 TC-15: cancelling a collector leaves the GET to finish and cache its value") {
            val repository = repositoryFor(held = true)
            val gate = CountDownLatch(1)
            script.answer("GET", reply(200, entryBody()), gate)
            val collectorScope = newScope(Dispatchers.Default)
            val observed = Observed(repository, UUID, collectorScope)
            nextRequest()
            observed.awaitLast(WishlistState.Unknown)
            observed.stop()

            gate.countDown()
            Observed(repository, UUID, newScope(Dispatchers.Default)).awaitLast(presentDefault())
            repository.getWishlistFlow(UUID).first() shouldBe presentDefault()
            server.requestCount shouldBe 1
            observed.states shouldNotContain presentDefault()
            observed.states shouldContainExactly listOf(WishlistState.Unknown)
        }

        it("cachedState reads the current state with no request, through Unknown, Present and Absent") {
            val repository = repositoryFor()
            repository.cachedState(UUID) shouldBe WishlistState.Unknown
            server.requestCount shouldBe 0

            script.answer("GET", reply(200, entryBody()))
            repository.getWishlistFlow(UUID).first() shouldBe presentDefault()
            repository.cachedState(UUID) shouldBe presentDefault()

            script.answer("DELETE", reply(200, "{\"book_deleted\":false}"))
            repository.removeFromWishlist(UUID) shouldBe WishlistRemovalResult.Removed(bookDeleted = false)
            repository.cachedState(UUID) shouldBe WishlistState.Absent
            server.requestCount shouldBe 2
        }
    }
})
