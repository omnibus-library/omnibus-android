package com.omnibus.omnibus.data.audiobook.position

import com.omnibus.omnibus.data.audiobook.ScriptedServer
import com.omnibus.omnibus.data.audiobook.httpClientFor
import com.omnibus.omnibus.data.audiobook.position.local.AudioPositionStore
import com.omnibus.omnibus.data.audiobook.position.local.TestAudioPositionStore
import com.omnibus.omnibus.data.audiobook.droppedConnection
import com.omnibus.omnibus.data.audiobook.reply
import com.omnibus.omnibus.data.audiobook.unreachableUrl
import com.omnibus.omnibus.data.network.ConnectivityMonitor
import com.omnibus.omnibus.data.network.TestConnectivityMonitor
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
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
import okhttp3.HttpUrl

/** Runs [block] with a scripted server and an application scope that flushes on the calling thread. */
private inline fun withServer(block: (ScriptedServer, CoroutineScope) -> Unit) {
    ScriptedServer().use { server ->
        withScope { scope -> block(server, scope) }
    }
}

private inline fun withScope(block: (CoroutineScope) -> Unit) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    try {
        block(scope)
    } finally {
        scope.cancel()
    }
}

/** A JSON body with [records] for the book's progress. */
private fun progressBody(vararg records: String) = """{"book_uuid":"b","records":[${records.joinToString(",")}],"furthest":null}"""

private fun audioRecord(seconds: Double, updatedAt: Long, clientUpdatedAt: Long?, file: Long = 11): String =
    """{"book_uuid":"b","format":"audio","audio_position_seconds":$seconds,"updated_at":$updatedAt,
        "client_updated_at":$clientUpdatedAt,"book_file_id":$file}"""

class DefaultListeningProgressRepositoryTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    /** A repository for [baseUrl] (null means no server is saved). Every write flushes on the calling thread. */
    fun sut(
        scope: CoroutineScope,
        baseUrl: HttpUrl? = null,
        store: AudioPositionStore = TestAudioPositionStore(),
        connectivity: ConnectivityMonitor = TestConnectivityMonitor(online = true),
    ) = DefaultListeningProgressRepository(
        ioDispatcher = Dispatchers.Unconfined,
        omnibusHttpClient = httpClientFor(baseUrl),
        store = store,
        connectivity = connectivity,
        applicationScope = scope,
    )

    describe("reading the server's position") {

        // TC-10
        it("TC-10: asks for the book's audio format (I-RES-1)") {
            withServer { server, scope ->
                val book = randomString()
                server.answer(200, progressBody())

                sut(scope, server.baseUrl).serverPosition(book)

                val request = server.takeRequest()
                request.url.encodedPath shouldBe "/api/progress/$book"
                request.url.queryParameter("format") shouldBe "audio"
            }
        }

        // TC-11, TC-30: the client clock orders the records, or updated_at when a record has none
        it("TC-11, TC-30: picks the newest positive audio record, for each mix of records (I-RES-1)") {
            withServer { server, scope ->
                checkAll(
                    Exhaustive.of(
                        progressBody(audioRecord(100.0, 10, 10), audioRecord(50.0, 20, 20)) to
                            ServerAudioPosition(50.0, 11, 20),
                        progressBody(audioRecord(100.0, 10, 10), audioRecord(70.0, 30, null)) to
                            ServerAudioPosition(70.0, 11, 30),
                        progressBody(
                            audioRecord(0.0, 40, 40),
                            """{"book_uuid":"b","format":"epub","epub_cfi":"epubcfi(/6/4)","updated_at":60,"client_updated_at":60}""",
                        ) to null,
                    ),
                ) { (body, expected) ->
                    server.answer(200, body)

                    sut(scope, server.baseUrl).serverPosition(randomString()) shouldBe expected
                }
            }
        }

        // TC-12
        it("TC-12: answers null for each kind of failed response") {
            withServer { server, scope ->
                checkAll(
                    Exhaustive.of(
                        reply(200, "null"),
                        reply(404),
                        reply(500),
                        reply(401),
                        reply(200, "not json"),
                        droppedConnection(),
                    ),
                ) { response ->
                    server.answer(response)

                    sut(scope, server.baseUrl).serverPosition(randomString()).shouldBeNull()
                }
            }
        }
    }

    describe("reading the local candidate") {

        // TC-13: a tie keeps the cached one
        it("TC-13: answers the newer local position, for each cached-pending pair (I-RES-1)") {
            val book = randomString()
            val older = randomAudioPosition().copy(bookUuid = book, clientUpdatedAt = 10)
            val newer = older.copy(clientUpdatedAt = 20)
            val tied = older.copy(positionSeconds = older.positionSeconds + 1)

            checkAll(
                Exhaustive.of(
                    Triple(older, newer, newer),
                    Triple(newer, older, newer),
                    Triple(older, tied, older),
                    Triple(older, null, older),
                    Triple(null, newer, newer),
                    Triple(null, null, null),
                ),
            ) { (cached, pending, expected) ->
                withScope { scope ->
                    val store = TestAudioPositionStore(
                        cached = listOfNotNull(cached).associateBy { it.bookUuid },
                        pending = listOfNotNull(pending).associateBy { it.bookUuid },
                    )

                    sut(scope, store = store).localCandidate(book) shouldBe expected
                }
            }
        }
    }

    describe("saving a position") {

        context("when it is not pushed") {
            // TC-14
            it("TC-14: sends nothing (I-SAVE-2)") {
                withServer { server, scope ->
                    sut(scope, server.baseUrl).save(randomAudioPosition(), push = false)

                    server.requestCount shouldBe 0
                }
            }
        }

        context("when it is pushed") {
            // TC-15
            it("TC-15: posts the audio shape (I-SAVE-1)") {
                withServer { server, scope ->
                    val position = randomAudioPosition()
                    server.answer(200)

                    sut(scope, server.baseUrl).save(position, push = true)

                    val request = server.takeRequest()
                    request.url.encodedPath shouldBe "/api/progress"
                    Json.parseToJsonElement(request.body!!.utf8()) shouldBe Json.parseToJsonElement(
                        """{"book_uuid":"${position.bookUuid}","format":"audio","epub_cfi":null,
                           "audio_position_seconds":${position.positionSeconds},
                           "client_updated_at":${position.clientUpdatedAt},"book_file_id":${position.bookFileId}}""",
                    )
                }
            }

            // TC-16, TC-19: a 2xx is accepted, and any other 4xx can never succeed
            it("TC-16, TC-19: clears the pending write on a 2xx or a final 4xx (I-SAVE-4)") {
                withServer { server, scope ->
                    checkAll(Exhaustive.of(200, 400, 403, 404, 422)) { code ->
                        val store = TestAudioPositionStore()
                        server.answer(code)

                        sut(scope, server.baseUrl, store).save(randomAudioPosition(), push = true)

                        store.pendingPositions.shouldBeEmpty()
                    }
                }
            }

            // TC-17
            it("TC-17: keeps a newer save made during the POST (I-SAVE-4)") {
                withServer { server, scope ->
                    val store = TestAudioPositionStore()
                    val position = randomAudioPosition()
                    val newer = position.copy(clientUpdatedAt = position.clientUpdatedAt + 1)
                    server.answer(reply(200)) { runBlocking { store.save(newer) } }

                    sut(scope, server.baseUrl, store).save(position, push = true)

                    store.pendingPositions shouldBe mapOf(position.bookUuid to newer)
                }
            }

            // TC-18
            it("TC-18: keeps the write on each retryable status (I-SAVE-4)") {
                withServer { server, scope ->
                    checkAll(Exhaustive.of(500, 502, 401, 408, 429)) { code ->
                        val store = TestAudioPositionStore()
                        val position = randomAudioPosition()
                        server.answer(code)

                        sut(scope, server.baseUrl, store).save(position, push = true)

                        store.pendingPositions shouldBe mapOf(position.bookUuid to position)
                    }
                }
            }

            // TC-18
            it("TC-18: keeps the write with a missing or unreachable server (I-SAVE-4)") {
                checkAll(Exhaustive.of(null, unreachableUrl())) { baseUrl ->
                    withScope { scope ->
                        val store = TestAudioPositionStore()
                        val position = randomAudioPosition()

                        sut(scope, baseUrl, store).save(position, push = true)

                        store.pendingPositions shouldBe mapOf(position.bookUuid to position)
                    }
                }
            }

            context("when offline") {
                // TC-21
                it("TC-21: sends nothing (I-SAVE-4, AC5)") {
                    withServer { server, scope ->
                        val repository = sut(scope, server.baseUrl, connectivity = TestConnectivityMonitor(online = false))

                        repository.save(randomAudioPosition(), push = true)

                        server.requestCount shouldBe 0
                    }
                }
            }
        }

        // TC-23: the first save waits on the store, so the later ones must queue behind it
        it("TC-23: applies saves in call order (I-SAVE-4)") {
            withScope { scope ->
                val store = TestAudioPositionStore()
                val gate = CompletableDeferred<Unit>()
                store.saveGate = gate
                val repository = sut(scope, store = store)
                val first = randomAudioPosition()
                val last = first.copy(clientUpdatedAt = first.clientUpdatedAt + 1)

                val saves = listOf(first, last).map { position ->
                    scope.launch(start = CoroutineStart.UNDISPATCHED) { repository.save(position, push = false) }
                }
                gate.complete(Unit)
                saves.joinAll()

                store.cachedPositions shouldBe mapOf(first.bookUuid to last)
            }
        }
    }

    describe("flushing the outbox") {

        context("when the device comes online") {
            // TC-20
            it("TC-20: sends each pending write (I-SAVE-4)") {
                withServer { server, scope ->
                    val books = listOf("book-a", "book-b", "book-c")
                    val connectivity = TestConnectivityMonitor(online = false)
                    val store = TestAudioPositionStore(pending = books.associateWith { randomAudioPosition().copy(bookUuid = it) })
                    sut(scope, server.baseUrl, store, connectivity)
                    books.forEach { _ -> server.answer(200) }

                    connectivity.isOnline.value = true

                    val sent = books.map { _ -> Json.parseToJsonElement(server.takeRequest().body!!.utf8()) }
                    sent.map { it.jsonObject["book_uuid"]!!.jsonPrimitive.content } shouldContainExactlyInAnyOrder books
                }
            }
        }

        context("when the repository is built online") {
            // TC-22
            it("TC-22: sends what is pending (I-SAVE-4)") {
                withServer { server, scope ->
                    val position = randomAudioPosition()
                    val store = TestAudioPositionStore(pending = mapOf(position.bookUuid to position))
                    server.answer(200)

                    sut(scope, server.baseUrl, store)

                    store.pendingPositions.shouldBeEmpty()
                }
            }
        }
    }

    describe("queueing a listening session") {

        // TC-24
        it("TC-24: posts the report in a JSON array (I-SESS-1)") {
            withServer { server, scope ->
                val report = randomListeningSessionReport()
                server.answer(200)

                sut(scope, server.baseUrl).queueSession(report)

                val request = server.takeRequest()
                request.url.encodedPath shouldBe "/api/progress/sessions"
                Json.parseToJsonElement(request.body!!.utf8()) shouldBe Json.parseToJsonElement(
                    """[{"book_uuid":"${report.bookUuid}","format":"audio","started_at":${report.startedAt},
                        "ended_at":${report.endedAt},"progress_units":${report.progressUnits},
                        "client_id":"${report.clientId}","utc_offset_minutes":${report.utcOffsetMinutes},
                        "time_zone":"${report.timeZone}"}]""",
                )
            }
        }

        // TC-25
        it("TC-25: removes the report on a 2xx, 400 or 422 (I-SESS-3)") {
            withServer { server, scope ->
                checkAll(Exhaustive.of(200, 400, 422)) { code ->
                    val store = TestAudioPositionStore()
                    server.answer(code)

                    sut(scope, server.baseUrl, store).queueSession(randomListeningSessionReport())

                    store.sessions().shouldBeEmpty()
                }
            }
        }

        // TC-25
        it("TC-25: keeps the report on each other status (I-SESS-3)") {
            withServer { server, scope ->
                checkAll(Exhaustive.of(500, 401)) { code ->
                    val store = TestAudioPositionStore()
                    val report = randomListeningSessionReport()
                    server.answer(code)

                    sut(scope, server.baseUrl, store).queueSession(report)

                    store.sessions() shouldBe listOf(report)
                }
            }
        }

        // TC-27: the server rejects a larger POST with 422
        it("TC-27: sends at most 500 reports in a POST (I-SESS-3)") {
            withServer { server, scope ->
                val store = TestAudioPositionStore()
                store.queueSessions(List(501) { randomListeningSessionReport().copy(clientId = "c-$it") })
                server.answer(200)
                server.answer(200)

                sut(scope, server.baseUrl, store)

                val sizes = List(2) { Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonArray.size }
                sizes shouldBe listOf(500, 1)
            }
        }

        context("when another report is queued during a failing POST") {
            // TC-28
            it("TC-28: keeps both reports (I-SESS-3)") {
                withServer { server, scope ->
                    val store = TestAudioPositionStore()
                    val report = randomListeningSessionReport()
                    val second = randomListeningSessionReport()
                    server.answer(reply(500)) { runBlocking { store.queueSessions(listOf(second)) } }

                    sut(scope, server.baseUrl, store).queueSession(report)

                    store.sessions() shouldBe listOf(report, second)
                }
            }
        }

        context("when a failed report is retried") {
            // TC-28: the server drops a repeated client id, so a retry never counts twice
            it("TC-28: reuses its client id (I-SESS-3)") {
                withServer { server, scope ->
                    val report = randomListeningSessionReport()
                    val repository = sut(scope, server.baseUrl)
                    server.answer(500)
                    repository.queueSession(report)
                    server.answer(200)

                    repository.requestFlush()

                    server.takeRequest()
                    val retry = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonArray
                    retry.single().jsonObject["client_id"]!!.jsonPrimitive.content shouldBe report.clientId
                }
            }
        }
    }
})
