package com.omnibus.omnibus.data.audiobook.remote

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.TestClientInfo
import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.data.network.TestAuthTokenProvider
import com.omnibus.omnibus.data.network.TestServerBaseUrlProvider
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okhttp3.HttpUrl

private const val PATH = "/api/progress/recent"

private fun reply(code: Int, body: String = ""): MockResponse = MockResponse.Builder().code(code).body(body).build()

/** A response whose connection drops before any byte of it arrives. */
private fun dropped(): MockResponse = MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()

/** Answers requests in the order they were scripted. A request with nothing queued gets a 500. */
private class ScriptedDispatcher : Dispatcher() {
    private val steps = ArrayDeque<MockResponse>()

    fun answer(response: MockResponse) {
        synchronized(steps) { steps.addLast(response) }
    }

    override fun dispatch(request: RecordedRequest): MockResponse =
        synchronized(steps) { steps.removeFirstOrNull() } ?: reply(500, "unscripted")
}

/** Two listening points, newest first, as the server writes them. */
private const val TWO_POINTS = """[
    {"record":{"book_uuid":"book-1","format":"audio","audio_position_seconds":60.0,"updated_at":90,"book_file_id":7},
     "book":{"id":1,"unique_identifier":"book-1","title":"One","formats":["m4b"]}},
    {"record":{"book_uuid":"book-2","format":"audio","audio_position_seconds":5.0,"updated_at":80},
     "book":{"id":2,"unique_identifier":"book-2","title":"Two","formats":["m4b"]}}
  ]"""

class DefaultRecentListeningSourceTest : DescribeSpec({

    lateinit var server: MockWebServer
    lateinit var script: ScriptedDispatcher

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    beforeEach {
        script = ScriptedDispatcher()
        server = MockWebServer().apply {
            dispatcher = script
            start()
        }
    }

    afterEach { server.close() }

    /** A source over [baseUrl], or with no server when it is null. */
    fun sourceFor(baseUrl: HttpUrl? = server.url("/")) = DefaultRecentListeningSource(
        ioDispatcher = Dispatchers.Unconfined,
        omnibusHttpClient = OmnibusHttpClient(
            baseUrlProvider = TestServerBaseUrlProvider(baseUrl = baseUrl),
            authTokenProvider = TestAuthTokenProvider(token = "secret"),
            omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
            authClientMetadataInterceptor = AuthClientMetadataInterceptor(TestClientInfo()),
        ),
    )

    describe("recent listening") {

        // TC-48
        it("requests limit=12&format=audio (I-SYS-5)") {
            script.answer(reply(200, "[]"))

            sourceFor().recentAudio()

            val request = server.takeRequest()
            request.method shouldBe "GET"
            request.url.encodedPath shouldBe PATH
            request.url.queryParameter("limit") shouldBe "12"
            request.url.queryParameter("format") shouldBe "audio"
        }

        // TC-48
        it("decodes points (I-SYS-5)") {
            script.answer(reply(200, TWO_POINTS))

            val points = sourceFor().recentAudio()

            points.map { it.record.bookUuid } shouldBe listOf("book-1", "book-2")
            points[0].record.format shouldBe ProgressFormat.Listening
            points[0].record.bookFileId shouldBe 7L
            points[0].record.audioPositionSeconds shouldBe 60.0
            points[0].book.uuid shouldBe "book-1"
        }

        // TC-48
        it("failures are not thrown (I-SYS-5)") {
            script.answer(reply(401))
            sourceFor().recentAudio() shouldBe emptyList()

            script.answer(dropped())
            sourceFor().recentAudio() shouldBe emptyList()

            script.answer(reply(200, "[]"))
            sourceFor().recentAudio() shouldBe emptyList()

            script.answer(reply(200, "{bad"))
            sourceFor().recentAudio() shouldBe emptyList()

            sourceFor(baseUrl = null).recentAudio() shouldBe emptyList()
        }
    }
})
