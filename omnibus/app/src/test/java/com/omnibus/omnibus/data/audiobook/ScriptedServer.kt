package com.omnibus.omnibus.data.audiobook

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.TestClientInfo
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.data.network.TestAuthTokenProvider
import com.omnibus.omnibus.data.network.TestServerBaseUrlProvider
import com.omnibus.omnibus.testing.randomString
import java.util.concurrent.TimeUnit
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okhttp3.HttpUrl

private const val REQUEST_WAIT_SECONDS = 5L

/**
 * A started [MockWebServer] that answers each request with the next scripted step: the step's hook runs first (it may
 * write to a fake store, as a concurrent save would), then its response goes out. A request with no step left gets a 500.
 */
internal class ScriptedServer : AutoCloseable {
    private val steps = ArrayDeque<Pair<(RecordedRequest) -> Unit, MockResponse>>()

    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val step = synchronized(steps) { steps.removeFirstOrNull() } ?: return reply(500, "unscripted")
                step.first(request)
                return step.second
            }
        }
        start()
    }

    val baseUrl: HttpUrl get() = server.url("/")

    val requestCount: Int get() = server.requestCount

    fun url(path: String): HttpUrl = server.url(path)

    fun answer(response: MockResponse, beforeAnswer: (RecordedRequest) -> Unit = {}) {
        synchronized(steps) { steps.addLast(beforeAnswer to response) }
    }

    fun answer(code: Int, body: String = "") = answer(reply(code, body))

    /** The next request. It fails instead of waiting forever, so a request that was never sent can't hang the suite. */
    fun takeRequest(): RecordedRequest =
        requireNotNull(server.takeRequest(REQUEST_WAIT_SECONDS, TimeUnit.SECONDS)) { "No request arrived" }

    override fun close() = server.close()
}

internal fun reply(code: Int, body: String = ""): MockResponse = MockResponse.Builder().code(code).body(body).build()

/** A response whose connection drops before any byte of it arrives. */
internal fun droppedConnection(): MockResponse = MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()

/** The address of a server that has been shut down, so a request to it fails with an IOException. */
internal fun unreachableUrl(): HttpUrl = MockWebServer().run {
    start()
    url("/").also { close() }
}

/** A client for [baseUrl] (null means no server is saved), signed in with [token]. */
internal fun httpClientFor(baseUrl: HttpUrl?, token: String = randomString()) = OmnibusHttpClient(
    baseUrlProvider = TestServerBaseUrlProvider(baseUrl = baseUrl),
    authTokenProvider = TestAuthTokenProvider(token = token),
    omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
    authClientMetadataInterceptor = AuthClientMetadataInterceptor(TestClientInfo()),
)
