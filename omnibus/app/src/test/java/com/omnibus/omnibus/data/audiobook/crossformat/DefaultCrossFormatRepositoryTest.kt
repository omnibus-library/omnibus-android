package com.omnibus.omnibus.data.audiobook.crossformat

import com.omnibus.omnibus.data.audiobook.crossformat.local.CrossFormatDismissStore
import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.TestClientInfo
import com.omnibus.omnibus.data.network.TestAuthTokenProvider
import com.omnibus.omnibus.data.network.TestServerBaseUrlProvider
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okhttp3.HttpUrl

/**
 * Answers each request with the next scripted step. A request with no step left gets a 500.
 */
private class CrossFormatScriptedDispatcher : Dispatcher() {
    private val steps = ArrayDeque<MockResponse>()

    fun answer(response: MockResponse) {
        synchronized(steps) { steps.addLast(response) }
    }

    override fun dispatch(request: RecordedRequest): MockResponse =
        synchronized(steps) { steps.removeFirstOrNull() }
            ?: MockResponse.Builder().code(500).body("unscripted").build()
}

/** An in-memory [CrossFormatDismissStore] for the repository tests. */
private class InMemoryDismissStore : CrossFormatDismissStore {
    val clocks = mutableMapOf<String, Long>()

    override suspend fun dismissedClock(bookUuid: String): Long? = clocks[bookUuid]

    override suspend fun store(bookUuid: String, sourceClock: Long) {
        clocks[bookUuid] = sourceClock
    }
}

