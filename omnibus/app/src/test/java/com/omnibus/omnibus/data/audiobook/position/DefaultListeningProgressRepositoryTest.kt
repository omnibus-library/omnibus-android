package com.omnibus.omnibus.data.audiobook.position

import com.omnibus.omnibus.data.audiobook.position.local.AudioPositionStore
import com.omnibus.omnibus.data.audiobook.position.local.FakeAudioPositionStore
import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.FakeClientInfo
import com.omnibus.omnibus.data.network.FakeAuthTokenProvider
import com.omnibus.omnibus.data.network.FakeConnectivityMonitor
import com.omnibus.omnibus.data.network.FakeServerBaseUrlProvider
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okhttp3.HttpUrl

/**
 * Answers each request with the next scripted step: its hook runs first (it may write to the fake store, as a
 * concurrent save or queue would), then the step's response. A request with no step left gets a 500.
 */
private class ScriptedDispatcher : Dispatcher() {
    private val steps = ArrayDeque<Pair<(RecordedRequest) -> Unit, MockResponse>>()

    fun answer(response: MockResponse, beforeAnswer: (RecordedRequest) -> Unit = {}) {
        synchronized(steps) { steps.addLast(beforeAnswer to response) }
    }

    override fun dispatch(request: RecordedRequest): MockResponse {
        val step = synchronized(steps) { steps.removeFirstOrNull() }
            ?: return MockResponse.Builder().code(500).body("unscripted").build()
        step.first(request)
        return step.second
    }
}

