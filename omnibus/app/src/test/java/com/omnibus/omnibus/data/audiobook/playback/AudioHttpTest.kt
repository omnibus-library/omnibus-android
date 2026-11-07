package com.omnibus.omnibus.data.audiobook.playback

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.TestClientInfo
import com.omnibus.omnibus.data.network.TestAuthTokenProvider
import com.omnibus.omnibus.data.network.TestServerBaseUrlProvider
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Request

class AudioHttpTest : DescribeSpec({

    lateinit var server: MockWebServer

    beforeEach {
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse.Builder().body("audio bytes").build()
            }
            start()
        }
    }

    afterEach { server.close() }

    describe("audioCallFactory") {

        // TC-94
        it("audio shares the app client and headers (AND-STK-3)") {
            val client = OmnibusHttpClient(
                baseUrlProvider = TestServerBaseUrlProvider(baseUrl = server.url("/")),
                authTokenProvider = TestAuthTokenProvider(token = "secret"),
                omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
                authClientMetadataInterceptor = AuthClientMetadataInterceptor(TestClientInfo()),
            )
            val factory = audioCallFactory(client)

            // The headers the manifest result carries, as OkHttpDataSource sets them as default request properties.
            repeat(2) {
                val request = Request.Builder()
                    .url(server.url("/api/audiobooks/book-1/parts/1"))
                    .header("Authorization", "Bearer secret")
                    .header("Accept", "*/*")
                    .build()
                factory.newCall(request).execute().use { response ->
                    response.body.string() shouldBe "audio bytes"
                }
            }

            server.requestCount shouldBe 2
            repeat(2) {
                val recorded = server.takeRequest()
                recorded.headers["Authorization"] shouldBe "Bearer secret"
                recorded.headers["Accept"] shouldBe "*/*"
                // Both requests share one connection, which only the app's client pool does here.
                recorded.connectionIndex shouldBe 0
            }
        }
    }
})
