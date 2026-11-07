package com.omnibus.omnibus.data.network

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.TestClientInfo
import com.omnibus.omnibus.testing.randomString
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okhttp3.HttpUrl

/** An [OmnibusHttpClient] for [baseUrl] (null means no server is saved), signed in with a random token. */
fun testOmnibusHttpClient(baseUrl: HttpUrl? = null): OmnibusHttpClient = OmnibusHttpClient(
    baseUrlProvider = TestServerBaseUrlProvider(baseUrl = baseUrl),
    authTokenProvider = TestAuthTokenProvider(token = randomString()),
    omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
    authClientMetadataInterceptor = AuthClientMetadataInterceptor(TestClientInfo()),
)

/** Runs [block] against a started [MockWebServer] and closes it afterwards. */
inline fun <T> withMockServer(block: (MockWebServer) -> T): T = MockWebServer().use { server ->
    server.start()
    block(server)
}

fun reply(code: Int, body: String = ""): MockResponse = MockResponse.Builder().code(code).body(body).build()

/** A connection the server drops before it answers. */
fun droppedConnection(): MockResponse = MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()

/** The next request the server received, in arrival order. */
fun MockWebServer.nextRequest(): RecordedRequest =
    checkNotNull(takeRequest(5, TimeUnit.SECONDS)) { "No request reached the server" }

/**
 * An IO dispatcher that holds work until the test calls `scheduler.runCurrent()`, so a request can be left in flight
 * on one thread, without a real clock.
 */
fun pausedDispatcher(): TestDispatcher = StandardTestDispatcher(TestCoroutineScheduler())