class DefaultListeningProgressRepositoryTest : DescribeSpec({

    lateinit var server: MockWebServer
    lateinit var script: ScriptedDispatcher
    lateinit var store: FakeAudioPositionStore
    lateinit var connectivity: FakeConnectivityMonitor
    lateinit var applicationScope: CoroutineScope

    val position = AudioPosition("book-1", 12.5, 11, 7)
    val report = ListeningSessionReport("book-1", 988, 1000, 12, "c-1", 330, "Asia/Kolkata")

    /**
     * A repository for [baseUrl] (null means no server is saved), writing to [repositoryStore]. Every write
     * flushes on the calling thread, so a test can assert right after the call.
     */
    fun repositoryFor(
        baseUrl: HttpUrl?,
        repositoryStore: AudioPositionStore = store,
    ) = DefaultListeningProgressRepository(
        ioDispatcher = Dispatchers.Unconfined,
        omnibusHttpClient = OmnibusHttpClient(
            baseUrlProvider = FakeServerBaseUrlProvider(baseUrl = baseUrl),
            authTokenProvider = FakeAuthTokenProvider(token = "secret"),
            omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
            authClientMetadataInterceptor = AuthClientMetadataInterceptor(FakeClientInfo()),
        ),
        store = repositoryStore,
        connectivity = connectivity,
        applicationScope = applicationScope,
    )

    /** A JSON body with [records] for `GET api/progress/book-1`. */
    fun progressBody(vararg records: String) =
        """{"book_uuid":"book-1","records":[${records.joinToString(",")}],"furthest":null}"""

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    beforeEach {
        script = ScriptedDispatcher()
        server = MockWebServer().apply {
            dispatcher = script
            start()
        }
        store = FakeAudioPositionStore()
        connectivity = FakeConnectivityMonitor(online = true)
        applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    }

    afterEach {
        applicationScope.cancel()
        server.close()
    }

    describe("serverPosition") {

        it("TC-10: requests the audio format for the book (I-RES-1)") {
            script.answer(
                MockResponse.Builder().code(200).body(
                    progressBody(
                        """{"book_uuid":"book-1","format":"audio","audio_position_seconds":600.5,"updated_at":20,"client_updated_at":20,"book_file_id":11}""",
                    ),
                ).build(),
            )

            repositoryFor(server.url("/")).serverPosition("book-1") shouldBe ServerAudioPosition(600.5, 11, 20)

            val request = server.takeRequest()
            request.method shouldBe "GET"
            request.url.encodedPath shouldBe "/api/progress/book-1"
            request.url.queryParameter("format") shouldBe "audio"
        }

        it("TC-11: picks the newest positive audio record by its ordering clock (I-RES-1)") {
            val rows = listOf(
                // The newest client clock wins: 50 s at 20 beats 100 s at 10.
                Triple(
                    progressBody(
                        """{"book_uuid":"book-1","format":"audio","audio_position_seconds":100.0,"updated_at":10,"client_updated_at":10,"book_file_id":11}""",
                        """{"book_uuid":"book-1","format":"audio","audio_position_seconds":50.0,"updated_at":20,"client_updated_at":20,"book_file_id":11}""",
                    ),
                    ServerAudioPosition(50.0, 11, 20),
                    "two audio records",
                ),
                // No client clock: updated_at (30) orders the record, and it beats client 10.
                Triple(
                    progressBody(
                        """{"book_uuid":"book-1","format":"audio","audio_position_seconds":100.0,"updated_at":10,"client_updated_at":10,"book_file_id":11}""",
                        """{"book_uuid":"book-1","format":"audio","audio_position_seconds":70.0,"updated_at":30,"book_file_id":11}""",
                    ),
                    ServerAudioPosition(70.0, 11, 30),
                    "updated_at fallback",
                ),
                // Only a zero, a null, or an epub record: no audio answer.
                Triple(
                    progressBody(
                        """{"book_uuid":"book-1","format":"audio","audio_position_seconds":0,"updated_at":40,"client_updated_at":40,"book_file_id":11}""",
                        """{"book_uuid":"book-1","format":"audio","updated_at":50,"client_updated_at":50,"book_file_id":11}""",
                        """{"book_uuid":"book-1","format":"epub","epub_cfi":"epubcfi(/6/4)","updated_at":60,"client_updated_at":60}""",
                    ),
                    null,
                    "no positive audio record",
                ),
            )

            rows.forEach { (body, expected, name) ->
                withClue(name) {
                    script.answer(MockResponse.Builder().code(200).body(body).build())
                    repositoryFor(server.url("/")).serverPosition("book-1") shouldBe expected
                }
            }
        }

        it("TC-12: every failure is no server answer") {
            val rows = listOf(
                MockResponse.Builder().code(200).body("null").build() to "200 null",
                MockResponse.Builder().code(404).build() to "404",
                MockResponse.Builder().code(500).build() to "500",
                MockResponse.Builder().code(401).build() to "401",
                MockResponse.Builder().code(200).body("not json").build() to "not json",
                MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build() to "closed socket",
            )

            rows.forEach { (response, name) ->
                withClue(name) {
                    script.answer(response)
                    shouldNotThrowAny {
                        repositoryFor(server.url("/")).serverPosition("book-1").shouldBeNull()
                    }
                }
            }

            withClue("no saved server") {
                val before = server.requestCount
                shouldNotThrowAny {
                    repositoryFor(null).serverPosition("book-1").shouldBeNull()
                }
                server.requestCount shouldBe before
            }
        }
    }

    describe("localCandidate") {

        it("TC-13: the newer of cached and pending, ties keep cached (I-RES-1)") {
            fun at(seconds: Double, clock: Long) = AudioPosition("book-1", seconds, 11, clock)
            val rows = listOf(
                Triple(at(10.0, 10), at(20.0, 20), at(20.0, 20)) to "pending newer",
                Triple(at(20.0, 20), at(10.0, 10), at(20.0, 20)) to "cached newer",
                Triple(at(10.0, 10), at(11.0, 10), at(10.0, 10)) to "tie keeps cached",
            )

            rows.forEach { (row, name) ->
                withClue(name) {
                    val (cached, pending, expected) = row
                    val rowStore = FakeAudioPositionStore(
                        cached = mapOf("book-1" to cached),
                        pending = mapOf("book-1" to pending),
                    )
                    repositoryFor(null, rowStore).localCandidate("book-1") shouldBe expected
                }
            }

            withClue("only cached") {
                val rowStore = FakeAudioPositionStore(cached = mapOf("book-1" to at(10.0, 10)))
                repositoryFor(null, rowStore).localCandidate("book-1") shouldBe at(10.0, 10)
            }
            withClue("only pending") {
                val rowStore = FakeAudioPositionStore(pending = mapOf("book-1" to at(20.0, 20)))
                repositoryFor(null, rowStore).localCandidate("book-1") shouldBe at(20.0, 20)
            }
            withClue("neither") {
                repositoryFor(null, FakeAudioPositionStore()).localCandidate("book-1").shouldBeNull()
            }
        }
    }

    describe("positions") {

        it("TC-14: a local-only save sends nothing (I-SAVE-2)") {
            repositoryFor(server.url("/")).save(position, push = false)

            store.cachedPositions shouldBe mapOf("book-1" to position)
            store.pendingPositions shouldBe mapOf("book-1" to position)
            server.requestCount shouldBe 0
        }

        it("TC-15: the POST body has the audio shape (I-SAVE-1)") {
            script.answer(MockResponse.Builder().code(200).build())

            repositoryFor(server.url("/")).save(position, push = true)

            server.requestCount shouldBe 1
            val request = server.takeRequest()
            request.method shouldBe "POST"
            request.url.encodedPath shouldBe "/api/progress"
            Json.parseToJsonElement(request.body!!.utf8()) shouldBe Json.parseToJsonElement(
                """{"book_uuid":"book-1","format":"audio","epub_cfi":null,"audio_position_seconds":12.5,"client_updated_at":7,"book_file_id":11}""",
            )
        }

        it("TC-16: a 2xx clears the pending write (I-SAVE-4)") {
            script.answer(MockResponse.Builder().code(200).build())

            repositoryFor(server.url("/")).save(position, push = true)

            store.pendingPositions shouldBe emptyMap()
            store.cachedPositions shouldBe mapOf("book-1" to position)
        }

        it("TC-17: a newer save made during a POST stays pending (I-SAVE-4)") {
            val newer = AudioPosition("book-1", 40.0, 11, 9)
            script.answer(MockResponse.Builder().code(200).build()) {
                // A save from another thread lands while the POST for [position] is in flight.
                runBlocking { store.save(newer) }
            }

            repositoryFor(server.url("/")).save(position, push = true)

            store.pendingPositions shouldBe mapOf("book-1" to newer)
        }

        it("TC-18: 5xx, 401, 408, 429, IOException and no server keep the write (I-SAVE-4)") {
            val rows = listOf(
                MockResponse.Builder().code(500).build() to "500",
                MockResponse.Builder().code(502).build() to "502",
                MockResponse.Builder().code(401).build() to "401",
                MockResponse.Builder().code(408).build() to "408",
                MockResponse.Builder().code(429).build() to "429",
                MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build() to "closed socket",
            )

            rows.forEach { (response, name) ->
                withClue(name) {
                    val rowStore = FakeAudioPositionStore()
                    script.answer(response)
                    repositoryFor(server.url("/"), rowStore).save(position, push = true)
                    rowStore.pendingPositions shouldBe mapOf("book-1" to position)
                }
            }

            withClue("no saved server") {
                val rowStore = FakeAudioPositionStore()
                repositoryFor(null, rowStore).save(position, push = true)
                rowStore.pendingPositions shouldBe mapOf("book-1" to position)
            }
        }

        it("TC-19: any other 4xx drops the write (I-SAVE-4)") {
            listOf(400, 403, 404, 422).forEach { code ->
                withClue("$code") {
                    val rowStore = FakeAudioPositionStore()
                    script.answer(MockResponse.Builder().code(code).build())
                    repositoryFor(server.url("/"), rowStore).save(position, push = true)
                    rowStore.pendingPositions.shouldBeEmpty()
                    rowStore.cachedPositions shouldBe mapOf("book-1" to position)
                }
            }
        }

        it("TC-20: a flush sends every pending write once (I-SAVE-4)") {
            val books = listOf("book-a", "book-b", "book-c")
            val pending = books.associateWith { AudioPosition(it, 5.0, 11, 3) }
            val offlineStore = FakeAudioPositionStore(pending = pending)
            connectivity.isOnline.value = false
            repositoryFor(server.url("/"), offlineStore)
            books.forEach { _ -> script.answer(MockResponse.Builder().code(200).build()) }

            connectivity.isOnline.value = true

            server.requestCount shouldBe 3
            val uuids = List(3) { server.takeRequest().body!!.utf8() }
                .map { Json.parseToJsonElement(it).jsonObject["book_uuid"]!!.jsonPrimitive.content }
            uuids shouldContainExactlyInAnyOrder books
            offlineStore.pendingPositions.shouldBeEmpty()
        }

        it("TC-21: offline keeps writes pending until online (I-SAVE-4, AC5)") {
            connectivity.isOnline.value = false
            val repository = repositoryFor(server.url("/"))
            script.answer(MockResponse.Builder().code(200).build())

            repository.save(position, push = true)

            server.requestCount shouldBe 0
            store.pendingPositions shouldBe mapOf("book-1" to position)

            connectivity.isOnline.value = true

            server.requestCount shouldBe 1
            store.pendingPositions.shouldBeEmpty()
        }

        it("TC-22: construction flushes when online, else on reconnect (I-SAVE-4)") {
            val rows = listOf(true to "built online", false to "built offline")
            rows.forEach { (online, name) ->
                withClue(name) {
                    val rowStore = FakeAudioPositionStore(pending = mapOf("book-1" to position))
                    connectivity.isOnline.value = online
                    script.answer(MockResponse.Builder().code(200).build())
                    val requestsBefore = server.requestCount

                    repositoryFor(server.url("/"), rowStore)

                    if (online) {
                        server.requestCount shouldBe requestsBefore + 1
                        rowStore.pendingPositions.shouldBeEmpty()
                    } else {
                        server.requestCount shouldBe requestsBefore
                        rowStore.pendingPositions shouldBe mapOf("book-1" to position)
                        connectivity.isOnline.value = true
                        server.requestCount shouldBe requestsBefore + 1
                        rowStore.pendingPositions.shouldBeEmpty()
                    }
                }
            }
        }

        it("TC-23: saves apply in call order (I-SAVE-4)") {
            val first = AudioPosition("book-1", 10.0, 11, 1)
            val second = AudioPosition("book-1", 20.0, 11, 2)
            val third = AudioPosition("book-1", 30.0, 11, 3)
            val gate = CompletableDeferred<Unit>()
            store.saveGate = gate
            val repository = repositoryFor(null)
            val callers = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

            try {
                // UNDISPATCHED starts each call in order, so the first reaches the mutex before the second, and so on.
                val jobs = listOf(first, second, third).map { p ->
                    callers.launch(start = CoroutineStart.UNDISPATCHED) { repository.save(p, push = false) }
                }
                gate.complete(Unit)
                jobs.joinAll()
            } finally {
                callers.cancel()
            }

            store.cachedPositions shouldBe mapOf("book-1" to third)
        }
    }

    describe("sessions") {

        it("TC-24: POSTs the reports as a JSON array (I-SESS-1)") {
            script.answer(MockResponse.Builder().code(200).build())

            repositoryFor(server.url("/")).queueSession(report)

            server.requestCount shouldBe 1
            val request = server.takeRequest()
            request.method shouldBe "POST"
            request.url.encodedPath shouldBe "/api/progress/sessions"
            Json.parseToJsonElement(request.body!!.utf8()) shouldBe Json.parseToJsonElement(
                """[{"book_uuid":"book-1","format":"audio","started_at":988,"ended_at":1000,"progress_units":12,"client_id":"c-1","utc_offset_minutes":330,"time_zone":"Asia/Kolkata"}]""",
            )
            store.sessions().shouldBeEmpty()
        }

        it("TC-25: 2xx removes, 400 and 422 drop, anything else keeps (I-SESS-3)") {
            val rows = listOf(
                MockResponse.Builder().code(200).build() to emptyList<ListeningSessionReport>(),
                MockResponse.Builder().code(400).build() to emptyList(),
                MockResponse.Builder().code(422).build() to emptyList(),
                MockResponse.Builder().code(500).build() to listOf(report),
                MockResponse.Builder().code(401).build() to listOf(report),
                MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build() to listOf(report),
            )

            rows.forEach { (response, expected) ->
                withClue("${response.code} expects ${expected.size} kept") {
                    val rowStore = FakeAudioPositionStore()
                    script.answer(response)
                    repositoryFor(server.url("/"), rowStore).queueSession(report)
                    rowStore.sessions() shouldBe expected
                }
            }
        }

        it("TC-26: offline reports are sent on reconnect (I-SESS-3)") {
            connectivity.isOnline.value = false
            val repository = repositoryFor(server.url("/"))
            script.answer(MockResponse.Builder().code(200).build())

            repository.queueSession(report)

            server.requestCount shouldBe 0
            store.sessions() shouldBe listOf(report)

            connectivity.isOnline.value = true

            server.requestCount shouldBe 1
            store.sessions().shouldBeEmpty()
        }

        it("TC-27: a batch is capped at 500 (I-SESS-3)") {
            val reports = (1..501).map { ListeningSessionReport("book-1", 1, 2, 1, "c-$it", 0, "UTC") }
            store.queueSessions(reports)
            script.answer(MockResponse.Builder().code(200).build())
            script.answer(MockResponse.Builder().code(200).build())
            connectivity.isOnline.value = false
            repositoryFor(server.url("/"))

            connectivity.isOnline.value = true

            server.requestCount shouldBe 2
            Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonArray.size shouldBe 500
            Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonArray.size shouldBe 1
            store.sessions().shouldBeEmpty()
        }

        it("TC-28: a report queued during a POST survives and a retry reuses client_id (I-SESS-3)") {
            val second = ListeningSessionReport("book-2", 500, 510, 10, "c-2", 0, "UTC")
            val repository = repositoryFor(server.url("/"))
            script.answer(MockResponse.Builder().code(500).build()) {
                // A session queued while the POST for [report] is in flight.
                runBlocking { store.queueSessions(listOf(second)) }
            }

            repository.queueSession(report)

            store.sessions() shouldBe listOf(report, second)

            script.answer(MockResponse.Builder().code(200).build())
            repository.requestFlush()

            server.requestCount shouldBe 2
            server.takeRequest()
            val retry = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonArray
            retry.size shouldBe 2
            retry[0].jsonObject["client_id"]!!.jsonPrimitive.content shouldBe report.clientId
            retry[1].jsonObject["client_id"]!!.jsonPrimitive.content shouldBe second.clientId
            store.sessions().shouldBeEmpty()
        }

        it("TC-29: a flush sends positions then sessions (I-SAVE-4, I-SESS-3)") {
            connectivity.isOnline.value = false
            val rowStore = FakeAudioPositionStore(pending = mapOf("book-1" to position))
            rowStore.queueSessions(listOf(report))
            repositoryFor(server.url("/"), rowStore)
            script.answer(MockResponse.Builder().code(200).build())
            script.answer(MockResponse.Builder().code(200).build())

            connectivity.isOnline.value = true

            server.requestCount shouldBe 2
            server.takeRequest().url.encodedPath shouldBe "/api/progress"
            server.takeRequest().url.encodedPath shouldBe "/api/progress/sessions"
        }
    }
})
