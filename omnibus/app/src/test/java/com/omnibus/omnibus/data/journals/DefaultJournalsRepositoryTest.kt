package com.omnibus.omnibus.data.journals

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.TestClientInfo
import com.omnibus.omnibus.data.network.TestAuthTokenProvider
import com.omnibus.omnibus.data.network.TestServerBaseUrlProvider
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
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

/** A row as the server writes it, in server order. The author is 7 and the text names the [id]. */
private fun row(id: Long): String =
    """{"id":$id,"author_id":7,"author_name":"Ann","body_md":"b$id","status":"published","created_at":1761264000}"""

/** The entry [row] decodes to. */
private fun entry(id: Long) = JournalEntry(
    id = id,
    authorId = 7,
    authorName = "Ann",
    bodyMd = "b$id",
    isDraft = false,
    createdAtMillis = 1_761_264_000_000L,
)

private fun loaded(vararg ids: Long) = JournalsState.Loaded(ids.map { entry(it) })

private fun reply(code: Int, body: String = ""): MockResponse = MockResponse.Builder().code(code).body(body).build()

/** A connection that drops before any response, which reads as Failed too. */
private fun dropped(): MockResponse = MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()

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
private class Observed(repository: JournalsRepository, uuid: String, scope: CoroutineScope) {
    val states = CopyOnWriteArrayList<JournalsState>()
    private val job: Job = scope.launch { repository.getJournalsFlow(uuid).collect { states += it } }

    /** Waits until the latest emission is [expected]. */
    suspend fun awaitLast(expected: JournalsState) {
        withTimeout(5_000) {
            while (states.lastOrNull() != expected) delay(10)
        }
    }

    fun stop() = job.cancel()
}

