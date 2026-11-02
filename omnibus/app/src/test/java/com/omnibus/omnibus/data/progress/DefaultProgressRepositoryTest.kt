package com.omnibus.omnibus.data.progress

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.FakeClientInfo
import com.omnibus.omnibus.data.network.FakeAuthTokenProvider
import com.omnibus.omnibus.data.network.FakeServerBaseUrlProvider
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.data.progress.local.FakeReadingPositionStore
import com.omnibus.omnibus.data.progress.local.ReadingPositionStore
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.HttpUrl

class DefaultProgressRepositoryTest : DescribeSpec({

    lateinit var server: MockWebServer
    lateinit var store: FakeReadingPositionStore

    val position = ReadingPosition("book-1", "epubcfi(/6/4)", 40, 1_700_000_000)

    /** A repository for [baseUrl] (null means no server is saved), writing to [readingStore]. */
    fun repositoryFor(
        baseUrl: HttpUrl?,
        readingStore: ReadingPositionStore = store,
        dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    ) = DefaultProgressRepository(
        ioDispatcher = dispatcher,
        omnibusHttpClient = OmnibusHttpClient(
            baseUrlProvider = FakeServerBaseUrlProvider(baseUrl = baseUrl),
            authTokenProvider = FakeAuthTokenProvider(token = "secret"),
            omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
            authClientMetadataInterceptor = AuthClientMetadataInterceptor(FakeClientInfo()),
        ),
        store = readingStore,
    )

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    beforeEach {
        server = MockWebServer().apply { start() }
        store = FakeReadingPositionStore()
    }

    afterEach { server.close() }

    describe("savePosition") {

        it("TC-7: a push sends the update payload to POST api/progress") {
            server.enqueue(MockResponse.Builder().code(200).build())

            repositoryFor(server.url("/")).savePosition(position, push = true) shouldBe PositionSaveResult.Pushed

            val request = server.takeRequest()
            request.method shouldBe "POST"
            request.url.encodedPath shouldBe "/api/progress"
            Json.parseToJsonElement(request.body!!.utf8()) shouldBe Json.parseToJsonElement(
                """{"book_uuid":"book-1","format":"epub","epub_cfi":"epubcfi(/6/4)","progress_percent":40,"client_updated_at":1700000000}""",
            )
        }

        it("TC-8: absent optional fields are omitted, not sent as null") {
            server.enqueue(MockResponse.Builder().code(200).build())

            repositoryFor(server.url("/"))
                .savePosition(position.copy(percent = null), push = true) shouldBe PositionSaveResult.Pushed

            val raw = server.takeRequest().body!!.utf8()
            Json.parseToJsonElement(raw).jsonObject.keys shouldBe
                setOf("book_uuid", "format", "epub_cfi", "client_updated_at")
            raw shouldNotContain "null"
        }

        it("TC-9: each outcome has its result, and the local write always happens") {
            // Pushed: a 200 with push = true.
            server.enqueue(MockResponse.Builder().code(200).build())
            val pushedStore = FakeReadingPositionStore()
            repositoryFor(server.url("/"), pushedStore).savePosition(position, push = true) shouldBe
                PositionSaveResult.Pushed
            server.requestCount shouldBe 1
            pushedStore.getPositionFlow("book-1").first() shouldBe position

            // NotPushed: push = false sends nothing.
            val notPushedStore = FakeReadingPositionStore()
            repositoryFor(server.url("/"), notPushedStore).savePosition(position, push = false) shouldBe
                PositionSaveResult.NotPushed
            server.requestCount shouldBe 1
            notPushedStore.getPositionFlow("book-1").first() shouldBe position

            // NoServer: push = true with no saved server.
            val noServerStore = FakeReadingPositionStore()
            repositoryFor(null, noServerStore).savePosition(position, push = true) shouldBe
                PositionSaveResult.NoServer
            server.requestCount shouldBe 1
            noServerStore.getPositionFlow("book-1").first() shouldBe position
        }

        it("TC-12: failed pushes map to Failed without throwing, and the position stays stored") {
            val rows = listOf(
                MockResponse.Builder().code(400).build() to ProgressError.Http(400),
                MockResponse.Builder().code(404).build() to ProgressError.Http(404),
                MockResponse.Builder().code(500).build() to ProgressError.Http(500),
                MockResponse.Builder().code(401).build() to ProgressError.SessionExpired,
                MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build() to ProgressError.Unreachable,
            )
            rows.forEach { (response, error) ->
                server.enqueue(response)
                val rowStore = FakeReadingPositionStore()

                repositoryFor(server.url("/"), rowStore).savePosition(position, push = true) shouldBe
                    PositionSaveResult.Failed(error)

                rowStore.getPositionFlow("book-1").first() shouldBe position
            }
        }

        it("TC-14: saves land in call order even when the first push is slow") {
            server.enqueue(MockResponse.Builder().code(200).headersDelay(300, TimeUnit.MILLISECONDS).build())
            server.enqueue(MockResponse.Builder().code(200).build())
            val repository = repositoryFor(server.url("/"), dispatcher = Dispatchers.IO)
            val second = ReadingPosition("book-1", "epubcfi(/6/8)", 45, 1_700_000_100)

            // UNDISPATCHED starts each call in order, so A reaches the mutex before B.
            val scope = CoroutineScope(Dispatchers.Default)
            try {
                val first = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    repository.savePosition(position, push = true)
                }
                val later = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    repository.savePosition(second, push = true)
                }
                joinAll(first, later)
            } finally {
                scope.cancel()
            }

            Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject["epub_cfi"]!!.jsonPrimitive.content shouldBe
                "epubcfi(/6/4)"
            Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject["epub_cfi"]!!.jsonPrimitive.content shouldBe
                "epubcfi(/6/8)"
            store.getPositionFlow("book-1").first() shouldBe second
        }
    }
})
