package com.omnibus.omnibus.data.metadata

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.FakeClientInfo
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.network.FakeAuthTokenProvider
import com.omnibus.omnibus.data.network.FakeServerBaseUrlProvider
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okhttp3.HttpUrl

private const val UUID = "book-1"

/** A merged record as the server sends it back from the overrides route. */
private const val MERGED = """{"id":5,"unique_identifier":"u-5","title":"T","genres":["A"],"subjects":["x"]}"""

private fun reply(code: Int, body: String = ""): MockResponse = MockResponse.Builder().code(code).body(body).build()

private fun saved(): MockResponse = reply(200, MERGED)

/** A connection the server drops before it answers. */
private fun dropped(): MockResponse = MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()

/**
 * Answers requests from a script, per HTTP method, in order. A step can hold its response until the test releases
 * [CountDownLatch]. A request with no step gets a 500, so an unexpected call fails the test instead of hanging.
 * It also records the largest number of requests answered at once.
 */
private class ScriptedDispatcher : Dispatcher() {
    private class Step(val response: MockResponse, val gate: CountDownLatch?)

    private val steps = mutableMapOf<String, ArrayDeque<Step>>()
    private val answering = AtomicInteger(0)
    val maxAnswering = AtomicInteger(0)

    fun answer(method: String, response: MockResponse, gate: CountDownLatch? = null) {
        synchronized(steps) { steps.getOrPut(method) { ArrayDeque() }.addLast(Step(response, gate)) }
    }

    override fun dispatch(request: RecordedRequest): MockResponse {
        maxAnswering.accumulateAndGet(answering.incrementAndGet(), ::maxOf)
        try {
            val method = request.method.orEmpty()
            val step = synchronized(steps) { steps[method]?.removeFirstOrNull() }
                ?: return reply(500, "unscripted $method")
            step.gate?.await(5, TimeUnit.SECONDS)
            return step.response
        } finally {
            answering.decrementAndGet()
        }
    }
}

/** The raw text of a recorded request body. */
private fun bodyOf(request: RecordedRequest): String = request.body!!.utf8()

/** The values under [key] in a recorded body, as a string list. */
private fun valuesUnder(request: RecordedRequest, key: String): List<String> =
    Json.parseToJsonElement(bodyOf(request)).jsonObject.getValue(key).jsonArray.map { it.jsonPrimitive.content }