class DefaultJournalsRepositoryTest : DescribeSpec({

    lateinit var server: MockWebServer
    lateinit var script: ScriptedDispatcher
    val scopes = mutableListOf<CoroutineScope>()

    /** A new scope that is cancelled after the test. */
    fun newScope(dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher).also { scopes += it }

    /**
     * A repository for [baseUrl] (null means no server is saved). [held] runs HTTP on real threads, which lets a test hold
     * a request open. Otherwise everything runs unconfined on the calling thread.
     */
    fun repositoryFor(baseUrl: HttpUrl? = server.url("/"), held: Boolean = false): DefaultJournalsRepository {
        val scope = newScope(if (held) Dispatchers.Default else Dispatchers.Unconfined)
        return DefaultJournalsRepository(
            ioDispatcher = if (held) Dispatchers.IO else Dispatchers.Unconfined,
            omnibusHttpClient = OmnibusHttpClient(
                baseUrlProvider = TestServerBaseUrlProvider(baseUrl = baseUrl),
                authTokenProvider = TestAuthTokenProvider(token = "secret"),
                omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
                authClientMetadataInterceptor = AuthClientMetadataInterceptor(TestClientInfo()),
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
    }

    afterEach {
        scopes.forEach { it.cancel() }
        scopes.clear()
        server.close()
    }

    describe("reading the journal") {

        it("TC-6: entries keep the server's order, and the GET uses the encoded uuid and the bearer token") {
            script.answer("GET", reply(200, "[${row(9)},${row(2)},${row(5)}]"))

            repositoryFor().getJournalsFlow("book 1/é").first() shouldBe loaded(9, 2, 5)

            val request = nextRequest()
            request.method shouldBe "GET"
            request.url.encodedPath shouldBe "/api/journals/book/book%201%2F%C3%A9"
            request.headers["Authorization"] shouldBe "Bearer secret"
        }

        it("TC-7: the first collection fetches once; a later collector gets the loaded list without a request") {
            val repository = repositoryFor(held = true)
            val gate = CountDownLatch(1)
            script.answer("GET", reply(200, "[${row(1)}]"), gate)
            val first = Observed(repository, UUID, newScope(Dispatchers.Default))
            nextRequest()
            first.awaitLast(JournalsState.Loading)

            gate.countDown()
            first.awaitLast(loaded(1))
            first.states.first() shouldBe JournalsState.Loading

            val second = Observed(repository, UUID, newScope(Dispatchers.Default))
            second.awaitLast(loaded(1))
            server.requestCount shouldBe 1
        }

        it("TC-8: while the first GET is held, more collectors and refreshes join it: one request, one Loaded") {
            val repository = repositoryFor(held = true)
            val scope = newScope(Dispatchers.Default)
            val gate = CountDownLatch(1)
            script.answer("GET", reply(200, "[${row(1)}]"), gate)
            val first = Observed(repository, UUID, newScope(Dispatchers.Default))
            nextRequest()

            val second = Observed(repository, UUID, newScope(Dispatchers.Default))
            val third = Observed(repository, UUID, newScope(Dispatchers.Default))
            val refreshA = scope.async { repository.refresh(UUID) }
            val refreshB = scope.async { repository.refresh(UUID) }
            delay(200) // give every caller time to join the GET in flight before it completes
            gate.countDown()
            refreshA.await()
            refreshB.await()

            first.awaitLast(loaded(1))
            second.awaitLast(loaded(1))
            third.awaitLast(loaded(1))
            server.requestCount shouldBe 1
        }

        it("TC-9: an empty array is a Loaded empty list, not Failed") {
            script.answer("GET", reply(200, "[]"))

            repositoryFor().getJournalsFlow(UUID).first() shouldBe JournalsState.Loaded(emptyList())
        }

        it("TC-10: every non-2xx answer and a dropped connection read as Failed, and nothing is thrown") {
            listOf(
                { reply(401) },
                { reply(404) },
                { reply(500) },
                { dropped() },
            ).forEach { answer ->
                script.answer("GET", answer())
                repositoryFor().getJournalsFlow(UUID).first() shouldBe JournalsState.Failed
            }
        }

        it("TC-11: a body that does not decode reads as Failed: malformed JSON, an object, or a row missing id") {
            listOf(
                "{not json",
                "{}",
                "hello",
                """[{"author_id":7,"body_md":"no id"}]""",
                """[{"id":"abc","author_id":7}]""",
            ).forEach { body ->
                script.answer("GET", reply(200, body))
                repositoryFor().getJournalsFlow(UUID).first() shouldBe JournalsState.Failed
            }
        }

        it("TC-12: no server, or a blank uuid, is Failed through the flow and the refresh, and sends no request") {
            val before = server.requestCount
            listOf(
                repositoryFor(baseUrl = null) to UUID,
                repositoryFor() to "",
                repositoryFor() to "   ",
            ).forEach { (repository, uuid) ->
                repository.getJournalsFlow(uuid).first() shouldBe JournalsState.Failed
                repository.refresh(uuid)
            }
            server.requestCount shouldBe before
        }

        it("TC-13: a failed refresh keeps the loaded list, with no Loading or Failed emitted") {
            val repository = repositoryFor()
            script.answer("GET", reply(200, "[${row(1)}]"))
            val observed = Observed(repository, UUID, newScope(Dispatchers.Unconfined))
            observed.awaitLast(loaded(1))
            // Emissions from here on: the failed refresh must add nothing, and the good one adds only its list.
            val mark = observed.states.size

            script.answer("GET", reply(500))
            repository.refresh(UUID)
            observed.states.drop(mark) shouldBe emptyList()

            script.answer("GET", reply(200, "[${row(2)},${row(3)}]"))
            repository.refresh(UUID)
            observed.states.drop(mark) shouldBe listOf(loaded(2, 3))
        }

        it("TC-14: a refresh from Failed shows Loading while it runs, then Loaded or Failed") {
            val repository = repositoryFor(held = true)
            script.answer("GET", reply(500))
            val observed = Observed(repository, UUID, newScope(Dispatchers.Default))
            observed.awaitLast(JournalsState.Failed)
            nextRequest() // the first fetch's request, drained so the next one is the refresh

            val mark = observed.states.size
            val gate = CountDownLatch(1)
            script.answer("GET", reply(200, "[${row(1)}]"), gate)
            val refresh = newScope(Dispatchers.Default).async { repository.refresh(UUID) }
            nextRequest()
            observed.awaitLast(JournalsState.Loading)
            gate.countDown()
            refresh.await()
            observed.states.drop(mark) shouldBe listOf(JournalsState.Loading, loaded(1))

            // The same again, answered with a failure: Failed, Loading, Failed.
            val failingRepository = repositoryFor(held = true)
            script.answer("GET", reply(500))
            val failing = Observed(failingRepository, UUID, newScope(Dispatchers.Default))
            failing.awaitLast(JournalsState.Failed)
            nextRequest()
            val failingMark = failing.states.size
            val gateAgain = CountDownLatch(1)
            script.answer("GET", reply(500), gateAgain)
            val retry = newScope(Dispatchers.Default).async { failingRepository.refresh(UUID) }
            nextRequest()
            failing.awaitLast(JournalsState.Loading)
            gateAgain.countDown()
            retry.await()
            failing.states.drop(failingMark) shouldBe listOf(JournalsState.Loading, JournalsState.Failed)
        }

        it("TC-15: each book's state is its own: one book failing does not touch another") {
            val repository = repositoryFor()
            script.answer("GET", reply(500))
            val a = Observed(repository, "A", newScope(Dispatchers.Unconfined))
            a.awaitLast(JournalsState.Failed)
            script.answer("GET", reply(200, "[${row(1)}]"))
            val b = Observed(repository, "B", newScope(Dispatchers.Unconfined))
            b.awaitLast(loaded(1))

            a.states.none { it is JournalsState.Loaded } shouldBe true
            a.states.last() shouldBe JournalsState.Failed
            b.states.none { it == JournalsState.Failed } shouldBe true
            b.states.last() shouldBe loaded(1)
            server.requestCount shouldBe 2
        }

        it("TC-16: a cancelled caller does not cancel the shared fetch, and Failed is not sticky") {
            val repository = repositoryFor(held = true)
            val scope = newScope(Dispatchers.Default)
            val gate = CountDownLatch(1)
            script.answer("GET", reply(200, "[${row(1)}]"), gate)
            val observed = Observed(repository, UUID, newScope(Dispatchers.Default))
            val caller = scope.launch { repository.refresh(UUID) }
            nextRequest()

            caller.cancelAndJoin()
            caller.isCancelled shouldBe true

            gate.countDown()
            observed.awaitLast(loaded(1))

            // Failed is not sticky: after a failure, a new collector fetches again.
            val failingRepository = repositoryFor()
            script.answer("GET", reply(500))
            script.answer("GET", reply(200, "[${row(1)}]"))
            val first = Observed(failingRepository, UUID, newScope(Dispatchers.Unconfined))
            first.awaitLast(JournalsState.Failed)
            first.stop()

            val second = Observed(failingRepository, UUID, newScope(Dispatchers.Unconfined))
            second.awaitLast(loaded(1))
            server.requestCount shouldBe 3
        }
    }
})
