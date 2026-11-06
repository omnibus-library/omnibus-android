package com.omnibus.omnibus.data.quotecard

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.FakeClientInfo
import com.omnibus.omnibus.data.export.local.DefaultExportFileStore
import com.omnibus.omnibus.data.network.FakeAuthTokenProvider
import com.omnibus.omnibus.data.network.FakeServerBaseUrlProvider
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.HttpUrl

class DefaultQuoteCardRepositoryTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }
    afterSpec { unmockAndroidLog() }

    lateinit var tempDir: File
    lateinit var server: MockWebServer

    beforeEach {
        tempDir = Files.createTempDirectory("quote-card").toFile()
        server = MockWebServer().apply { start() }
    }

    afterEach {
        server.close()
        tempDir.deleteRecursively()
    }

    /** A repository writing under [root] and talking to [baseUrl]; null means no server is saved. */
    fun repository(root: File = File(tempDir, "book-exports"), baseUrl: HttpUrl? = server.url("/")) =
        DefaultQuoteCardRepository(
            ioDispatcher = Dispatchers.Unconfined,
            omnibusHttpClient = OmnibusHttpClient(
                baseUrlProvider = FakeServerBaseUrlProvider(baseUrl = baseUrl),
                authTokenProvider = FakeAuthTokenProvider(token = "secret"),
                omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
                authClientMetadataInterceptor = AuthClientMetadataInterceptor(FakeClientInfo()),
            ),
            exportFileStore = DefaultExportFileStore(root),
        )

    fun reply(code: Int, body: String = ""): MockResponse = MockResponse.Builder().code(code).body(body).build()

    describe("DefaultQuoteCardRepository.saveCard") {

        it("returns omnibus-quote.png holding exactly the bytes, in a new folder under the export root") {
            val root = File(tempDir, "book-exports")
            val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0, 1, 2, 3)

            val file = repository(root).saveCard(png).shouldNotBeNull()

            file.name shouldBe "omnibus-quote.png"
            file.readBytes().toList() shouldBe png.toList()
            file.parentFile!!.parentFile shouldBe root
        }

        it("writes each card to its own folder") {
            val repository = repository(File(tempDir, "book-exports"))

            val first = repository.saveCard(byteArrayOf(1)).shouldNotBeNull()
            val second = repository.saveCard(byteArrayOf(2)).shouldNotBeNull()

            first.parentFile shouldNotBe second.parentFile
            first.readBytes().toList() shouldBe listOf<Byte>(1)
        }

        it("returns null when the export folder can't be written") {
            val root = File(tempDir, "book-exports").apply { writeText("a file, not a folder") }

            repository(root).saveCard(byteArrayOf(1)).shouldBeNull()
        }
    }

    describe("DefaultQuoteCardRepository.getCoverPairs") {

        val twoPairs = """{"pairs":[
            {"from":"#5d1f1c","to":"#c01a1a","ink":"light"},
            {"from":"#efe4e6","to":"#d5c2c9","ink":"dark"}
        ]}"""

        it("GETs the book's palette beneath the server with the bearer token, and maps the pairs in order") {
            server.enqueue(reply(200, twoPairs))

            val pairs = repository().getCoverPairs("book 1/é")

            pairs shouldBe listOf(
                CoverPair(0xFF5D1F1C.toInt(), 0xFFC01A1A.toInt(), lightInk = true),
                CoverPair(0xFFEFE4E6.toInt(), 0xFFD5C2C9.toInt(), lightInk = false),
            )
            val request = checkNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            request.method shouldBe "GET"
            request.url.encodedPath shouldBe "/api/books/book%201%2F%C3%A9/cover/palette"
            request.headers["Authorization"] shouldBe "Bearer secret"
        }

        listOf(
            "a 404 from a server without the endpoint" to reply(404),
            "a 500" to reply(500, "boom"),
            "a body that isn't JSON" to reply(200, "<html>sign in</html>"),
            "a connection the server drops" to
                MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build(),
        ).forEach { (why, response) ->
            it("returns no pairs for $why") {
                server.enqueue(response)

                repository().getCoverPairs("book-1") shouldBe emptyList()
            }
        }

        it("returns no pairs, and makes no request, when no server is saved") {
            repository(baseUrl = null).getCoverPairs("book-1") shouldBe emptyList()

            server.requestCount shouldBe 0
        }

        it("returns no pairs, and makes no request, for a blank uuid") {
            repository().getCoverPairs("  ") shouldBe emptyList()

            server.requestCount shouldBe 0
        }
    }
})
