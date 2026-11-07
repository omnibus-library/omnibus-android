package com.omnibus.omnibus.testing

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.TestClientInfo
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.data.network.TestAuthTokenProvider
import com.omnibus.omnibus.data.network.TestServerBaseUrlProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okhttp3.HttpUrl

private const val REQUEST_WAIT_SECONDS = 5L

/** The key of a step that answers whichever request comes next. */
private const val ANY_REQUEST = ""

/** Outside the codes the app maps, so an unscripted request can't pass as an ordinary server error. */
private const val UNSCRIPTED_CODE = 599

/** The one status OkHttp retries by itself. */
private const val RETRIED_CODE = 408

private val DROPPED: MockResponse = MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()

/** An [OmnibusHttpClient] for [baseUrl] (null means no server is saved), signed in with [token]. */
fun testOmnibusHttpClient(baseUrl: HttpUrl? = null, token: String = randomString()): OmnibusHttpClient = OmnibusHttpClient(
    baseUrlProvider = TestServerBaseUrlProvider(baseUrl = baseUrl),
    authTokenProvider = TestAuthTokenProvider(token = token),
    omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
    authClientMetadataInterceptor = AuthClientMetadataInterceptor(TestClientInfo()),
)

fun reply(code: Int, body: String = ""): MockResponse = MockResponse.Builder().code(code).body(body).build()

/**
 * A connection the server drops before it answers. OkHttp retries a dropped connection, so one such response never
 * reaches the client. Use it where every attempt gets it: a [ScriptedDispatcher] step, or a route that answers repeatedly.
 */
fun dropped(): MockResponse = DROPPED

/** The address of a server that has shut down, so a request to it is refused. */
fun unreachableUrl(): HttpUrl = withMockServer { it.url("/") }

/** The next request the server received, in arrival order. It fails instead of waiting forever. */
fun MockWebServer.nextRequest(): RecordedRequest =
    checkNotNull(takeRequest(REQUEST_WAIT_SECONDS, TimeUnit.SECONDS)) { "No request reached the server" }

/**
 * An IO dispatcher that holds work until the test calls `scheduler.runCurrent()`, so a request can be left in flight
 * on one thread, without a real clock.
 */
fun pausedDispatcher(): TestDispatcher = StandardTestDispatcher(TestCoroutineScheduler())

/** Runs [block] against a started [MockWebServer] that holds [answers] in order, and closes it afterwards. */
inline fun <T> withMockServer(vararg answers: MockResponse, block: (MockWebServer) -> T): T = MockWebServer().use { server ->
    answers.forEach { server.enqueue(it) }
    server.start()
    block(server)
}

/**
 * Answers requests from a script, in order. A step is keyed by HTTP method, by path, or by neither, and a request takes
 * the first step queued for its method, then its path, then either. A step can hold its response on a gate, and can run
 * a hook first. A response OkHttp retries by itself, a [dropped] connection or a 408, answers every attempt until the test
 * scripts the next answer for its key, however often the client retries.
 */
class ScriptedDispatcher : Dispatcher() {
    private class Step(val response: MockResponse, val gate: CountDownLatch?, val beforeAnswer: (RecordedRequest) -> Unit) {
        var served = false
    }

    private val steps = mutableMapOf<String, ArrayDeque<Step>>()
    private val unscripted = mutableListOf<String>()

    fun answer(key: String, response: MockResponse, gate: CountDownLatch? = null, beforeAnswer: (RecordedRequest) -> Unit = {}) {
        synchronized(steps) {
            val queue = steps.getOrPut(key) { ArrayDeque() }
            if (queue.firstOrNull()?.served == true) queue.removeFirst()
            queue.addLast(Step(response, gate, beforeAnswer))
        }
    }

    fun answer(response: MockResponse, beforeAnswer: (RecordedRequest) -> Unit = {}) =
        answer(ANY_REQUEST, response, beforeAnswer = beforeAnswer)

    fun answer(code: Int, body: String = "") = answer(reply(code, body))

    override fun dispatch(request: RecordedRequest): MockResponse {
        val step = synchronized(steps) { nextStep(request) }
        if (step == null) {
            synchronized(unscripted) { unscripted += "${request.method} ${request.url.encodedPath}" }
            return reply(UNSCRIPTED_CODE, "unscripted")
        }
        step.beforeAnswer(request)
        step.gate?.await(REQUEST_WAIT_SECONDS, TimeUnit.SECONDS)
        return step.response
    }

    private fun nextStep(request: RecordedRequest): Step? {
        val queue = listOf(request.method.orEmpty(), request.url.encodedPath, ANY_REQUEST)
            .firstNotNullOfOrNull { key -> steps[key]?.takeIf { it.isNotEmpty() } }
            ?: return null
        val head = queue.first()
        if (head.response === DROPPED || head.response.code == RETRIED_CODE) head.served = true else queue.removeFirst()
        return head
    }

    /** Fails the test if a request arrived that no step answered. */
    fun assertAllScripted() {
        synchronized(unscripted) { check(unscripted.isEmpty()) { "Unscripted requests reached the server: $unscripted" } }
    }
}

/** A running [server] that answers from [script], and [scope], a place for work on real threads that outlives one call. */
class ScriptedServer(val server: MockWebServer, val script: ScriptedDispatcher, val scope: CoroutineScope) {

    val url: HttpUrl get() = server.url("/")

    fun nextRequest(): RecordedRequest = server.nextRequest()
}

/** Runs [block] against a new [ScriptedServer] and closes it afterwards. A request nothing scripted fails the test. */
inline fun <T> withScriptedServer(block: ScriptedServer.() -> T): T {
    val script = ScriptedDispatcher()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    return try {
        MockWebServer().use { server ->
            server.dispatcher = script
            server.start()
            ScriptedServer(server, script, scope).block().also { script.assertAllScripted() }
        }
    } finally {
        scope.cancel()
    }
}
