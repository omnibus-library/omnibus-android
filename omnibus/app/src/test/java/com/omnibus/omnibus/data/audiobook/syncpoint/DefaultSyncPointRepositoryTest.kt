package com.omnibus.omnibus.data.audiobook.syncpoint

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.FakeClientInfo
import com.omnibus.omnibus.data.network.FakeAuthTokenProvider
import com.omnibus.omnibus.data.network.FakeServerBaseUrlProvider
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.serialization.json.Json
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okhttp3.HttpUrl

/**
 * Answers each request with the next scripted step. A step can hold its response until the test releases its latch. A
 * request with no step left gets a 500, so an unexpected call fails the test instead of hanging.
 */
private class ScriptedDispatcher : Dispatcher() {
    private class Step(val response: MockResponse, val gate: CountDownLatch?)

    private val steps = ArrayDeque<Step>()

    fun answer(response: MockResponse, gate: CountDownLatch? = null) {
        synchronized(steps) { steps.addLast(Step(response, gate)) }
    }

    override fun dispatch(request: RecordedRequest): MockResponse {
        val step = synchronized(steps) { steps.removeFirstOrNull() }
            ?: return reply(500, "unscripted")
        step.gate?.await(5, TimeUnit.SECONDS)
        return step.response
    }
}

private fun reply(code: Int, body: String = ""): MockResponse = MockResponse.Builder().code(code).body(body).build()

/** A transport failure: the socket closes after the request arrives. */
private fun dropped(): MockResponse = MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()

