package com.omnibus.omnibus.data.readstatus

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.TestClientInfo
import com.omnibus.omnibus.data.network.TestAuthTokenProvider
import com.omnibus.omnibus.data.network.TestServerBaseUrlProvider
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okhttp3.HttpUrl

private const val UUID = "book-1"

/** `updated_at` in the server's examples, in seconds and in millis. */
private const val SECONDS_UPDATED = 1_761_775_445L
private const val MILLIS_UPDATED = 1_761_775_445_000L

/** `finished_at` for a book finished earlier, in seconds and in millis. */
private const val SECONDS_FINISHED = 1_761_000_000L
private const val MILLIS_FINISHED = 1_761_000_000_000L

/** The fixed clock's "now" for optimistic values. */
private const val NOW = 1_000_000L

/** A server record body, as the server writes it. The `updated_at_iso` field is left out. */
private fun body(
    status: String,
    updatedAt: Long? = SECONDS_UPDATED,
    finishedAt: Long? = null,
    bookUuid: String = UUID,
): String {
    val fields = listOfNotNull(
        "\"book_uuid\":\"$bookUuid\"",
        "\"status\":\"$status\"",
        updatedAt?.let { "\"updated_at\":$it" },
        finishedAt?.let { "\"finished_at\":$it" },
    )
    return fields.joinToString(prefix = "{", postfix = "}", separator = ",")
}

private fun reply(code: Int, body: String = ""): MockResponse = MockResponse.Builder().code(code).body(body).build()

private fun known(status: ReadStatus, updated: Long? = MILLIS_UPDATED, finished: Long? = null) =
    ReadStatusState.Known(ReadStatusRecord(status, updated, finished))

private fun record(status: ReadStatus, updated: Long? = MILLIS_UPDATED, finished: Long? = null) =
    ReadStatusRecord(status, updated, finished)

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

/** The `status` field of a recorded PUT body. */
private fun statusOf(request: RecordedRequest): String =
    Json.parseToJsonElement(request.body!!.utf8()).jsonObject.getValue("status").jsonPrimitive.content

/** Collects a book's flow in the background, recording every emission in order. */
private class Observed(repository: ReadStatusRepository, uuid: String, scope: CoroutineScope) {
    val states = CopyOnWriteArrayList<ReadStatusState>()
    private val job: Job = scope.launch { repository.getReadStatusFlow(uuid).collect { states += it } }

    /** Waits until the latest emission is [expected]. */
    suspend fun awaitLast(expected: ReadStatusState) {
        withTimeout(5_000) {
            while (states.lastOrNull() != expected) delay(10)
        }
    }

    fun stop() = job.cancel()
}