class DefaultCrossFormatRepositoryTest : DescribeSpec({

    lateinit var server: MockWebServer
    lateinit var script: CrossFormatScriptedDispatcher
    lateinit var dismissStore: InMemoryDismissStore

    /**
     * A repository for [baseUrl] (null means no server is saved). Requests run on [dispatcher], which the cancellation
     * test replaces with a real IO dispatcher.
     */
    fun repositoryFor(
        baseUrl: HttpUrl?,
        dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    ) = DefaultCrossFormatRepository(
        ioDispatcher = dispatcher,
        omnibusHttpClient = OmnibusHttpClient(
            baseUrlProvider = TestServerBaseUrlProvider(baseUrl = baseUrl),
            authTokenProvider = TestAuthTokenProvider(token = "secret"),
            omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
            authClientMetadataInterceptor = AuthClientMetadataInterceptor(TestClientInfo()),
        ),
        dismissStore = dismissStore,
    )

    /** The body of a candidate answer for book-1 with [state]. */
    fun candidateBody(state: String = "candidate", extra: String = "") =
        """{"state":"$state","follow":false$extra,"candidate":{"source_client_updated_at":42,"book_file_id":11,"audio_position_seconds":12.5,"source_ahead":true}}"""

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    beforeEach {
        script = CrossFormatScriptedDispatcher()
        server = MockWebServer().apply {
            dispatcher = script
            start()
        }
        dismissStore = InMemoryDismissStore()
    }

    afterEach {
        server.close()
    }

    describe("audioResume") {

        // I-RES-7, P-SYNC-7: a candidate maps every field and is one GET
        it("#166 TC-5: a candidate body maps every field, with one GET to the audio target (I-RES-7)") {
            script.answer(MockResponse.Builder().code(200).body(candidateBody()).build())

            repositoryFor(server.url("/")).audioResume("book-1") shouldBe
                CrossFormatAudioResult.Candidate(
                    positionSeconds = 12.5,
                    bookFileId = 11L,
                    sourceAhead = true,
                    sourceClock = 42L,
                    follow = false,
                )

            server.requestCount shouldBe 1
            val request = server.takeRequest()
            request.method shouldBe "GET"
            request.url.encodedPath shouldBe "/api/books/book-1/cross-format-resume"
            request.url.queryParameter("target") shouldBe "audio"
        }

        // P-SYNC-AC5, R5: follow is carried through
        it("#166 TC-6: a follow candidate carries follow true (P-SYNC-AC5)") {
            script.answer(
                MockResponse.Builder().code(200).body(
                    """{"state":"candidate","follow":true,"candidate":{"source_client_updated_at":42,"book_file_id":11,"audio_position_seconds":12.5}}""",
                ).build(),
            )

            val result = repositoryFor(server.url("/")).audioResume("book-1")

            (result as CrossFormatAudioResult.Candidate).follow shouldBe true
        }

        // P-SYNC-AC5, R6: source_ahead true, false, absent (true) and null (true)
        it("#166 TC-7: source_ahead true, false, absent and null map to true, false, true and true (P-SYNC-AC5)") {
            val rows = listOf(
                """"source_ahead":true""" to true,
                """"source_ahead":false""" to false,
                "" to true,
                """"source_ahead":null""" to true,
            )

            rows.forEach { (field, expected) ->
                withClue(field.ifEmpty { "absent" }) {
                    val separator = if (field.isEmpty()) "" else ","
                    script.answer(
                        MockResponse.Builder().code(200).body(
                            """{"state":"candidate","candidate":{"source_client_updated_at":42,"book_file_id":11,"audio_position_seconds":12.5$separator$field}}""",
                        ).build(),
                    )

                    val result = repositoryFor(server.url("/")).audioResume("book-1")

                    (result as CrossFormatAudioResult.Candidate).sourceAhead shouldBe expected
                }
            }
        }

        // I-RES-7: every state other than candidate is no offer
        it("#166 TC-8: not_linked, link_stale, nothing_newer and aligned are NoCandidate (I-RES-7)") {
            listOf("not_linked", "link_stale", "nothing_newer", "aligned").forEach { state ->
                withClue(state) {
                    script.answer(MockResponse.Builder().code(200).body(candidateBody(state = state)).build())

                    repositoryFor(server.url("/")).audioResume("book-1") shouldBe CrossFormatAudioResult.NoCandidate
                }
            }
        }

        // R4: a future state the app doesn't know is no offer, and does not fail the decode
        it("#166 TC-9: an unknown future state is NoCandidate, with or without a candidate (I-RES-7)") {
            listOf(
                """{"state":"something_new"}""",
                candidateBody(state = "something_new"),
            ).forEach { body ->
                withClue(body) {
                    script.answer(MockResponse.Builder().code(200).body(body).build())

                    repositoryFor(server.url("/")).audioResume("book-1") shouldBe CrossFormatAudioResult.NoCandidate
                }
            }
        }

        // R4: a candidate with no position, or no candidate object, is no offer
        it("#166 TC-10: a candidate without a position or without a candidate object is NoCandidate (I-RES-7)") {
            listOf(
                """{"state":"candidate","candidate":{"source_client_updated_at":42,"book_file_id":11,"audio_position_seconds":null}}""",
                """{"state":"candidate","candidate":{"source_client_updated_at":42,"book_file_id":11}}""",
                """{"state":"candidate"}""",
            ).forEach { body ->
                withClue(body) {
                    script.answer(MockResponse.Builder().code(200).body(body).build())

                    repositoryFor(server.url("/")).audioResume("book-1") shouldBe CrossFormatAudioResult.NoCandidate
                }
            }
        }

        // R4: the file is not checked here, so a null file id reaches the holder
        it("#166 TC-11: a null or absent book_file_id maps to a null bookFileId (I-RES-7)") {
            listOf(
                """{"state":"candidate","candidate":{"source_client_updated_at":42,"book_file_id":null,"audio_position_seconds":12.5}}""",
                """{"state":"candidate","candidate":{"source_client_updated_at":42,"audio_position_seconds":12.5}}""",
            ).forEach { body ->
                script.answer(MockResponse.Builder().code(200).body(body).build())

                val result = repositoryFor(server.url("/")).audioResume("book-1")

                (result as CrossFormatAudioResult.Candidate).bookFileId.shouldBeNull()
            }
        }

        // R4: an HTTP error is Failed, never thrown
        it("#166 TC-12: HTTP 404 and 500 are Failed and do not throw (I-RES-7)") {
            listOf(404, 500).forEach { code ->
                withClue("$code") {
                    script.answer(MockResponse.Builder().code(code).build())

                    shouldNotThrowAny {
                        repositoryFor(server.url("/")).audioResume("book-1") shouldBe CrossFormatAudioResult.Failed
                    }
                }
            }
        }

        // R4: a body that doesn't decode is Failed, never thrown
        it("#166 TC-13: unparseable, empty and wrong-shape bodies are Failed and do not throw (I-RES-7)") {
            listOf("not json", "", "[]", "\"x\"").forEach { body ->
                withClue(body) {
                    script.answer(MockResponse.Builder().code(200).body(body).build())

                    shouldNotThrowAny {
                        repositoryFor(server.url("/")).audioResume("book-1") shouldBe CrossFormatAudioResult.Failed
                    }
                }
            }
        }

        // R4: no saved server means no request at all
        it("#166 TC-14: with no server configured the result is Failed and nothing is sent (I-RES-7)") {
            shouldNotThrowAny {
                repositoryFor(null).audioResume("book-1") shouldBe CrossFormatAudioResult.Failed
            }

            server.requestCount shouldBe 0
        }

        // R4: a dropped connection is Failed, never thrown
        it("#166 TC-15: a dropped connection is Failed and does not throw (I-RES-7)") {
            script.answer(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build())

            shouldNotThrowAny {
                repositoryFor(server.url("/")).audioResume("book-1") shouldBe CrossFormatAudioResult.Failed
            }
        }

        // R4: a blank uuid is Failed without a request
        it("#166 TC-16: a blank uuid is Failed and sends nothing (I-RES-7)") {
            repositoryFor(server.url("/")).audioResume("  ") shouldBe CrossFormatAudioResult.Failed

            server.requestCount shouldBe 0
        }

        // R4: the server adds keys over time, and optional fields may be absent
        it("#166 TC-17: extra unknown keys are ignored and absent optional fields take their defaults (I-RES-7)") {
            script.answer(
                MockResponse.Builder().code(200).body(
                    """{"state":"candidate","follow":true,"added_later":{"x":1},"candidate":{"source_client_updated_at":42,"audio_position_seconds":12.5,"extra":"x"}}""",
                ).build(),
            )

            repositoryFor(server.url("/")).audioResume("book-1") shouldBe
                CrossFormatAudioResult.Candidate(
                    positionSeconds = 12.5,
                    bookFileId = null,
                    sourceAhead = true,
                    sourceClock = 42L,
                    follow = true,
                )
        }

        // R4: cancellation is never mapped to Failed
        it("#166 TC-18: cancelling a read in flight throws CancellationException instead of Failed (I-RES-7)") {
            script.answer(
                MockResponse.Builder().code(200).body(candidateBody()).headersDelay(500, TimeUnit.MILLISECONDS).build(),
            )
            val dispatched = repositoryFor(server.url("/"), Dispatchers.IO)

            coroutineScope {
                val pending = async(Dispatchers.IO) { dispatched.audioResume("book-1") }
                delay(100)
                pending.cancel()
                shouldThrow<CancellationException> { pending.await() }
            }
        }
    }

    describe("dismissal memory") {

        // R8: the repository only delegates to the store and makes no request
        it("#166 TC-84: dismissedClock and storeDismissedClock delegate to the store with no request (R8)") {
            val repository = repositoryFor(server.url("/"))

            repository.dismissedClock("book-1").shouldBeNull()

            repository.storeDismissedClock("book-1", 77L)

            repository.dismissedClock("book-1") shouldBe 77L
            dismissStore.clocks shouldBe mapOf("book-1" to 77L)
            server.requestCount shouldBe 0
        }
    }
})