class DefaultMetadataRepositoryTest : DescribeSpec({

    lateinit var server: MockWebServer
    lateinit var script: ScriptedDispatcher
    val scopes = mutableListOf<CoroutineScope>()

    /** A new scope that is cancelled after the test. */
    fun newScope(dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher).also { scopes += it }

    /**
     * A repository for [baseUrl] (null means no server is saved). [held] runs HTTP on real threads, which lets a test
     * hold a request open. Otherwise everything runs unconfined on the calling thread.
     */
    fun repositoryFor(baseUrl: HttpUrl? = server.url("/"), held: Boolean = false): DefaultMetadataRepository =
        DefaultMetadataRepository(
            ioDispatcher = if (held) Dispatchers.IO else Dispatchers.Unconfined,
            omnibusHttpClient = OmnibusHttpClient(
                baseUrlProvider = FakeServerBaseUrlProvider(baseUrl = baseUrl),
                authTokenProvider = FakeAuthTokenProvider(token = "secret"),
                omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
                authClientMetadataInterceptor = AuthClientMetadataInterceptor(FakeClientInfo()),
            ),
            applicationScope = newScope(if (held) Dispatchers.Default else Dispatchers.Unconfined),
        )

    /** Waits for the next request the server received, in arrival order. */
    fun nextRequest(): RecordedRequest =
        checkNotNull(server.takeRequest(5, TimeUnit.SECONDS)) { "No request reached the server" }

    /** Waits up to five seconds for [deferred]. */
    suspend fun <T> awaitResult(deferred: Deferred<T>): T = withTimeout(5_000) { deferred.await() }

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    beforeEach {
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

    describe("replaceList") {

        it("TC-6: genres sends only the genres key") {
            script.answer("POST", saved())

            repositoryFor().replaceList(UUID, MetadataListKind.Genres, listOf("A", "B"))

            bodyOf(nextRequest()) shouldBe "{\"genres\":[\"A\",\"B\"]}"
        }

        it("TC-7: tags sends only the subjects key") {
            script.answer("POST", saved())

            repositoryFor().replaceList(UUID, MetadataListKind.Tags, listOf("x"))

            bodyOf(nextRequest()) shouldBe "{\"subjects\":[\"x\"]}"
        }

        it("TC-8: an empty list is sent as an empty array, never {}") {
            script.answer("POST", saved())
            script.answer("POST", saved())
            val repository = repositoryFor()

            repository.replaceList(UUID, MetadataListKind.Tags, emptyList())
            bodyOf(nextRequest()) shouldBe "{\"subjects\":[]}"

            repository.replaceList(UUID, MetadataListKind.Genres, emptyList())
            bodyOf(nextRequest()) shouldBe "{\"genres\":[]}"
        }

        it("TC-9: keeps values and order verbatim") {
            val values = listOf("Say \"hi\"", "back\\slash", "Ünïcødé ✓", "Repeat", "Repeat", " leading space")
            script.answer("POST", saved())

            repositoryFor().replaceList(UUID, MetadataListKind.Genres, values)

            valuesUnder(nextRequest(), "genres") shouldContainExactly values
        }

        it("TC-10: posts to the overrides route for the book, encoding the uuid as one segment") {
            script.answer("POST", saved())

            repositoryFor().replaceList("a/b?c", MetadataListKind.Genres, listOf("A"))

            val request = nextRequest()
            request.method shouldBe "POST"
            request.url.encodedPath shouldBe "/api/ebooks/a%2Fb%3Fc/overrides"
            request.headers["Content-Type"] shouldStartWith "application/json"
        }

        it("TC-11: decodes the merged record from a 200") {
            script.answer("POST", saved())

            val result = repositoryFor().replaceList(UUID, MetadataListKind.Genres, listOf("A"))

            val book: Book = (result as MetadataWriteResult.Saved).book
            book.uuid shouldBe "u-5"
            book.title shouldBe "T"
            book.genres shouldBe listOf("A")
            book.subjects shouldBe listOf("x")
        }

        it("TC-12: maps 401, 403 and 404") {
            script.answer("POST", reply(401))
            script.answer("POST", reply(403))
            script.answer("POST", reply(404))
            val repository = repositoryFor()

            repository.replaceList(UUID, MetadataListKind.Genres, listOf("A")) shouldBe
                MetadataWriteResult.Failure(MetadataError.SessionExpired)
            repository.replaceList(UUID, MetadataListKind.Genres, listOf("A")) shouldBe
                MetadataWriteResult.Failure(MetadataError.Forbidden)
            repository.replaceList(UUID, MetadataListKind.Genres, listOf("A")) shouldBe
                MetadataWriteResult.Failure(MetadataError.NotFound)
        }

        it("TC-13: maps other non-2xx to Http with the server message") {
            script.answer("POST", reply(400, "Too many genres"))
            script.answer("POST", reply(500, ""))
            val repository = repositoryFor()

            repository.replaceList(UUID, MetadataListKind.Genres, listOf("A")) shouldBe
                MetadataWriteResult.Failure(MetadataError.Http(400, "Too many genres"))
            repository.replaceList(UUID, MetadataListKind.Genres, listOf("A")) shouldBe
                MetadataWriteResult.Failure(MetadataError.Http(500, null))
        }

        it("TC-14: an undecodable 200 is UnexpectedResponse") {
            script.answer("POST", reply(200, "{\"nope\":1}"))

            repositoryFor().replaceList(UUID, MetadataListKind.Genres, listOf("A")) shouldBe
                MetadataWriteResult.Failure(MetadataError.UnexpectedResponse)
        }

        it("TC-15: maps IO errors to Unreachable") {
            // Two drops, in case the HTTP client retries the POST once on a dropped connection.
            script.answer("POST", dropped())
            script.answer("POST", dropped())

            repositoryFor().replaceList(UUID, MetadataListKind.Genres, listOf("A")) shouldBe
                MetadataWriteResult.Failure(MetadataError.Unreachable)
        }

        it("TC-15: rethrows the caller's cancellation instead of mapping it") {
            val release = CountDownLatch(1)
            script.answer("POST", saved(), gate = release)
            val repository = repositoryFor(held = true)
            val caller = newScope(Dispatchers.Default).async {
                repository.replaceList(UUID, MetadataListKind.Genres, listOf("A"))
            }
            nextRequest()

            caller.cancel()

            shouldThrow<CancellationException> { caller.await() }
            release.countDown()
        }

        it("TC-16: NoServer without a base URL and NotFound for a blank uuid, with no request made") {
            val noServer = repositoryFor(baseUrl = null)
            noServer.replaceList(UUID, MetadataListKind.Genres, listOf("A")) shouldBe MetadataWriteResult.NoServer

            repositoryFor().replaceList("  ", MetadataListKind.Genres, listOf("A")) shouldBe
                MetadataWriteResult.Failure(MetadataError.NotFound)

            server.requestCount shouldBe 0
        }
    }

    describe("getVocabulary") {

        it("TC-17: reads genres and tags, in server order, with a missing count as 0") {
            script.answer("GET", reply(200, "[{\"name\":\"Noir\",\"count\":3},{\"name\":\"Epic\"}]"))
            script.answer("GET", reply(200, "[{\"name\":\"Cozy\",\"count\":9}]"))
            val repository = repositoryFor()

            repository.getVocabulary(MetadataListKind.Genres) shouldBe VocabularyResult.Success(
                listOf(VocabularyEntry("Noir", 3), VocabularyEntry("Epic", 0)),
            )
            nextRequest().url.encodedPath shouldBe "/api/genres"

            repository.getVocabulary(MetadataListKind.Tags) shouldBe
                VocabularyResult.Success(listOf(VocabularyEntry("Cozy", 9)))
            nextRequest().url.encodedPath shouldBe "/api/tags"
        }

        it("TC-18: maps failures like replaceList") {
            script.answer("GET", reply(401))
            script.answer("GET", reply(403))
            script.answer("GET", reply(404))
            script.answer("GET", reply(500, "boom"))
            script.answer("GET", reply(200, "{\"not\":\"a list\"}"))
            script.answer("GET", dropped())
            script.answer("GET", dropped())
            val repository = repositoryFor()

            repository.getVocabulary(MetadataListKind.Genres) shouldBe
                VocabularyResult.Failure(MetadataError.SessionExpired)
            repository.getVocabulary(MetadataListKind.Genres) shouldBe
                VocabularyResult.Failure(MetadataError.Forbidden)
            repository.getVocabulary(MetadataListKind.Genres) shouldBe
                VocabularyResult.Failure(MetadataError.NotFound)
            repository.getVocabulary(MetadataListKind.Genres) shouldBe
                VocabularyResult.Failure(MetadataError.Http(500, "boom"))
            repository.getVocabulary(MetadataListKind.Genres) shouldBe
                VocabularyResult.Failure(MetadataError.UnexpectedResponse)
            repository.getVocabulary(MetadataListKind.Genres) shouldBe
                VocabularyResult.Failure(MetadataError.Unreachable)
            repositoryFor(baseUrl = null).getVocabulary(MetadataListKind.Tags) shouldBe VocabularyResult.NoServer
        }
    }

    describe("write chain") {

        it("TC-19: sends in tap order with one request in flight") {
            val gate = CountDownLatch(1)
            script.answer("POST", saved(), gate = gate)
            script.answer("POST", saved())
            val repository = repositoryFor(held = true)
            val callers = newScope(Dispatchers.Unconfined)

            val first = callers.async { repository.replaceList(UUID, MetadataListKind.Genres, listOf("A1")) }
            val firstRequest = nextRequest()
            val second = callers.async { repository.replaceList(UUID, MetadataListKind.Genres, listOf("B")) }

            // The second save waits behind the held first one, so the server has seen only the first.
            server.requestCount shouldBe 1
            gate.countDown()
            (awaitResult(first) is MetadataWriteResult.Saved) shouldBe true
            (awaitResult(second) is MetadataWriteResult.Saved) shouldBe true

            bodyOf(firstRequest) shouldBe "{\"genres\":[\"A1\"]}"
            bodyOf(nextRequest()) shouldBe "{\"genres\":[\"B\"]}"
            script.maxAnswering.get() shouldBe 1
        }

        it("TC-20: only the newest queued list is sent") {
            val gate = CountDownLatch(1)
            script.answer("POST", saved(), gate = gate)
            script.answer("POST", saved())
            val repository = repositoryFor(held = true)
            val callers = newScope(Dispatchers.Unconfined)

            val a = callers.async { repository.replaceList(UUID, MetadataListKind.Genres, listOf("A")) }
            nextRequest()
            val b = callers.async { repository.replaceList(UUID, MetadataListKind.Genres, listOf("A", "B")) }
            val c = callers.async { repository.replaceList(UUID, MetadataListKind.Genres, listOf("A", "B", "C")) }
            val d = callers.async { repository.replaceList(UUID, MetadataListKind.Genres, listOf("A", "B", "C", "D")) }
            gate.countDown()

            (awaitResult(a) is MetadataWriteResult.Saved) shouldBe true
            awaitResult(b) shouldBe MetadataWriteResult.Superseded
            awaitResult(c) shouldBe MetadataWriteResult.Superseded
            (awaitResult(d) is MetadataWriteResult.Saved) shouldBe true

            server.requestCount shouldBe 2
            valuesUnder(nextRequest(), "genres") shouldContainExactly listOf("A", "B", "C", "D")
            script.maxAnswering.get() shouldBe 1
        }

        it("TC-21: a request already sent still returns its own result") {
            val gate = CountDownLatch(1)
            script.answer("POST", saved(), gate = gate)
            script.answer("POST", saved())
            val repository = repositoryFor(held = true)
            val callers = newScope(Dispatchers.Unconfined)

            val a = callers.async { repository.replaceList(UUID, MetadataListKind.Genres, listOf("A")) }
            nextRequest()
            val b = callers.async { repository.replaceList(UUID, MetadataListKind.Genres, listOf("B")) }
            gate.countDown()

            (awaitResult(a) is MetadataWriteResult.Saved) shouldBe true
            (awaitResult(b) is MetadataWriteResult.Saved) shouldBe true
        }

        it("TC-21: a request already sent still returns its failure, not Superseded") {
            val gate = CountDownLatch(1)
            script.answer("POST", reply(500, "down"), gate = gate)
            script.answer("POST", saved())
            val repository = repositoryFor(held = true)
            val callers = newScope(Dispatchers.Unconfined)

            val a = callers.async { repository.replaceList(UUID, MetadataListKind.Genres, listOf("A")) }
            nextRequest()
            val b = callers.async { repository.replaceList(UUID, MetadataListKind.Genres, listOf("B")) }
            gate.countDown()

            awaitResult(a) shouldBe MetadataWriteResult.Failure(MetadataError.Http(500, "down"))
            (awaitResult(b) is MetadataWriteResult.Saved) shouldBe true
        }

        it("TC-22: genres and tags do not wait on each other") {
            val gate = CountDownLatch(1)
            script.answer("POST", saved(), gate = gate)
            script.answer("POST", saved())
            val repository = repositoryFor(held = true)
            val callers = newScope(Dispatchers.Unconfined)

            val genres = callers.async { repository.replaceList(UUID, MetadataListKind.Genres, listOf("G")) }
            val genresRequest = nextRequest()
            val tags = callers.async { repository.replaceList(UUID, MetadataListKind.Tags, listOf("T")) }

            // The tags save completes while the genres save is still held.
            (awaitResult(tags) is MetadataWriteResult.Saved) shouldBe true
            gate.countDown()
            (awaitResult(genres) is MetadataWriteResult.Saved) shouldBe true

            bodyOf(genresRequest) shouldBe "{\"genres\":[\"G\"]}"
            bodyOf(nextRequest()) shouldBe "{\"subjects\":[\"T\"]}"
        }

        it("TC-23: different books do not wait on each other") {
            val gate = CountDownLatch(1)
            script.answer("POST", saved(), gate = gate)
            script.answer("POST", saved())
            val repository = repositoryFor(held = true)
            val callers = newScope(Dispatchers.Unconfined)

            val book1 = callers.async { repository.replaceList("book-1", MetadataListKind.Genres, listOf("A")) }
            nextRequest()
            val book2 = callers.async { repository.replaceList("book-2", MetadataListKind.Genres, listOf("B")) }

            (awaitResult(book2) is MetadataWriteResult.Saved) shouldBe true
            gate.countDown()
            (awaitResult(book1) is MetadataWriteResult.Saved) shouldBe true
        }

        it("TC-24: survives cancellation of the caller") {
            val gate = CountDownLatch(1)
            script.answer("POST", saved(), gate = gate)
            script.answer("POST", saved())
            val repository = repositoryFor(held = true)
            val callers = newScope(Dispatchers.Default)

            val first = callers.async { repository.replaceList(UUID, MetadataListKind.Genres, listOf("A")) }
            nextRequest()
            first.cancel()
            gate.countDown()

            // The cancelled save still reached the server, and a later save for the same key is still sent.
            val later = callers.async { repository.replaceList(UUID, MetadataListKind.Genres, listOf("B")) }
            (awaitResult(later) is MetadataWriteResult.Saved) shouldBe true
            server.requestCount shouldBe 2
        }

        it("TC-25: a failure does not block the queue") {
            val gate = CountDownLatch(1)
            script.answer("POST", reply(500, "down"), gate = gate)
            script.answer("POST", saved())
            val repository = repositoryFor(held = true)
            val callers = newScope(Dispatchers.Unconfined)

            val a = callers.async { repository.replaceList(UUID, MetadataListKind.Genres, listOf("A")) }
            nextRequest()
            val b = callers.async { repository.replaceList(UUID, MetadataListKind.Genres, listOf("B")) }
            gate.countDown()

            awaitResult(a) shouldBe MetadataWriteResult.Failure(MetadataError.Http(500, "down"))
            (awaitResult(b) is MetadataWriteResult.Saved) shouldBe true
        }
    }
})
