package com.omnibus.omnibus.data.reader.remote

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.TestClientInfo
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.data.network.TestAuthTokenProvider
import com.omnibus.omnibus.data.network.TestServerBaseUrlProvider
import com.omnibus.omnibus.data.reader.BookFileResult
import com.omnibus.omnibus.data.reader.local.LocalBookFiles
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.reply
import com.omnibus.omnibus.testing.unmockAndroidLog
import com.omnibus.omnibus.testing.unreachableUrl
import com.omnibus.omnibus.testing.withMockServer
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import okhttp3.HttpUrl

class DefaultBookFileSourceTest : DescribeSpec({

    fun sut(baseUrl: HttpUrl? = null, localFile: File? = null): DefaultBookFileSource = DefaultBookFileSource(
        ioDispatcher = Dispatchers.Unconfined,
        omnibusHttpClient = OmnibusHttpClient(
            baseUrlProvider = TestServerBaseUrlProvider(baseUrl = baseUrl),
            authTokenProvider = TestAuthTokenProvider(),
            omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
            authClientMetadataInterceptor = AuthClientMetadataInterceptor(TestClientInfo()),
        ),
        localBookFiles = LocalBookFiles { localFile },
    )


    /** Runs [test] with a downloaded copy holding [content], and deletes it afterwards. */
    suspend fun withDownloadedCopy(content: String, test: suspend (File) -> Unit) {
        val file = Files.createTempFile("book", ".epub").toFile().apply { writeText(content) }
        try {
            test(file)
        } finally {
            file.delete()
        }
    }

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("opening a book") {

        context("when there is no downloaded copy") {

            it("TC-214: streams the server's bytes with their length") {
                withMockServer { server ->
                    val content = randomString()
                    server.enqueue(reply(200, content))

                    val opened = sut(server.url("/")).open(randomString()).shouldBeInstanceOf<BookFileResult.Opened>()

                    opened.stream.use { it.readBytes().decodeToString() } shouldBe content
                    opened.length shouldBe content.length.toLong()
                }
            }

            it("TC-214: asks the server for that book's file") {
                withMockServer { server ->
                    val uuid = randomString()
                    server.enqueue(reply(200))

                    sut(server.url("/")).open(uuid).shouldBeInstanceOf<BookFileResult.Opened>().stream.close()

                    server.takeRequest().url.encodedPath shouldBe "/api/ebooks/$uuid/file"
                }
            }

            it("TC-216, TC-217, TC-218: reports each failure status as its own failure") {
                withMockServer { server ->
                    checkAll(
                        Exhaustive.of(
                            401 to BookFileResult.Unauthenticated,
                            404 to BookFileResult.NotFound,
                            403 to BookFileResult.Error,
                            500 to BookFileResult.Error,
                        ),
                    ) { (code, failure) ->
                        server.enqueue(reply(code))

                        sut(server.url("/")).open(randomString()) shouldBe failure
                    }
                }
            }

            context("when the connection drops") {

                it("TC-220: reports an error instead of throwing") {
                    sut(unreachableUrl()).open(randomString()) shouldBe BookFileResult.Error
                }
            }

            context("when no server is saved") {

                it("TC-219: reports that there is no server") {
                    sut().open(randomString()) shouldBe BookFileResult.NoServer
                }
            }
        }

        context("when there is a downloaded copy") {

            it("TC-221: streams the copy's bytes with their length") {
                val content = randomString()

                withDownloadedCopy(content) { file ->
                    val opened = sut(localFile = file).open(randomString()).shouldBeInstanceOf<BookFileResult.Opened>()

                    opened.stream.use { it.readBytes().decodeToString() } shouldBe content
                    opened.length shouldBe content.length.toLong()
                }
            }

            it("TC-221: never asks the server") {
                withMockServer { server ->
                    withDownloadedCopy(randomString()) { file ->
                        sut(server.url("/"), file).open(randomString())
                            .shouldBeInstanceOf<BookFileResult.Opened>().stream.close()

                        server.requestCount shouldBe 0
                    }
                }
            }

            context("when the copy has gone missing") {

                it("TC-222: falls back to the server") {
                    withMockServer { server ->
                        val content = randomString()
                        server.enqueue(reply(200, content))

                        val opened = sut(server.url("/"), File("/nonexistent/${randomString()}.epub"))
                            .open(randomString()).shouldBeInstanceOf<BookFileResult.Opened>()

                        opened.stream.use { it.readBytes().decodeToString() } shouldBe content
                    }
                }
            }
        }
    }
})
