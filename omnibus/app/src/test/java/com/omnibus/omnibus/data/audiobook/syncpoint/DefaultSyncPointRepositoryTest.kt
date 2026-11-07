package com.omnibus.omnibus.data.audiobook.syncpoint

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.TestClientInfo
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.data.network.TestAuthTokenProvider
import com.omnibus.omnibus.data.network.TestServerBaseUrlProvider
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomDouble
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.HttpUrl

private fun reply(code: Int, body: String = ""): MockResponse = MockResponse.Builder().code(code).body(body).build()

/** A transport failure: the socket closes after the request arrives. */
private fun dropped(): MockResponse = MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()

/** A started server that gives each of [responses] to one request, in order. */
private fun serverAnswering(vararg responses: MockResponse) = MockWebServer().apply {
    responses.forEach { enqueue(it) }
    start()
}

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSyncPointRepositoryTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }
    afterSpec { unmockAndroidLog() }

    fun sut(baseUrl: HttpUrl?, ioDispatcher: CoroutineDispatcher = Dispatchers.Unconfined) = DefaultSyncPointRepository(
        ioDispatcher = ioDispatcher,
        omnibusHttpClient = OmnibusHttpClient(
            baseUrlProvider = TestServerBaseUrlProvider(baseUrl = baseUrl),
            authTokenProvider = TestAuthTokenProvider(token = randomString()),
            omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
            authClientMetadataInterceptor = AuthClientMetadataInterceptor(TestClientInfo()),
        ),
        applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    )

    describe("declaring an audio sync point") {
        // TC38, I-BMK-3
        it("TC38: posts the documented body to the book's sync point path") {
            val bookUuid = randomString()
            val fileId = randomLong()
            val seconds = randomDouble(1.0, 10_000.0)
            serverAnswering(reply(204)).use { server ->
                sut(server.url("/")).declareAudioSyncPoint(bookUuid, fileId, seconds)

                val request = server.takeRequest()
                request.method shouldBe "POST"
                request.url.encodedPath shouldBe "/api/books/$bookUuid/sync-point"
                Json.parseToJsonElement(request.body!!.utf8()) shouldBe Json.parseToJsonElement(
                    """{"book_uuid":"$bookUuid","format":"audio","ebook_fraction":null,"audio_book_file_id":$fileId,"audio_seconds":$seconds}""",
                )
            }
        }

        // TC39: the body of a 2xx answer is never read.
        it("TC39: reports declared for a 200 with a body or a 204 with none") {
            checkAll(Exhaustive.of(reply(200, "{\"ignored\":true}"), reply(204))) { response ->
                serverAnswering(response).use { server ->
                    sut(server.url("/")).declareAudioSyncPoint(randomString(), randomLong(), randomDouble()) shouldBe
                        SyncPointResult.Declared
                }
            }
        }

        context("when the uuid has reserved characters") {
            // TC40, R11
            it("TC40: keeps the uuid in one path segment") {
                serverAnswering(reply(204)).use { server ->
                    sut(server.url("/")).declareAudioSyncPoint("a/b c", randomLong(), randomDouble())

                    server.takeRequest().url.encodedPathSegments shouldBe listOf("api", "books", "a%2Fb%20c", "sync-point")
                }
            }
        }

        context("when the uuid is blank") {
            // TC40, R11: the server has no answer queued, so a request would time out and report something else.
            it("TC40: reports the book as not found without asking the server") {
                serverAnswering().use { server ->
                    sut(server.url("/")).declareAudioSyncPoint("  ", randomLong(), randomDouble()) shouldBe
                        SyncPointResult.Failed(SyncPointError.BookNotFound)
                }
            }
        }

        context("when the server refuses") {
            // TC41
            it("TC41: reports the typed error for each status") {
                checkAll(
                    Exhaustive.of(
                        401 to SyncPointError.SessionExpired,
                        404 to SyncPointError.BookNotFound,
                        409 to SyncPointError.Conflict,
                        400 to SyncPointError.Rejected,
                        422 to SyncPointError.Rejected,
                        500 to SyncPointError.Http(500),
                        418 to SyncPointError.Http(418),
                    ),
                ) { (code, error) ->
                    serverAnswering(reply(code)).use { server ->
                        sut(server.url("/")).declareAudioSyncPoint(randomString(), randomLong(), randomDouble()) shouldBe
                            SyncPointResult.Failed(error)
                    }
                }
            }

            // TC41
            it("TC41: does not retry") {
                serverAnswering(reply(500), reply(500)).use { server ->
                    sut(server.url("/")).declareAudioSyncPoint(randomString(), randomLong(), randomDouble())

                    server.requestCount shouldBe 1
                }
            }
        }

        context("when no server is saved") {
            // TC42
            it("TC42: reports NoServer") {
                sut(baseUrl = null).declareAudioSyncPoint(randomString(), randomLong(), randomDouble()) shouldBe
                    SyncPointResult.Failed(SyncPointError.NoServer)
            }
        }

        context("when the connection drops") {
            // TC43
            it("TC43: reports Unreachable") {
                serverAnswering(dropped()).use { server ->
                    sut(server.url("/")).declareAudioSyncPoint(randomString(), randomLong(), randomDouble()) shouldBe
                        SyncPointResult.Failed(SyncPointError.Unreachable)
                }
            }
        }

        context("when the caller is cancelled before the request starts") {
            // TC46, I-BMK-3: the request waits on the scheduler, so it can only reach the server after the cancel, and only
            // if it runs on the application scope.
            it("TC46: still sends the request") {
                val scheduler = TestCoroutineScheduler()
                serverAnswering(reply(204)).use { server ->
                    val repository = sut(server.url("/"), ioDispatcher = StandardTestDispatcher(scheduler))
                    val caller = CoroutineScope(Dispatchers.Unconfined).async {
                        repository.declareAudioSyncPoint(randomString(), randomLong(), randomDouble())
                    }

                    caller.cancel()
                    scheduler.runCurrent()

                    server.requestCount shouldBe 1
                }
            }
        }
    }
})
