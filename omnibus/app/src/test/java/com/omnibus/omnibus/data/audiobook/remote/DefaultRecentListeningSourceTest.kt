package com.omnibus.omnibus.data.audiobook.remote

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.TestClientInfo
import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.data.network.TestAuthTokenProvider
import com.omnibus.omnibus.data.network.TestServerBaseUrlProvider
import com.omnibus.omnibus.testing.dropped
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.reply
import com.omnibus.omnibus.testing.unmockAndroidLog
import com.omnibus.omnibus.testing.withMockServer
import com.omnibus.omnibus.testing.withScriptedServer
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.Dispatchers
import okhttp3.HttpUrl

/** Two listening points, newest first, as the server writes them. */
private const val TWO_POINTS = """[
    {"record":{"book_uuid":"book-1","format":"audio","audio_position_seconds":60.0,"updated_at":90,"book_file_id":7},
     "book":{"id":1,"unique_identifier":"book-1","title":"One","formats":["m4b"]}},
    {"record":{"book_uuid":"book-2","format":"audio","audio_position_seconds":5.0,"updated_at":80},
     "book":{"id":2,"unique_identifier":"book-2","title":"Two","formats":["m4b"]}}
  ]"""

class DefaultRecentListeningSourceTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }
    afterSpec { unmockAndroidLog() }

    fun sut(baseUrl: HttpUrl?) = DefaultRecentListeningSource(
        ioDispatcher = Dispatchers.Unconfined,
        omnibusHttpClient = OmnibusHttpClient(
            baseUrlProvider = TestServerBaseUrlProvider(baseUrl = baseUrl),
            authTokenProvider = TestAuthTokenProvider(token = randomString()),
            omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
            authClientMetadataInterceptor = AuthClientMetadataInterceptor(TestClientInfo()),
        ),
    )

    describe("reading the recent listening points") {
        // TC-48
        it("asks the server for its recent audio points (I-SYS-5)") {
            withMockServer(reply(200, "[]")) { server ->
                sut(server.url("/")).recentAudio()

                val request = server.takeRequest()
                request.method shouldBe "GET"
                request.url.encodedPath shouldBe "/api/progress/recent"
                request.url.queryParameter("format") shouldBe "audio"
            }
        }

        // TC-48
        it("gives the server's points in order (I-SYS-5)") {
            withMockServer(reply(200, TWO_POINTS)) { server ->
                val points = sut(server.url("/")).recentAudio()

                points.map { it.record.bookUuid } shouldBe listOf("book-1", "book-2")
                points[0].record.format shouldBe ProgressFormat.Listening
                points[0].record.bookFileId shouldBe 7L
                points[0].record.audioPositionSeconds shouldBe 60.0
            }
        }

        // TC-48
        it("gives no points for an error status, a dropped connection or a bad body (I-SYS-5)") {
            checkAll(Exhaustive.of(reply(401), dropped(), reply(200, "{bad"))) { response ->
                withScriptedServer {
                    script.answer(response)

                    sut(url).recentAudio().shouldBeEmpty()
                }
            }
        }

        context("when no server is saved") {
            // TC-48
            it("gives no points (I-SYS-5)") {
                sut(baseUrl = null).recentAudio().shouldBeEmpty()
            }
        }
    }
})