class DefaultReadStatusRepositoryTest : DescribeSpec({

    lateinit var server: MockWebServer
    lateinit var script: ScriptedDispatcher
    var now = NOW
    val scopes = mutableListOf<CoroutineScope>()

    /** A new scope that is cancelled after the test. */
    fun newScope(dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher).also { scopes += it }

    /**
     * A repository for [baseUrl] (null means no server is saved). [held] runs HTTP on real threads, which lets a test
     * hold a request open. Otherwise everything runs unconfined on the calling thread.
     */
    fun repositoryFor(baseUrl: HttpUrl? = server.url("/"), held: Boolean = false): DefaultReadStatusRepository {
        val scope = newScope(if (held) Dispatchers.Default else Dispatchers.Unconfined)
        return DefaultReadStatusRepository(
            ioDispatcher = if (held) Dispatchers.IO else Dispatchers.Unconfined,
            omnibusHttpClient = OmnibusHttpClient(
                baseUrlProvider = TestServerBaseUrlProvider(baseUrl = baseUrl),
                authTokenProvider = TestAuthTokenProvider(token = "secret"),
                omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
                authClientMetadataInterceptor = AuthClientMetadataInterceptor(TestClientInfo()),
            ),
            timeSource = TimeSource { now },
            applicationScope = scope,
        )
    }

    /** Waits for the next request the server received, in arrival order. */
    fun nextRequest(): RecordedRequest =
        checkNotNull(server.takeRequest(5, TimeUnit.SECONDS)) { "No request reached the server" }

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    beforeEach {
        now = NOW
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

    describe("reading the status") {

        it("TC-1: a reading record maps its updated_at seconds to millis") {
            script.answer("GET", reply(200, body("reading")))

            repositoryFor().fetchStatus(UUID) shouldBe known(ReadStatus.Reading)
        }

        it("TC-2: finished_at seconds map to millis, and updated_at_iso is ignored") {
            val json = "{\"book_uuid\":\"$UUID\",\"status\":\"finished\",\"updated_at\":$SECONDS_UPDATED," +
                "\"updated_at_iso\":\"1999-01-01T00:00:00Z\",\"finished_at\":$SECONDS_FINISHED}"
            script.answer("GET", reply(200, json))

            repositoryFor().fetchStatus(UUID) shouldBe
                known(ReadStatus.Finished, MILLIS_UPDATED, MILLIS_FINISHED)
        }

        it("TC-3: a null body is a missing row, which reads as Known Unread") {
            script.answer("GET", reply(200, "null"))
            val repository = repositoryFor()

            repository.fetchStatus(UUID) shouldBe known(ReadStatus.Unread, updated = null, finished = null)
            repository.getReadStatusFlow(UUID).first() shouldBe known(ReadStatus.Unread, updated = null, finished = null)
        }

        it("TC-4: an unknown or missing token reads as Unread, and the token match ignores case") {
            val repository = repositoryFor()

            script.answer("GET", reply(200, body("abandoned")))
            repository.fetchStatus(UUID) shouldBe known(ReadStatus.Unread)

            script.answer("GET", reply(200, "{\"book_uuid\":\"$UUID\",\"updated_at\":$SECONDS_UPDATED}"))
            repository.fetchStatus(UUID) shouldBe known(ReadStatus.Unread)

            script.answer("GET", reply(200, body("FINISHED")))
            repository.fetchStatus(UUID) shouldBe known(ReadStatus.Finished, MILLIS_UPDATED, finished = null)
        }

        it("TC-5: a non-2xx read is Failed, and nothing is thrown") {
            listOf(401, 404, 500).forEach { code ->
                // The flow's first collection reads once, and the explicit fetch reads again.
                script.answer("GET", reply(code))
                script.answer("GET", reply(code))
                val repository = repositoryFor()

                repository.getReadStatusFlow(UUID).first() shouldBe ReadStatusState.Failed
                repository.fetchStatus(UUID) shouldBe ReadStatusState.Failed
            }
        }

        it("TC-6: a malformed, non-JSON or wrong-shaped body is Failed") {
            listOf("not json", "[1,2]", "{\"status\":5}", "<html></html>").forEach { body ->
                script.answer("GET", reply(200, body))

                repositoryFor().fetchStatus(UUID) shouldBe ReadStatusState.Failed
            }
        }

        it("TC-7: no configured server is Failed, and no request is sent") {
            val before = server.requestCount

            repositoryFor(baseUrl = null).fetchStatus(UUID) shouldBe ReadStatusState.Failed
            server.requestCount shouldBe before
        }

        it("TC-8: a dropped connection on the read is Failed, and nothing is thrown") {
            script.answer("GET", MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build())

            repositoryFor().fetchStatus(UUID) shouldBe ReadStatusState.Failed
        }

        it("TC-9: a blank uuid is Failed for the flow, the fetch and the refresh, and sends no request") {
            val before = server.requestCount
            val repository = repositoryFor()

            repository.getReadStatusFlow("").first() shouldBe ReadStatusState.Failed
            repository.fetchStatus("   ") shouldBe ReadStatusState.Failed
            repository.refresh("")
            server.requestCount shouldBe before
        }

        it("TC-10: the GET uses the encoded uuid segment and the bearer token, and the state is keyed by the requested uuid") {
            script.answer("GET", reply(200, body("reading", bookUuid = "canonical-uuid")))
            val repository = repositoryFor()

            repository.fetchStatus("book one") shouldBe known(ReadStatus.Reading)

            val request = nextRequest()
            request.method shouldBe "GET"
            request.url.encodedPath shouldBe "/api/read-status/book%20one"
            request.headers["Authorization"] shouldBe "Bearer secret"
            repository.getReadStatusFlow("book one").first() shouldBe known(ReadStatus.Reading)
        }

        it("TC-11: the first emission is Unknown, one GET starts on the first collect, and a Known state fetches nothing") {
            val repository = repositoryFor(held = true)
            val gate = CountDownLatch(1)
            script.answer("GET", reply(200, body("reading")), gate)
            val first = Observed(repository, UUID, newScope(Dispatchers.Default))

            nextRequest()
            first.awaitLast(ReadStatusState.Unknown)
            first.states.first() shouldBe ReadStatusState.Unknown
            gate.countDown()
            first.awaitLast(known(ReadStatus.Reading))

            val second = Observed(repository, UUID, newScope(Dispatchers.Default))
            second.awaitLast(known(ReadStatus.Reading))
            server.requestCount shouldBe 1
        }

        it("TC-12: an in-flight GET is shared, and a refresh after it completes fetches again") {
            val repository = repositoryFor(held = true)
            val scope = newScope(Dispatchers.Default)
            val gate = CountDownLatch(1)
            script.answer("GET", reply(200, body("reading")), gate)
            val observed = Observed(repository, UUID, scope)
            nextRequest()

            val refresh = scope.async { repository.refresh(UUID) }
            val fetch = scope.async { repository.fetchStatus(UUID) }
            delay(200) // give both callers time to join the GET in flight before it completes
            gate.countDown()
            refresh.await()
            fetch.await() shouldBe known(ReadStatus.Reading)
            observed.awaitLast(known(ReadStatus.Reading))
            server.requestCount shouldBe 1

            script.answer("GET", reply(200, body("finished")))
            repository.refresh(UUID)
            server.requestCount shouldBe 2
            observed.awaitLast(known(ReadStatus.Finished))
        }

        it("TC-13: a failed read keeps a Known state, but the caller still gets Failed") {
            val repository = repositoryFor()
            script.answer("GET", reply(200, body("reading")))
            repository.fetchStatus(UUID) shouldBe known(ReadStatus.Reading)

            script.answer("GET", reply(500))
            repository.refresh(UUID)
            script.answer("GET", MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build())
            repository.fetchStatus(UUID) shouldBe ReadStatusState.Failed

            repository.getReadStatusFlow(UUID).first() shouldBe known(ReadStatus.Reading)
        }

        it("TC-14: an Unknown state that fails becomes Failed, then recovers to Known") {
            val repository = repositoryFor(held = true)
            val gate = CountDownLatch(1)
            script.answer("GET", reply(500), gate)
            val observed = Observed(repository, UUID, newScope(Dispatchers.Default))
            nextRequest()
            observed.awaitLast(ReadStatusState.Unknown)

            gate.countDown()
            observed.awaitLast(ReadStatusState.Failed)

            script.answer("GET", reply(200, body("reading")))
            repository.refresh(UUID)
            observed.awaitLast(known(ReadStatus.Reading))
            observed.states shouldBe listOf(ReadStatusState.Unknown, ReadStatusState.Failed, known(ReadStatus.Reading))
        }
    }

    describe("writing the status") {

        it("TC-15: PUT sends the uuid and lowercase token as JSON to api/read-status") {
            val repository = repositoryFor()

            listOf(ReadStatus.Unread, ReadStatus.Reading, ReadStatus.Finished).forEach { status ->
                val token = status.name.lowercase()
                script.answer("PUT", reply(200, body(token)))

                repository.setStatus(UUID, status) shouldBe ReadStatusWriteResult.Saved(record(status))

                val request = nextRequest()
                request.method shouldBe "PUT"
                request.url.encodedPath shouldBe "/api/read-status"
                request.headers["Content-Type"]!!.shouldStartWith("application/json")
                Json.parseToJsonElement(request.body!!.utf8()) shouldBe
                    Json.parseToJsonElement("{\"book_uuid\":\"$UUID\",\"status\":\"$token\"}")
            }
        }

        it("TC-16: a blank uuid write fails with BookNotFound, sends no request and leaves the state alone") {
            val before = server.requestCount
            val repository = repositoryFor()

            repository.setStatus("", ReadStatus.Finished) shouldBe ReadStatusWriteResult.Failed(ReadStatusError.BookNotFound)
            server.requestCount shouldBe before
            repository.getReadStatusFlow("").first() shouldBe ReadStatusState.Failed
        }

        it("TC-17: the optimistic value is visible to every collector before the PUT returns") {
            val repository = repositoryFor(held = true)
            script.answer("GET", reply(200, body("unread")))
            repository.fetchStatus(UUID)
            nextRequest()
            val gate = CountDownLatch(1)
            script.answer("PUT", reply(200, body("reading")), gate)
            val scope = newScope(Dispatchers.Default)
            val write = scope.async { repository.setStatus(UUID, ReadStatus.Reading) }
            nextRequest()

            val first = Observed(repository, UUID, scope)
            val second = Observed(repository, UUID, scope)
            first.awaitLast(known(ReadStatus.Reading, NOW))
            second.awaitLast(known(ReadStatus.Reading, NOW))

            gate.countDown()
            write.await()
        }

        it("TC-18: the server record replaces the optimistic one and is returned") {
            val repository = repositoryFor()
            script.answer("PUT", reply(200, body("finished", SECONDS_UPDATED, SECONDS_FINISHED)))

            repository.setStatus(UUID, ReadStatus.Finished) shouldBe
                ReadStatusWriteResult.Saved(record(ReadStatus.Finished, MILLIS_UPDATED, MILLIS_FINISHED))
            repository.getReadStatusFlow(UUID).first() shouldBe
                known(ReadStatus.Finished, MILLIS_UPDATED, MILLIS_FINISHED)
        }

        it("TC-19: setting Finished again keeps finished_at and makes no request") {
            val repository = repositoryFor()
            script.answer("GET", reply(200, body("finished", SECONDS_UPDATED, SECONDS_FINISHED)))
            repository.fetchStatus(UUID)
            val before = server.requestCount

            repository.setStatus(UUID, ReadStatus.Finished) shouldBe ReadStatusWriteResult.Unchanged
            server.requestCount shouldBe before
            repository.getReadStatusFlow(UUID).first() shouldBe
                known(ReadStatus.Finished, MILLIS_UPDATED, MILLIS_FINISHED)
        }

        it("TC-20: moving from Finished to Reading or Unread clears finished_at, optimistically and on the server") {
            val repository = repositoryFor(held = true)
            script.answer("GET", reply(200, body("finished", SECONDS_UPDATED, SECONDS_FINISHED)))
            repository.fetchStatus(UUID)
            nextRequest()
            val scope = newScope(Dispatchers.Default)
            val observed = Observed(repository, UUID, scope)
            observed.awaitLast(known(ReadStatus.Finished, MILLIS_UPDATED, MILLIS_FINISHED))

            val gate = CountDownLatch(1)
            script.answer("PUT", reply(200, body("reading", SECONDS_UPDATED)), gate)
            val reading = scope.async { repository.setStatus(UUID, ReadStatus.Reading) }
            nextRequest()
            observed.awaitLast(known(ReadStatus.Reading, NOW, finished = null))
            gate.countDown()
            reading.await() shouldBe ReadStatusWriteResult.Saved(record(ReadStatus.Reading, MILLIS_UPDATED, null))

            script.answer("PUT", reply(200, body("finished", SECONDS_UPDATED, SECONDS_FINISHED)))
            repository.setStatus(UUID, ReadStatus.Finished)
            script.answer("PUT", reply(200, body("unread", SECONDS_UPDATED)))
            repository.setStatus(UUID, ReadStatus.Unread) shouldBe
                ReadStatusWriteResult.Saved(record(ReadStatus.Unread, MILLIS_UPDATED, null))
            repository.getReadStatusFlow(UUID).first() shouldBe known(ReadStatus.Unread, MILLIS_UPDATED, null)
        }

        it("TC-21: moving into Finished takes now as finished_at, and a second finish takes a new date") {
            val repository = repositoryFor(held = true)
            script.answer("GET", reply(200, body("unread")))
            repository.fetchStatus(UUID)
            nextRequest()
            val scope = newScope(Dispatchers.Default)
            val observed = Observed(repository, UUID, scope)
            observed.awaitLast(known(ReadStatus.Unread))

            val gate = CountDownLatch(1)
            script.answer("PUT", reply(200, body("finished", SECONDS_UPDATED, SECONDS_FINISHED)), gate)
            val finish = scope.async { repository.setStatus(UUID, ReadStatus.Finished) }
            nextRequest()
            observed.awaitLast(known(ReadStatus.Finished, NOW, finished = NOW))
            gate.countDown()
            finish.await() shouldBe
                ReadStatusWriteResult.Saved(record(ReadStatus.Finished, MILLIS_UPDATED, MILLIS_FINISHED))

            script.answer("PUT", reply(200, body("unread", SECONDS_UPDATED)))
            repository.setStatus(UUID, ReadStatus.Unread)

            now = 2_000_000L
            val secondGate = CountDownLatch(1)
            script.answer("PUT", reply(200, body("finished", 2_000_000L / 1_000L)), secondGate)
            val second = scope.async { repository.setStatus(UUID, ReadStatus.Finished) }
            nextRequest()
            observed.awaitLast(known(ReadStatus.Finished, 2_000_000L, finished = 2_000_000L))
            secondGate.countDown()
            second.await()
        }

        it("TC-22: choosing the current status makes no request, for each of the three statuses") {
            val repository = repositoryFor()
            ReadStatus.entries.forEach { status ->
                val uuid = "same-${status.name}"
                script.answer("GET", reply(200, body(status.name.lowercase())))
                repository.fetchStatus(uuid)
                val before = server.requestCount

                repository.setStatus(uuid, status) shouldBe ReadStatusWriteResult.Unchanged
                server.requestCount shouldBe before
            }
        }

        it("TC-23: the same-status guard applies only to a Known state, so writes while Unknown or Failed are sent") {
            val repository = repositoryFor()
            script.answer("PUT", reply(200, body("unread")))
            val before = server.requestCount

            repository.setStatus("unknown-book", ReadStatus.Unread) shouldBe
                ReadStatusWriteResult.Saved(record(ReadStatus.Unread))
            server.requestCount shouldBe before + 1

            script.answer("GET", reply(500))
            repository.fetchStatus("failed-book") shouldBe ReadStatusState.Failed
            script.answer("PUT", reply(200, body("unread")))
            repository.setStatus("failed-book", ReadStatus.Unread) shouldBe
                ReadStatusWriteResult.Saved(record(ReadStatus.Unread))
        }

        it("TC-24: each write failure maps to its typed error and rolls back to the previous state") {
            val repository = repositoryFor()
            script.answer("GET", reply(200, body("unread")))
            repository.fetchStatus(UUID)
            val previous = known(ReadStatus.Unread)

            repositoryFor(baseUrl = null).setStatus(UUID, ReadStatus.Reading) shouldBe
                ReadStatusWriteResult.Failed(ReadStatusError.NoServer)

            val failures = listOf(
                MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build() to ReadStatusError.Unreachable,
                reply(400) to ReadStatusError.Http(400),
                reply(401) to ReadStatusError.SessionExpired,
                reply(404) to ReadStatusError.BookNotFound,
                reply(500) to ReadStatusError.Http(500),
                reply(200, "garbage") to ReadStatusError.UnexpectedResponse,
                reply(200, "null") to ReadStatusError.UnexpectedResponse,
            )
            failures.forEach { (response, error) ->
                script.answer("PUT", response)

                repository.setStatus(UUID, ReadStatus.Reading) shouldBe ReadStatusWriteResult.Failed(error)
                repository.getReadStatusFlow(UUID).first() shouldBe previous
            }
        }

        it("TC-25: the rollback restores the exact previous state, including its times") {
            val repository = repositoryFor()
            val scope = newScope(Dispatchers.Unconfined)

            script.answer("GET", reply(200, body("finished", SECONDS_UPDATED, SECONDS_FINISHED)))
            repository.fetchStatus(UUID)
            val previousFinished = known(ReadStatus.Finished, MILLIS_UPDATED, MILLIS_FINISHED)
            val seen = CopyOnWriteArrayList<ReadStatusState>()
            scope.launch { repository.getReadStatusFlow(UUID).collect { seen += it } }
            script.answer("PUT", reply(500))
            repository.setStatus(UUID, ReadStatus.Unread) shouldBe ReadStatusWriteResult.Failed(ReadStatusError.Http(500))
            seen shouldContain known(ReadStatus.Unread, NOW, null)
            seen.last() shouldBe previousFinished

            script.answer("GET", reply(200, body("reading")))
            repository.refresh(UUID)
            script.answer("PUT", reply(500))
            repository.setStatus(UUID, ReadStatus.Finished)
            repository.getReadStatusFlow(UUID).first() shouldBe known(ReadStatus.Reading)

            // A Failed state rolls back to Failed: the flow's own read answers the 500 it gets on collection.
            script.answer("GET", reply(500))
            repository.fetchStatus("failed-rollback")
            script.answer("PUT", reply(500))
            repository.setStatus("failed-rollback", ReadStatus.Reading) shouldBe
                ReadStatusWriteResult.Failed(ReadStatusError.Http(500))
            script.answer("GET", reply(500))
            repository.getReadStatusFlow("failed-rollback").first() shouldBe ReadStatusState.Failed
        }

        it("TC-26: an earlier success does not overwrite a later write's optimistic value") {
            val repository = repositoryFor(held = true)
            script.answer("GET", reply(200, body("unread")))
            repository.fetchStatus(UUID)
            nextRequest()
            val scope = newScope(Dispatchers.Default)
            val observed = Observed(repository, UUID, scope)
            observed.awaitLast(known(ReadStatus.Unread))

            val gateA = CountDownLatch(1)
            val gateB = CountDownLatch(1)
            script.answer("PUT", reply(200, body("reading", SECONDS_UPDATED)), gateA)
            script.answer("PUT", reply(200, body("finished", SECONDS_UPDATED, SECONDS_FINISHED)), gateB)
            val a = scope.async { repository.setStatus(UUID, ReadStatus.Reading) }
            nextRequest()
            val b = scope.async { repository.setStatus(UUID, ReadStatus.Finished) }
            observed.awaitLast(known(ReadStatus.Finished, NOW, finished = NOW))

            gateA.countDown()
            a.await() shouldBe ReadStatusWriteResult.Saved(record(ReadStatus.Reading, MILLIS_UPDATED, null))
            repository.getReadStatusFlow(UUID).first() shouldBe known(ReadStatus.Finished, NOW, finished = NOW)

            gateB.countDown()
            b.await() shouldBe ReadStatusWriteResult.Saved(record(ReadStatus.Finished, MILLIS_UPDATED, MILLIS_FINISHED))
        }

        it("TC-27: an earlier failure does not roll back over a later write") {
            val repository = repositoryFor(held = true)
            script.answer("GET", reply(200, body("unread")))
            repository.fetchStatus(UUID)
            nextRequest()
            val scope = newScope(Dispatchers.Default)
            val observed = Observed(repository, UUID, scope)
            observed.awaitLast(known(ReadStatus.Unread))

            val gateA = CountDownLatch(1)
            val gateB = CountDownLatch(1)
            script.answer("PUT", reply(500), gateA)
            script.answer("PUT", reply(200, body("finished", SECONDS_UPDATED, SECONDS_FINISHED)), gateB)
            val a = scope.async { repository.setStatus(UUID, ReadStatus.Reading) }
            nextRequest()
            val b = scope.async { repository.setStatus(UUID, ReadStatus.Finished) }
            observed.awaitLast(known(ReadStatus.Finished, NOW, finished = NOW))

            gateA.countDown()
            a.await() shouldBe ReadStatusWriteResult.Failed(ReadStatusError.Http(500))
            repository.getReadStatusFlow(UUID).first() shouldBe known(ReadStatus.Finished, NOW, finished = NOW)

            gateB.countDown()
            b.await() shouldBe ReadStatusWriteResult.Saved(record(ReadStatus.Finished, MILLIS_UPDATED, MILLIS_FINISHED))
        }

        it("TC-28: a later failure rolls back to the last confirmed record, not to the superseded optimistic value") {
            val repository = repositoryFor(held = true)
            script.answer("GET", reply(200, body("unread")))
            repository.fetchStatus(UUID)
            nextRequest()
            val scope = newScope(Dispatchers.Default)

            val gateA = CountDownLatch(1)
            script.answer("PUT", reply(200, body("reading", SECONDS_UPDATED)), gateA)
            script.answer("PUT", reply(500))
            val a = scope.async { repository.setStatus(UUID, ReadStatus.Reading) }
            nextRequest()
            val b = scope.async { repository.setStatus(UUID, ReadStatus.Finished) }
            gateA.countDown()
            a.await() shouldBe ReadStatusWriteResult.Saved(record(ReadStatus.Reading))
            b.await() shouldBe ReadStatusWriteResult.Failed(ReadStatusError.Http(500))

            repository.getReadStatusFlow(UUID).first() shouldBe known(ReadStatus.Reading)
        }

        it("TC-29: writes for one uuid reach the server in call order, one at a time") {
            val repository = repositoryFor(held = true)
            val scope = newScope(Dispatchers.Default)
            val gateA = CountDownLatch(1)
            script.answer("PUT", reply(200, body("reading", SECONDS_UPDATED)), gateA)
            script.answer("PUT", reply(200, body("finished", SECONDS_UPDATED, SECONDS_FINISHED)))

            val a = scope.async { repository.setStatus(UUID, ReadStatus.Reading) }
            val firstRequest = nextRequest()
            val b = scope.async { repository.setStatus(UUID, ReadStatus.Finished) }

            server.takeRequest(300, TimeUnit.MILLISECONDS) shouldBe null
            gateA.countDown()
            a.await()
            b.await()

            statusOf(firstRequest) shouldBe "reading"
            statusOf(nextRequest()) shouldBe "finished"
        }

        it("TC-30: a GET answered after a newer write does not overwrite the write's value, but the caller gets its outcome") {
            val repository = repositoryFor(held = true)
            val scope = newScope(Dispatchers.Default)
            val gate = CountDownLatch(1)
            script.answer("GET", reply(200, body("unread")), gate)
            val fetch = scope.async { repository.fetchStatus(UUID) }
            nextRequest()

            script.answer("PUT", reply(200, body("finished", SECONDS_UPDATED, SECONDS_FINISHED)))
            repository.setStatus(UUID, ReadStatus.Finished)
            nextRequest()

            gate.countDown()
            fetch.await() shouldBe known(ReadStatus.Unread)
            repository.getReadStatusFlow(UUID).first() shouldBe
                known(ReadStatus.Finished, MILLIS_UPDATED, MILLIS_FINISHED)
        }

        it("TC-31: a cancelled caller leaves the write and the read to finish or roll back in the application scope") {
            val repository = repositoryFor(held = true)
            val scope = newScope(Dispatchers.Default)
            script.answer("GET", reply(200, body("unread")))
            repository.fetchStatus(UUID)
            nextRequest()
            val observed = Observed(repository, UUID, scope)
            observed.awaitLast(known(ReadStatus.Unread))

            // Success after cancellation: the write completes and the server record is shown.
            val gate = CountDownLatch(1)
            script.answer("PUT", reply(200, body("finished", SECONDS_UPDATED, SECONDS_FINISHED)), gate)
            val caller = scope.launch { repository.setStatus(UUID, ReadStatus.Finished) }
            nextRequest()
            caller.cancel()
            caller.join()
            caller.isCancelled shouldBe true
            gate.countDown()
            observed.awaitLast(known(ReadStatus.Finished, MILLIS_UPDATED, MILLIS_FINISHED))

            // Failure after cancellation: the write rolls back to the previous confirmed record.
            val failGate = CountDownLatch(1)
            script.answer("PUT", reply(500), failGate)
            val failing = scope.launch { repository.setStatus(UUID, ReadStatus.Unread) }
            nextRequest()
            observed.awaitLast(known(ReadStatus.Unread, NOW, finished = null))
            failing.cancel()
            failing.join()
            failGate.countDown()
            observed.awaitLast(known(ReadStatus.Finished, MILLIS_UPDATED, MILLIS_FINISHED))

            // A cancelled read still completes and updates the state.
            val uuid = "fetch-cancel"
            val readGate = CountDownLatch(1)
            script.answer("GET", reply(200, body("reading")), readGate)
            val read = scope.launch { repository.fetchStatus(uuid) }
            nextRequest()
            read.cancel()
            read.join()
            readGate.countDown()
            Observed(repository, uuid, scope).awaitLast(known(ReadStatus.Reading))
        }

        it("TC-32: one state per uuid, shared by every collector; another uuid is untouched") {
            val repository = repositoryFor()
            script.answer("GET", reply(200, body("unread")))
            repository.fetchStatus(UUID)
            script.answer("GET", reply(200, body("finished", SECONDS_UPDATED, SECONDS_FINISHED, bookUuid = "other")))
            repository.fetchStatus("other")
            val scope = newScope(Dispatchers.Unconfined)
            val seenA = CopyOnWriteArrayList<ReadStatusState>()
            val seenB = CopyOnWriteArrayList<ReadStatusState>()
            scope.launch { repository.getReadStatusFlow(UUID).collect { seenA += it } }
            scope.launch { repository.getReadStatusFlow(UUID).collect { seenB += it } }

            script.answer("PUT", reply(200, body("reading", SECONDS_UPDATED)))
            repository.setStatus(UUID, ReadStatus.Reading)

            seenA.last() shouldBe known(ReadStatus.Reading)
            seenB.last() shouldBe known(ReadStatus.Reading)
            repository.getReadStatusFlow("other").first() shouldBe
                known(ReadStatus.Finished, MILLIS_UPDATED, MILLIS_FINISHED)
            server.requestCount shouldBe 3
        }

        it("TC-33: an undecodable value on either call is a result, never a thrown exception") {
            val repository = repositoryFor()
            val huge = "{\"status\":\"reading\",\"updated_at\":99999999999999999999999}"

            script.answer("GET", reply(200, huge))
            repository.fetchStatus(UUID) shouldBe ReadStatusState.Failed

            script.answer("PUT", reply(200, huge))
            repository.setStatus(UUID, ReadStatus.Reading) shouldBe
                ReadStatusWriteResult.Failed(ReadStatusError.UnexpectedResponse)
        }

        it("TC-102: two overlapping writes that both fail restore the state from before both") {
            val repository = repositoryFor(held = true)
            script.answer("GET", reply(200, body("unread")))
            repository.fetchStatus(UUID)
            nextRequest()
            val scope = newScope(Dispatchers.Default)
            val observed = Observed(repository, UUID, scope)
            observed.awaitLast(known(ReadStatus.Unread))

            val gateA = CountDownLatch(1)
            script.answer("PUT", reply(500), gateA)
            script.answer("PUT", reply(500))
            val a = scope.async { repository.setStatus(UUID, ReadStatus.Reading) }
            nextRequest()
            val b = scope.async { repository.setStatus(UUID, ReadStatus.Finished) }
            observed.awaitLast(known(ReadStatus.Finished, NOW, finished = NOW))

            gateA.countDown()
            a.await() shouldBe ReadStatusWriteResult.Failed(ReadStatusError.Http(500))
            b.await() shouldBe ReadStatusWriteResult.Failed(ReadStatusError.Http(500))
            repository.getReadStatusFlow(UUID).first() shouldBe known(ReadStatus.Unread)
        }

        it("TC-103: a read that lands during a write does not replace the optimistic value, and a failed write falls back to the read") {
            val repository = repositoryFor(held = true)
            val scope = newScope(Dispatchers.Default)
            val observed = Observed(repository, UUID, scope)
            val gateG = CountDownLatch(1)
            script.answer("GET", reply(200, body("finished", SECONDS_UPDATED, SECONDS_FINISHED)), gateG)
            val fetch = scope.async { repository.fetchStatus(UUID) }
            nextRequest()
            observed.awaitLast(ReadStatusState.Unknown)

            val gateW = CountDownLatch(1)
            script.answer("PUT", reply(500), gateW)
            val write = scope.async { repository.setStatus(UUID, ReadStatus.Reading) }
            nextRequest()
            observed.awaitLast(known(ReadStatus.Reading, NOW, finished = null))

            gateG.countDown()
            fetch.await() shouldBe known(ReadStatus.Finished, MILLIS_UPDATED, MILLIS_FINISHED)
            repository.getReadStatusFlow(UUID).first() shouldBe known(ReadStatus.Reading, NOW, finished = null)

            gateW.countDown()
            write.await() shouldBe ReadStatusWriteResult.Failed(ReadStatusError.Http(500))
            repository.getReadStatusFlow(UUID).first() shouldBe known(ReadStatus.Finished, MILLIS_UPDATED, MILLIS_FINISHED)
        }
    }
})
