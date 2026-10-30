package com.omnibus.omnibus.data.reader.remote

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.FakeClientInfo
import com.omnibus.omnibus.data.network.FakeAuthTokenProvider
import com.omnibus.omnibus.data.network.FakeServerBaseUrlProvider
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.data.reader.BookFileResult
import com.omnibus.omnibus.data.reader.local.LocalBookFiles
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import java.io.File
import java.nio.file.Files

class DefaultBookFileSourceTest : DescribeSpec({

    lateinit var server: MockWebServer
    lateinit var tokenProvider: FakeAuthTokenProvider
    lateinit var baseUrlProvider: FakeServerBaseUrlProvider
    var localFile: File? = null
    lateinit var source: DefaultBookFileSource

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    beforeEach {
        server = MockWebServer().apply { start() }
        tokenProvider = FakeAuthTokenProvider(token = "secret")
        baseUrlProvider = FakeServerBaseUrlProvider(baseUrl = server.url("/"))
        localFile = null
        source = DefaultBookFileSource(
            ioDispatcher = Dispatchers.Unconfined,
            omnibusHttpClient = OmnibusHttpClient(
                baseUrlProvider = baseUrlProvider,
                authTokenProvider = tokenProvider,
                omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
                authClientMetadataInterceptor = AuthClientMetadataInterceptor(FakeClientInfo()),
            ),
            localBookFiles = LocalBookFiles { localFile },
        )
    }

    afterEach { server.close() }

    fun enqueue(code: Int, body: String = "") {
        server.enqueue(MockResponse.Builder().code(code).body(body).build())
    }

    describe("open from the server") {

        it("TC-214: streams the server's bytes and length for the book's file path") {
            enqueue(200, "epub-bytes")

            val opened = source.open("book-1").shouldBeInstanceOf<BookFileResult.Opened>()

            opened.stream.use { it.readBytes().decodeToString() } shouldBe "epub-bytes"
            opened.length shouldBe 10L
            server.takeRequest().url.encodedPath shouldBe "/api/ebooks/book-1/file"
        }

        it("TC-215: attaches the bearer token") {
            enqueue(200, "x")

            source.open("book-1").shouldBeInstanceOf<BookFileResult.Opened>().stream.close()

            server.takeRequest().headers["Authorization"] shouldBe "Bearer secret"
        }

        it("TC-216: maps 401 to Unauthenticated and invalidates the stored token") {
            enqueue(401)

            source.open("book-1") shouldBe BookFileResult.Unauthenticated

            tokenProvider.invalidateCalls shouldBe 1
        }

        it("TC-217: maps 404 to NotFound") {
            enqueue(404)

            source.open("book-1") shouldBe BookFileResult.NotFound
        }

        it("TC-218: maps other failure statuses to Error") {
            enqueue(500)
            source.open("book-1") shouldBe BookFileResult.Error

            enqueue(403)
            source.open("book-1") shouldBe BookFileResult.Error
        }

        it("TC-219: reports NoServer without a request when no server is saved") {
            baseUrlProvider.baseUrl = null

            source.open("book-1") shouldBe BookFileResult.NoServer

            server.requestCount shouldBe 0
        }

        it("TC-220: maps an IO failure to Error instead of throwing") {
            server.enqueue(
                MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build(),
            )

            source.open("book-1") shouldBe BookFileResult.Error
        }
    }

    describe("open from a downloaded copy") {

        it("TC-221: serves the local file and never touches the network") {
            localFile = Files.createTempFile("book", ".epub").toFile().apply {
                writeText("local-bytes")
                deleteOnExit()
            }

            val opened = source.open("book-1").shouldBeInstanceOf<BookFileResult.Opened>()

            opened.stream.use { it.readBytes().decodeToString() } shouldBe "local-bytes"
            opened.length shouldBe 11L
            server.requestCount shouldBe 0
        }

        it("TC-222: falls back to the server when the downloaded file has gone missing") {
            localFile = File("/nonexistent/book.epub")
            enqueue(200, "remote-bytes")

            val opened = source.open("book-1").shouldBeInstanceOf<BookFileResult.Opened>()

            opened.stream.use { it.readBytes().decodeToString() } shouldBe "remote-bytes"
        }
    }
})
