package com.omnibus.omnibus.data.audiobook.rate.remote

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.FakeClientInfo
import com.omnibus.omnibus.data.network.FakeAuthTokenProvider
import com.omnibus.omnibus.data.network.FakeServerBaseUrlProvider
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okhttp3.HttpUrl

/** Answers each request with the next scripted step. A request with no step left gets a 500. */
private class RateScriptedDispatcher : Dispatcher() {
    private val steps = ArrayDeque<MockResponse>()

    fun answer(response: MockResponse) {
        synchronized(steps) { steps.addLast(response) }
    }

    override fun dispatch(request: RecordedRequest): MockResponse =
        synchronized(steps) { steps.removeFirstOrNull() }
            ?: MockResponse.Builder().code(500).body("unscripted").build()
}

class DefaultPlaybackRateApiTest : DescribeSpec({

    lateinit var server: MockWebServer
    lateinit var script: RateScriptedDispatcher

    /** The address of a server that has been shut down, so a request to it fails with an IOException. */
    fun unreachableUrl(): HttpUrl {
        val closed = MockWebServer().apply { start() }
        return closed.url("/").also { closed.close() }
    }

    /** An API for [baseUrl] (null means no server is saved). */
    fun apiFor(
        baseUrl: HttpUrl?,
        dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    ) = DefaultPlaybackRateApi(
        ioDispatcher = dispatcher,
        omnibusHttpClient = OmnibusHttpClient(
            baseUrlProvider = FakeServerBaseUrlProvider(baseUrl = baseUrl),
            authTokenProvider = FakeAuthTokenProvider(token = "secret"),
            omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
            authClientMetadataInterceptor = AuthClientMetadataInterceptor(FakeClientInfo()),
        ),
    )

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    beforeEach {
        script = RateScriptedDispatcher()
        server = MockWebServer().apply {
            dispatcher = script
            start()
        }
    }

    afterEach {
        server.close()
    }

    describe("fetch") {

        // I-SPD-1, R13: a found record maps to its rate, with one GET to the book's rate path
        it("#168 TC-16: a found record maps to Found, with one GET to the book's rate path (I-SPD-1)") {
            script.answer(
                MockResponse.Builder().code(200)
                    .body("""{"book_uuid":"b","playback_rate":1.25,"updated_at":1730000000}""").build(),
            )

            apiFor(server.url("/")).fetch("book-1") shouldBe RateFetch.Found(1.25)

            server.requestCount shouldBe 1
            val request = server.takeRequest()
            request.method shouldBe "GET"
            request.url.encodedPath shouldBe "/api/audiobooks/book-1/playback-rate"
        }

        // R13: the uuid is one encoded path segment, so a separator in it cannot change the path
        it("#168 TC-16b: a uuid with special characters is one encoded path segment (I-SPD-1)") {
            script.answer(MockResponse.Builder().code(200).body("null").build())

            apiFor(server.url("/")).fetch("a b/c") shouldBe RateFetch.Unset

            server.takeRequest().url.encodedPath shouldBe "/api/audiobooks/a%20b%2Fc/playback-rate"
        }

        // I-SPD-4: a null body is the listener never having set a rate
        it("#168 TC-17: a null body maps to Unset (I-SPD-4)") {
            script.answer(MockResponse.Builder().code(200).body("null").build())

            apiFor(server.url("/")).fetch("book-1") shouldBe RateFetch.Unset
        }

        // R13: unknown keys are ignored, and an integer rate decodes as a double
        it("#168 TC-18: unknown keys are ignored and an integer rate decodes as a double (R13)") {
            script.answer(
                MockResponse.Builder().code(200)
                    .body("""{"book_uuid":"b","playback_rate":1.25,"extra":{},"updated_at":1730000000}""").build(),
            )
            script.answer(MockResponse.Builder().code(200).body("""{"playback_rate":1}""").build())

            val api = apiFor(server.url("/"))
            api.fetch("book-1") shouldBe RateFetch.Found(1.25)
            api.fetch("book-1") shouldBe RateFetch.Found(1.0)
        }

        // R13: HTTP errors and bodies that don't decode are Failed, never thrown
        it("#168 TC-19: non-2xx answers and unreadable bodies are Failed and do not throw (R13)") {
            val rows = listOf(
                MockResponse.Builder().code(404).build() to "404",
                MockResponse.Builder().code(500).build() to "500",
                MockResponse.Builder().code(401).build() to "401",
                MockResponse.Builder().code(200).body("not json").build() to "malformed json",
                MockResponse.Builder().code(200).body("""{"book_uuid":"b"}""").build() to "missing playback_rate",
            )
            rows.forEach { (response, name) ->
                withClue(name) {
                    script.answer(response)
                    shouldNotThrowAny {
                        apiFor(server.url("/")).fetch("book-1") shouldBe RateFetch.Failed
                    }
                }
            }
        }

        // R13: no saved server, an unreachable server and a dropped connection are Failed, never thrown
        it("#168 TC-20: no server, an unreachable server and a dropped connection are Failed (R13)") {
            shouldNotThrowAny {
                apiFor(null).fetch("book-1") shouldBe RateFetch.Failed
                server.requestCount shouldBe 0

                apiFor(unreachableUrl()).fetch("book-1") shouldBe RateFetch.Failed

                script.answer(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build())
                apiFor(server.url("/")).fetch("book-1") shouldBe RateFetch.Failed
            }
        }
    }

    describe("put") {

        // R13, I-SPD-1: the body is exactly the one field, sent as JSON to the rate path
        it("#168 TC-21: a PUT sends exactly the playback_rate body and maps a 2xx to Sent (R13)") {
            script.answer(MockResponse.Builder().code(200).body("{}").build())
            script.answer(MockResponse.Builder().code(200).body("{}").build())

            val api = apiFor(server.url("/"))
            api.put("book-1", 1.25) shouldBe PutOutcome.Sent
            api.put("book-1", 0.5) shouldBe PutOutcome.Sent

            val first = server.takeRequest()
            first.method shouldBe "PUT"
            first.url.encodedPath shouldBe "/api/audiobooks/book-1/playback-rate"
            first.headers["Content-Type"]!!.startsWith("application/json") shouldBe true
            first.body?.utf8() shouldBe """{"playback_rate":1.25}"""
            server.takeRequest().body?.utf8() shouldBe """{"playback_rate":0.5}"""
        }

        // R13, I-SPD-3: 400, 404 and 422 can never succeed, so they are dropped
        it("#168 TC-22: 400, 404 and 422 map to Drop (R13, I-SPD-3)") {
            listOf(400, 404, 422).forEach { code ->
                withClue("$code") {
                    script.answer(MockResponse.Builder().code(code).build())
                    apiFor(server.url("/")).put("book-1", 1.25) shouldBe PutOutcome.Drop
                }
            }
        }

        // R13, I-SPD-3: everything else is kept for a later retry, and nothing is thrown
        it("#168 TC-23: transient failures, a missing server and a dropped connection map to Keep (R13, I-SPD-3)") {
            listOf(401, 408, 429, 500, 503).forEach { code ->
                withClue("$code") {
                    script.answer(MockResponse.Builder().code(code).build())
                    shouldNotThrowAny {
                        apiFor(server.url("/")).put("book-1", 1.25) shouldBe PutOutcome.Keep
                    }
                }
            }

            shouldNotThrowAny {
                apiFor(null).put("book-1", 1.25) shouldBe PutOutcome.Keep
                apiFor(unreachableUrl()).put("book-1", 1.25) shouldBe PutOutcome.Keep

                script.answer(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build())
                apiFor(server.url("/")).put("book-1", 1.25) shouldBe PutOutcome.Keep
            }
        }
    }
})