class DefaultSyncPointRepositoryTest : DescribeSpec({

    lateinit var server: MockWebServer
    lateinit var script: ScriptedDispatcher
    val scopes = mutableListOf<CoroutineScope>()

    /** A new scope that is cancelled after the test. */
    fun newScope(dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher).also { scopes += it }

    /**
     * A repository for [baseUrl] (the default is the test server). [held] runs HTTP on real threads, so a test can hold a
     * request open. Otherwise everything runs unconfined on the calling thread.
     */
    fun repositoryFor(
        baseUrl: HttpUrl? = server.url("/"),
        held: Boolean = false,
    ): DefaultSyncPointRepository {
        val scope = newScope(if (held) Dispatchers.Default else Dispatchers.Unconfined)
        return DefaultSyncPointRepository(
            ioDispatcher = if (held) Dispatchers.IO else Dispatchers.Unconfined,
            omnibusHttpClient = OmnibusHttpClient(
                baseUrlProvider = FakeServerBaseUrlProvider(baseUrl = baseUrl),
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

    /** The address of a server that has been shut down, so a request to it is refused. */
    fun unreachableUrl(): HttpUrl {
        val closed = MockWebServer().apply { start() }
        return closed.url("/").also { closed.close() }
    }

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

    describe("declaring an audio sync point") {

        // TC38, I-BMK-3: one POST to the sync-point path, with exactly the documented body; a 204 is Declared
        it("TC38: sends the documented body to the sync point path and reads a 204 as declared") {
            script.answer(reply(204))
            val repository = repositoryFor()

            repository.declareAudioSyncPoint("abc-123", 42L, 3725.5) shouldBe SyncPointResult.Declared

            val request = nextRequest()
            request.method shouldBe "POST"
            request.url.encodedPath shouldBe "/api/books/abc-123/sync-point"
            request.headers["Authorization"] shouldBe "Bearer secret"
            Json.parseToJsonElement(request.body!!.utf8()) shouldBe Json.parseToJsonElement(
                """{"book_uuid":"abc-123","format":"audio","ebook_fraction":null,"audio_book_file_id":42,"audio_seconds":3725.5}""",
            )
        }

        // TC39: the body of a 2xx answer is never read, so a 200 with a body and a 204 with none are both Declared
        it("TC39: a 200 with a body and a 204 with none are both declared") {
            script.answer(reply(200, "{\"ignored\":true}"))
            script.answer(reply(204))
            val repository = repositoryFor()

            repository.declareAudioSyncPoint("abc-123", 42L, 1.0) shouldBe SyncPointResult.Declared
            repository.declareAudioSyncPoint("abc-123", 42L, 2.0) shouldBe SyncPointResult.Declared
        }

        // TC40, R11: a reserved character in the uuid stays inside one path segment; a blank uuid makes no request
        it("TC40: encodes the uuid as one path segment and sends nothing for a blank uuid") {
            script.answer(reply(204))
            val repository = repositoryFor()

            repository.declareAudioSyncPoint("a/b c", 42L, 1.0) shouldBe SyncPointResult.Declared

            val segments = nextRequest().url.encodedPathSegments
            segments.size shouldBe 4
            segments[2] shouldBe "a%2Fb%20c"
            segments[3] shouldBe "sync-point"

            repository.declareAudioSyncPoint("  ", 42L, 1.0) shouldBe
                SyncPointResult.Failed(SyncPointError.BookNotFound)
            server.takeRequest(200, TimeUnit.MILLISECONDS) shouldBe null
        }

        // TC41: each status maps to its typed error, and each is sent once (no retry)
        it("TC41: maps each status to its typed error with one request each") {
            val mapped = listOf(
                401 to SyncPointError.SessionExpired,
                404 to SyncPointError.BookNotFound,
                409 to SyncPointError.Conflict,
                400 to SyncPointError.Rejected,
                422 to SyncPointError.Rejected,
                500 to SyncPointError.Http(500),
                418 to SyncPointError.Http(418),
            )
            val repository = repositoryFor()

            for ((code, error) in mapped) {
                script.answer(reply(code))
                repository.declareAudioSyncPoint("abc-123", 42L, 1.0) shouldBe SyncPointResult.Failed(error)
                nextRequest().url.encodedPath shouldBe "/api/books/abc-123/sync-point"
            }
            server.takeRequest(200, TimeUnit.MILLISECONDS) shouldBe null
        }

        // TC42: with no server saved, the result is NoServer and no request is made
        it("TC42: with no server saved, reports NoServer and sends nothing") {
            val repository = repositoryFor(baseUrl = null)

            repository.declareAudioSyncPoint("abc-123", 42L, 1.0) shouldBe
                SyncPointResult.Failed(SyncPointError.NoServer)
            server.takeRequest(200, TimeUnit.MILLISECONDS) shouldBe null
        }

        // TC43: a refused connection or a dropped socket is Unreachable, and nothing is thrown
        it("TC43: a refused connection or a dropped socket is Unreachable") {
            repositoryFor(baseUrl = unreachableUrl()).declareAudioSyncPoint("abc-123", 42L, 1.0) shouldBe
                SyncPointResult.Failed(SyncPointError.Unreachable)

            script.answer(dropped())
            repositoryFor().declareAudioSyncPoint("abc-123", 42L, 1.0) shouldBe
                SyncPointResult.Failed(SyncPointError.Unreachable)
        }

        // TC45, R11: cancelling the caller while the server holds its answer surfaces the cancellation, not a result
        it("TC45: cancelling the caller propagates CancellationException") {
            val gate = CountDownLatch(1)
            script.answer(reply(204), gate)
            val repository = repositoryFor(held = true)
            val caller = newScope(Dispatchers.Default).async {
                repository.declareAudioSyncPoint("abc-123", 42L, 1.0)
            }
            nextRequest()

            caller.cancelAndJoin()
            gate.countDown()

            caller.isCancelled shouldBe true
            shouldThrow<CancellationException> { caller.await() }
        }

        // TC46, I-BMK-3: the POST runs on the application scope, so a cancelled caller does not abandon it
        it("TC46: cancelling the caller does not stop the request on the application scope") {
            val gate = CountDownLatch(1)
            script.answer(reply(204), gate)
            val appScope = newScope(Dispatchers.Default)
            val repository = DefaultSyncPointRepository(
                ioDispatcher = Dispatchers.IO,
                omnibusHttpClient = OmnibusHttpClient(
                    baseUrlProvider = FakeServerBaseUrlProvider(baseUrl = server.url("/")),
                    authTokenProvider = FakeAuthTokenProvider(token = "secret"),
                    omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
                    authClientMetadataInterceptor = AuthClientMetadataInterceptor(FakeClientInfo()),
                ),
                applicationScope = appScope,
            )
            val caller = newScope(Dispatchers.Default).async {
                repository.declareAudioSyncPoint("abc-123", 42L, 1.0)
            }
            nextRequest()

            caller.cancelAndJoin()
            gate.countDown()
            appScope.coroutineContext.job.children.toList().joinAll()

            server.requestCount shouldBe 1
            server.takeRequest(200, TimeUnit.MILLISECONDS) shouldBe null
        }
    }
})
