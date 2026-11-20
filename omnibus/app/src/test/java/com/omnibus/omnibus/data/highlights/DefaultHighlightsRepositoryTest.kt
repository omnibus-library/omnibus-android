package com.omnibus.omnibus.data.highlights

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.omnibus.omnibus.di.ClientIdSource
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.testing.TestTimeSource
import com.omnibus.omnibus.testing.dropped
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.nextRequest
import com.omnibus.omnibus.testing.pausedDispatcher
import com.omnibus.omnibus.testing.randomEnum
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.reply
import com.omnibus.omnibus.testing.testOmnibusHttpClient
import com.omnibus.omnibus.testing.unmockAndroidLog
import com.omnibus.omnibus.testing.withMockServer
import com.omnibus.omnibus.testing.withScriptedServer
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import io.kotest.property.exhaustive.enum
import io.kotest.property.exhaustive.of
import java.util.concurrent.CountDownLatch
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest

/** A bare row as the server writes it. Its mapping is proved by [HighlightDtoTest]. */
private fun row(id: Long): String = """{"id":$id}"""

private fun loaded(vararg ids: Long) =
    HighlightsState.Loaded(ids.map { Highlight(it, null, null, HighlightColor.Amber, null, null, null, null) })

private val AWAIT_TIMEOUT = 5.seconds

/** The row a create answers with, as the server writes it: its id, colour and location. */
private fun createdRow(id: Long, draft: HighlightDraft): String =
    """{"id":$id,"color":"${draft.color.token}","epub_cfi_range":"${draft.cfiRange}"}"""

/** The highlight [createdRow] maps to: the draft's colour and location, with no text or note. */
private fun createdHighlight(id: Long, draft: HighlightDraft) =
    Highlight(id, null, null, draft.color, draft.cfiRange, "epub", null, null)

/** A draft with [text] (null for none) in a random book, at a random `epubcfi(` location. */
private fun draftWithText(text: String?): HighlightDraft = HighlightDraft.create(
    bookUuid = randomString(),
    cfiRange = "epubcfi(/6/${randomString(8..16)})",
    color = randomEnum(),
    text = text,
)!!

private fun RecordedRequest.json(): JsonObject = Json.parseToJsonElement(body!!.utf8()).jsonObject

/** Consumes states until [expected], which a real-thread fetch may reach after other states or none. */
private suspend fun ReceiveTurbine<HighlightsState>.awaitState(expected: HighlightsState) {
    while (awaitItem() != expected) Unit
}

class DefaultHighlightsRepositoryTest : DescribeSpec({

    fun sut(
        server: MockWebServer? = null,
        ioDispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
        applicationScope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
        timeSource: TimeSource = TestTimeSource(randomLong()),
        clientIdSource: ClientIdSource = ClientIdSource { randomString(8..16) },
    ) = DefaultHighlightsRepository(
        ioDispatcher = ioDispatcher,
        omnibusHttpClient = testOmnibusHttpClient(server?.url("/")),
        applicationScope = applicationScope,
        timeSource = timeSource,
        clientIdSource = clientIdSource,
    )

    /** A repository whose HTTP runs on real threads, so a test can hold a request open. */
    fun threadedSut(
        server: MockWebServer,
        scope: CoroutineScope,
        nowMillis: Long = randomLong(),
        clientId: String = randomString(8..16),
    ) = sut(server, Dispatchers.IO, scope, TestTimeSource(nowMillis), ClientIdSource { clientId })

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("observing a book's highlights") {

        it("TC-7: keeps the server's order") {
            withMockServer { server ->
                server.enqueue(reply(200, "[${row(9)},${row(2)},${row(5)}]"))

                sut(server).getHighlightsFlow(randomString()).first() shouldBe loaded(9, 2, 5)
            }
        }

        it("TC-7: asks for the book by its encoded uuid") {
            withMockServer { server ->
                server.enqueue(reply(200, "[]"))

                sut(server).getHighlightsFlow("book 1/é").first()

                server.nextRequest().url.encodedPath shouldBe "/api/highlights/book/book%201%2F%C3%A9"
            }
        }

        context("when nothing has loaded") {
            it("TC-8: shows Loading until the fetch answers") {
                withMockServer { server ->
                    server.enqueue(reply(200, "[${row(1)}]"))
                    val io = pausedDispatcher()

                    sut(server, io).getHighlightsFlow(randomString()).test {
                        awaitItem() shouldBe HighlightsState.Loading

                        io.scheduler.runCurrent()

                        awaitItem() shouldBe loaded(1)
                    }
                }
            }
        }

        context("when a list has loaded") {
            it("TC-8: answers a later collector without a request") {
                withMockServer { server ->
                    server.enqueue(reply(200, "[${row(1)}]"))
                    val repository = sut(server)
                    val uuid = randomString()
                    repository.getHighlightsFlow(uuid).first()

                    repository.getHighlightsFlow(uuid).first() shouldBe loaded(1)

                    server.requestCount shouldBe 1
                }
            }
        }

        context("when a fetch is in flight") {
            it("TC-9: shares one request among all its callers") {
                withMockServer { server ->
                    server.enqueue(reply(200, "[${row(1)}]"))
                    val io = pausedDispatcher()
                    val repository = sut(server, io)
                    val uuid = randomString()

                    coroutineScope {
                        val collectors = List(2) {
                            async(start = CoroutineStart.UNDISPATCHED) {
                                repository.getHighlightsFlow(uuid).first { it is HighlightsState.Loaded }
                            }
                        }
                        val refreshes = List(2) {
                            async(start = CoroutineStart.UNDISPATCHED) { repository.refresh(uuid) }
                        }
                        io.scheduler.runCurrent()
                        refreshes.awaitAll()
                        collectors.awaitAll()
                    }

                    server.requestCount shouldBe 1
                }
            }
        }

        context("when the server answers an empty array") {
            it("TC-10: is a loaded empty list") {
                withMockServer { server ->
                    server.enqueue(reply(200, "[]"))

                    sut(server).getHighlightsFlow(randomString()).first() shouldBe loaded()
                }
            }
        }

        context("when the read fails") {
            it("TC-11: reads as Failed for every kind of failure") {
                checkAll(
                    Exhaustive.of(
                        reply(500),
                        reply(200, "{not json"),
                        reply(200, "{}"),
                        reply(200, """[{"id":"abc"}]"""),
                        dropped(),
                    ),
                ) { answer ->
                    withScriptedServer {
                        script.answer(answer)

                        sut(server).getHighlightsFlow(randomString()).first() shouldBe HighlightsState.Failed
                    }
                }
            }

            it("TC-17: fetches again for a new collector") {
                withMockServer { server ->
                    server.enqueue(reply(500))
                    server.enqueue(reply(200, "[${row(1)}]"))
                    val repository = sut(server)
                    val uuid = randomString()
                    repository.getHighlightsFlow(uuid).first()

                    repository.getHighlightsFlow(uuid).first() shouldBe loaded(1)
                }
            }
        }

        context("when no server is saved") {
            it("TC-12: reads as Failed") {
                sut().getHighlightsFlow(randomString()).first() shouldBe HighlightsState.Failed
            }
        }

        context("when the uuid is blank") {
            it("TC-12: reads as Failed") {
                withMockServer { server ->
                    checkAll(Exhaustive.of("", "   ")) { uuid ->
                        sut(server).getHighlightsFlow(uuid).first() shouldBe HighlightsState.Failed
                    }
                }
            }

            it("TC-12: sends no request") {
                withMockServer { server ->
                    checkAll(Exhaustive.of("", "   ")) { uuid ->
                        sut(server).getHighlightsFlow(uuid).first()
                    }

                    server.requestCount shouldBe 0
                }
            }
        }

        context("when another book's read answers") {
            it("TC-15: emits nothing for this book") {
                withMockServer { server ->
                    server.enqueue(reply(500))
                    val repository = sut(server)

                    repository.getHighlightsFlow("A").test {
                        awaitItem() shouldBe HighlightsState.Failed
                        server.enqueue(reply(200, "[${row(1)}]"))

                        repository.getHighlightsFlow("B").first()

                        expectNoEvents()
                    }
                }
            }
        }
    }

    describe("refreshing a book's highlights") {

        context("when a list has loaded") {
            context("when the refresh fails") {
                it("TC-13: emits nothing, leaving the list") {
                    withMockServer { server ->
                        server.enqueue(reply(200, "[${row(1)}]"))
                        val repository = sut(server)
                        val uuid = randomString()

                        repository.getHighlightsFlow(uuid).test {
                            awaitItem() shouldBe loaded(1)
                            server.enqueue(reply(500))

                            repository.refresh(uuid)

                            expectNoEvents()
                        }
                    }
                }
            }

            context("when the refresh answers") {
                it("TC-13: emits only the new list") {
                    withMockServer { server ->
                        server.enqueue(reply(200, "[${row(1)}]"))
                        val repository = sut(server)
                        val uuid = randomString()

                        repository.getHighlightsFlow(uuid).test {
                            awaitItem() shouldBe loaded(1)
                            server.enqueue(reply(200, "[${row(2)},${row(3)}]"))

                            repository.refresh(uuid)

                            awaitItem() shouldBe loaded(2, 3)
                            expectNoEvents()
                        }
                    }
                }
            }
        }

        context("when the last fetch failed") {
            it("TC-14: shows Loading, then the list") {
                withMockServer { server ->
                    server.enqueue(reply(500))
                    server.enqueue(reply(200, "[${row(1)}]"))
                    val io = pausedDispatcher()
                    val repository = sut(server, io)
                    val uuid = randomString()

                    repository.getHighlightsFlow(uuid).test {
                        awaitItem() shouldBe HighlightsState.Loading
                        io.scheduler.runCurrent()
                        awaitItem() shouldBe HighlightsState.Failed

                        val refresh = CoroutineScope(Dispatchers.Unconfined).async { repository.refresh(uuid) }

                        awaitItem() shouldBe HighlightsState.Loading
                        io.scheduler.runCurrent()
                        refresh.await()
                        awaitItem() shouldBe loaded(1)
                    }
                }
            }
        }

        context("when the caller is cancelled") {
            it("TC-16: still completes the shared fetch") {
                withMockServer { server ->
                    server.enqueue(reply(200, "[${row(1)}]"))
                    val io = pausedDispatcher()
                    val repository = sut(server, io)
                    val uuid = randomString()
                    val caller = CoroutineScope(Dispatchers.Unconfined).launch { repository.refresh(uuid) }

                    caller.cancelAndJoin()
                    io.scheduler.runCurrent()

                    repository.getHighlightsFlow(uuid).first() shouldBe loaded(1)
                }
            }
        }
    }

    describe("adding a highlight") {

        context("when the draft has text") {
            it("#54 TC-9: sends one flat body with the draft's range, colour and text, and the minted id and clock") {
                withScriptedServer {
                    val nowMillis = randomLong()
                    val clientId = randomString()
                    val draft = draftWithText(randomString())
                    script.answer("POST", reply(200, createdRow(randomLong(), draft)))

                    sut(server, timeSource = TestTimeSource(nowMillis), clientIdSource = ClientIdSource { clientId })
                        .addHighlight(draft)

                    val request = server.nextRequest()
                    request.method shouldBe "POST"
                    request.url.encodedPath shouldBe "/api/highlights"
                    val body = request.json()
                    body.keys shouldBe setOf("book_uuid", "epub_cfi_range", "color", "text", "client_id", "client_created_at")
                    body["book_uuid"]!!.jsonPrimitive.content shouldBe draft.bookUuid
                    body["epub_cfi_range"]!!.jsonPrimitive.content shouldBe draft.cfiRange
                    body["color"]!!.jsonPrimitive.content shouldBe draft.color.token
                    body["text"]!!.jsonPrimitive.content shouldBe draft.text
                    body["client_id"]!!.jsonPrimitive.content shouldBe clientId
                    body["client_created_at"]!!.jsonPrimitive.long shouldBe nowMillis / 1000
                }
            }
        }

        context("when the draft has no text") {
            it("#54 TC-9: leaves the text out of the body") {
                withScriptedServer {
                    val draft = draftWithText(null)
                    script.answer("POST", reply(200, createdRow(randomLong(), draft)))

                    sut(server).addHighlight(draft)

                    server.nextRequest().json().keys shouldBe
                        setOf("book_uuid", "epub_cfi_range", "color", "client_id", "client_created_at")
                }
            }
        }

        context("when the server accepts the highlight") {
            it("#54 TC-10: returns the saved row") {
                withMockServer { server ->
                    val draft = randomHighlightDraft()
                    server.enqueue(reply(200, createdRow(2, draft)))

                    sut(server).addHighlight(draft) shouldBe HighlightWriteResult.Saved(createdHighlight(2, draft))
                }
            }
        }

        context("when a list has loaded") {
            it("#54 TC-10: appends the saved row to the end of the list") {
                withMockServer { server ->
                    val draft = randomHighlightDraft()
                    server.enqueue(reply(200, "[${row(1)}]"))
                    server.enqueue(reply(200, createdRow(2, draft)))
                    val repository = sut(server)

                    repository.getHighlightsFlow(draft.bookUuid).test {
                        awaitItem() shouldBe loaded(1)

                        repository.addHighlight(draft)

                        awaitItem() shouldBe HighlightsState.Loaded(loaded(1).highlights + createdHighlight(2, draft))
                    }
                }
            }

            context("when the saved row is already listed") {
                it("#54 TC-10: does not list the row twice") {
                    withMockServer { server ->
                        val draft = randomHighlightDraft()
                        server.enqueue(reply(200, "[${row(1)}]"))
                        server.enqueue(reply(200, createdRow(1, draft)))
                        val repository = sut(server)

                        repository.getHighlightsFlow(draft.bookUuid).test {
                            awaitItem() shouldBe loaded(1)

                            repository.addHighlight(draft)

                            expectNoEvents()
                        }
                    }
                }
            }
        }

        context("when the list has failed") {
            it("#54 TC-10: leaves the list Failed") {
                withMockServer { server ->
                    val draft = randomHighlightDraft()
                    server.enqueue(reply(500))
                    server.enqueue(reply(200, createdRow(2, draft)))
                    // Reading again shows the state: a Failed list is re-read, so a 500 keeps it Failed.
                    server.enqueue(reply(500))
                    val repository = sut(server)
                    repository.getHighlightsFlow(draft.bookUuid).first() shouldBe HighlightsState.Failed

                    repository.addHighlight(draft)

                    repository.getHighlightsFlow(draft.bookUuid).first() shouldBe HighlightsState.Failed
                }
            }
        }

        context("when nothing has loaded") {
            it("#54 TC-10: leaves the list unloaded") {
                withMockServer { server ->
                    val draft = randomHighlightDraft()
                    server.enqueue(reply(200, createdRow(2, draft)))
                    // Reading after the create shows the state: with nothing loaded, a 500 reads as Failed.
                    server.enqueue(reply(500))
                    val repository = sut(server)

                    repository.addHighlight(draft)

                    repository.getHighlightsFlow(draft.bookUuid).first() shouldBe HighlightsState.Failed
                }
            }
        }

        context("when the add fails") {
            it("#54 TC-11: returns the typed error for each answer") {
                withScriptedServer {
                    val failures = listOf(
                        dropped() to HighlightError.Unreachable,
                        reply(400) to HighlightError.Rejected,
                        reply(401) to HighlightError.SessionExpired,
                        reply(404) to HighlightError.BookNotFound,
                        reply(500) to HighlightError.Http(500),
                        reply(200, "{not json") to HighlightError.UnexpectedResponse,
                    )
                    checkAll(Exhaustive.collection(failures)) { (answer, error) ->
                        script.answer("POST", answer)

                        sut(server).addHighlight(randomHighlightDraft()) shouldBe HighlightWriteResult.Failed(error)
                    }
                }
            }

            context("when a list has loaded") {
                it("#54 TC-11: keeps the loaded list") {
                    withMockServer { server ->
                        val draft = randomHighlightDraft()
                        server.enqueue(reply(200, "[${row(1)}]"))
                        server.enqueue(reply(500))
                        val repository = sut(server)

                        repository.getHighlightsFlow(draft.bookUuid).test {
                            awaitItem() shouldBe loaded(1)

                            repository.addHighlight(draft)

                            expectNoEvents()
                        }
                    }
                }
            }
        }

        context("when no server is saved") {
            it("#54 TC-11: returns NoServer") {
                sut().addHighlight(randomHighlightDraft()) shouldBe HighlightWriteResult.Failed(HighlightError.NoServer)
            }
        }

        context("when the first fetch is still in flight") {
            it("#54 TC-12: keeps the row across the late list") {
                withScriptedServer {
                    val draft = randomHighlightDraft()
                    val repository = threadedSut(server, scope)
                    val getGate = CountDownLatch(1)
                    script.answer("GET", reply(200, "[]"), getGate)
                    script.answer("POST", reply(200, createdRow(2, draft)))

                    repository.getHighlightsFlow(draft.bookUuid).test(timeout = AWAIT_TIMEOUT) {
                        awaitItem() shouldBe HighlightsState.Loading

                        repository.addHighlight(draft)
                        getGate.countDown()

                        awaitItem() shouldBe HighlightsState.Loaded(listOf(createdHighlight(2, draft)))
                    }
                }
            }
        }

        context("when the caller is cancelled mid-request") {
            it("#54 TC-13: still saves the row") {
                withScriptedServer {
                    val draft = randomHighlightDraft()
                    val repository = threadedSut(server, scope)
                    val gate = CountDownLatch(1)
                    script.answer("GET", reply(200, "[]"))
                    script.answer("POST", reply(200, createdRow(2, draft)), gate)

                    repository.getHighlightsFlow(draft.bookUuid).test(timeout = AWAIT_TIMEOUT) {
                        awaitState(loaded())
                        server.nextRequest()

                        val caller = scope.async { repository.addHighlight(draft) }
                        // The create is on the server and held on its gate when the caller is cancelled.
                        server.nextRequest()
                        caller.cancelAndJoin()

                        gate.countDown()

                        awaitItem() shouldBe HighlightsState.Loaded(listOf(createdHighlight(2, draft)))
                    }
                }
            }
        }
    }

    describe("deleting a highlight") {

        it("#300 TC-1: sends DELETE for the id") {
            withScriptedServer {
                val id = randomLong()
                script.answer("DELETE", reply(204))

                sut(server).deleteHighlight(randomString(), id)

                val request = server.nextRequest()
                request.method shouldBe "DELETE"
                request.url.encodedPath shouldBe "/api/highlights/$id"
            }
        }

        it("#300 TC-2: returns Deleted for 204 and 404") {
            withScriptedServer {
                checkAll(Exhaustive.of(204, 404)) { code ->
                    script.answer("DELETE", reply(code))

                    sut(server).deleteHighlight(randomString(), randomLong()) shouldBe HighlightDeleteResult.Deleted
                }
            }
        }

        context("when a list has loaded") {
            it("#300 TC-3: drops the row from a loaded list, keeping the rest in order") {
                withMockServer { server ->
                    server.enqueue(reply(200, "[${row(1)},${row(2)},${row(3)}]"))
                    server.enqueue(reply(204))
                    val repository = sut(server)
                    val uuid = randomString()

                    repository.getHighlightsFlow(uuid).test {
                        awaitItem() shouldBe loaded(1, 2, 3)

                        repository.deleteHighlight(uuid, 2)

                        awaitItem() shouldBe loaded(1, 3)
                    }
                }
            }

            it("#300 TC-4: leaves a loaded list as it was") {
                withMockServer { server ->
                    server.enqueue(reply(200, "[${row(1)}]"))
                    server.enqueue(reply(500))
                    val repository = sut(server)
                    val uuid = randomString()

                    repository.getHighlightsFlow(uuid).test {
                        awaitItem() shouldBe loaded(1)

                        repository.deleteHighlight(uuid, 1)

                        expectNoEvents()
                    }
                }
            }

            context("when a fetch is in flight") {
                it("#300 TC-5: a fetch in flight cannot bring the row back") {
                    withScriptedServer {
                        val repository = threadedSut(server, scope)
                        val uuid = randomString()
                        val gate = CountDownLatch(1)
                        script.answer("GET", reply(200, "[${row(1)},${row(2)}]"))
                        script.answer("GET", reply(200, "[${row(1)},${row(2)}]"), gate)
                        script.answer("DELETE", reply(204))

                        repository.getHighlightsFlow(uuid).test(timeout = AWAIT_TIMEOUT) {
                            awaitState(loaded(1, 2))
                            server.nextRequest()

                            val refresh = scope.async { repository.refresh(uuid) }
                            // The refresh's GET is on the server and held on its gate while the delete succeeds.
                            server.nextRequest()
                            repository.deleteHighlight(uuid, 2)

                            gate.countDown()
                            refresh.await()
                            cancelAndIgnoreRemainingEvents()
                        }

                        repository.getHighlightsFlow(uuid).first() shouldBe loaded(1)
                    }
                }
            }
        }

        context("when no server is saved") {
            it("#300 TC-4: returns NoServer") {
                sut().deleteHighlight(randomString(), randomLong()) shouldBe
                    HighlightDeleteResult.Failed(HighlightError.NoServer)
            }
        }

        it("#300 TC-4: returns the typed error for each answer") {
            withScriptedServer {
                val failures = listOf(
                    dropped() to HighlightError.Unreachable,
                    reply(401) to HighlightError.SessionExpired,
                    reply(500) to HighlightError.Http(500),
                    reply(409) to HighlightError.Http(409),
                )
                checkAll(Exhaustive.collection(failures)) { (answer, error) ->
                    script.answer("DELETE", answer)

                    sut(server).deleteHighlight(randomString(), randomLong()) shouldBe HighlightDeleteResult.Failed(error)
                }
            }
        }
    }

    describe("recolouring a highlight") {

        it("#301 TC-1: sends PATCH for the id with the lowercase colour token") {
            withScriptedServer {
                val id = randomLong()
                checkAll(Exhaustive.enum<HighlightColor>()) { color ->
                    script.answer("PATCH", reply(204))

                    sut(server).recolorHighlight(randomString(), id, color)

                    val request = server.nextRequest()
                    request.method shouldBe "PATCH"
                    request.url.encodedPath shouldBe "/api/highlights/$id/color"
                    val body = request.json()
                    body.keys shouldBe setOf("color")
                    body["color"]!!.jsonPrimitive.content shouldBe color.token
                }
            }
        }

        it("#301 TC-1: returns Recolored for any 2xx") {
            withScriptedServer {
                checkAll(Exhaustive.of(200, 204)) { code ->
                    script.answer("PATCH", reply(code))

                    sut(server).recolorHighlight(randomString(), randomLong(), randomEnum()) shouldBe
                        HighlightRecolorResult.Recolored
                }
            }
        }

        it("#301 TC-3: returns the typed error for each answer") {
            withScriptedServer {
                val failures = listOf(
                    dropped() to HighlightError.Unreachable,
                    reply(401) to HighlightError.SessionExpired,
                    reply(404) to HighlightError.Http(404),
                    reply(409) to HighlightError.Http(409),
                    reply(500) to HighlightError.Http(500),
                )
                checkAll(Exhaustive.collection(failures)) { (answer, error) ->
                    script.answer("PATCH", answer)

                    sut(server).recolorHighlight(randomString(), randomLong(), randomEnum()) shouldBe
                        HighlightRecolorResult.Failed(error)
                }
            }
        }

        context("when no server is saved") {
            it("#301 TC-3: returns NoServer") {
                sut().recolorHighlight(randomString(), randomLong(), randomEnum()) shouldBe
                    HighlightRecolorResult.Failed(HighlightError.NoServer)
            }
        }

        context("when a list has loaded") {
            it("#301 TC-4: replaces only the row's colour") {
                withMockServer { server ->
                    val passage = """{"id":2,"text":"a passage","note":"a note","color":"amber","epub_cfi_range":"epubcfi(/6/4)"}"""
                    server.enqueue(reply(200, "[${row(1)},$passage,${row(3)}]"))
                    server.enqueue(reply(204))
                    val repository = sut(server)
                    val uuid = randomString()

                    repository.getHighlightsFlow(uuid).test {
                        val before = awaitItem() as HighlightsState.Loaded

                        repository.recolorHighlight(uuid, 2, HighlightColor.Green)

                        awaitItem() shouldBe HighlightsState.Loaded(
                            before.highlights.map { if (it.id == 2L) it.copy(color = HighlightColor.Green) else it },
                        )
                    }
                }
            }

            it("#301 TC-5: leaves a loaded list as it was") {
                withMockServer { server ->
                    server.enqueue(reply(200, "[${row(1)}]"))
                    server.enqueue(reply(500))
                    val repository = sut(server)
                    val uuid = randomString()

                    repository.getHighlightsFlow(uuid).test {
                        awaitItem() shouldBe loaded(1)

                        repository.recolorHighlight(uuid, 1, randomEnum())

                        expectNoEvents()
                    }
                }
            }

            context("when a fetch is in flight") {
                it("#301 TC-6: a fetch in flight cannot put the old colour back") {
                    withScriptedServer {
                        val repository = threadedSut(server, scope)
                        val uuid = randomString()
                        val gate = CountDownLatch(1)
                        script.answer("GET", reply(200, "[${row(1)},${row(2)}]"))
                        script.answer("GET", reply(200, "[${row(1)},${row(2)}]"), gate)
                        script.answer("PATCH", reply(204))

                        repository.getHighlightsFlow(uuid).test(timeout = AWAIT_TIMEOUT) {
                            awaitState(loaded(1, 2))
                            server.nextRequest()

                            val refresh = scope.async { repository.refresh(uuid) }
                            // The refresh's GET is on the server and held on its gate while the recolour succeeds.
                            server.nextRequest()
                            repository.recolorHighlight(uuid, 2, HighlightColor.Green)

                            gate.countDown()
                            refresh.await()
                            cancelAndIgnoreRemainingEvents()
                        }

                        repository.getHighlightsFlow(uuid).first() shouldBe HighlightsState.Loaded(
                            loaded(1, 2).highlights.map { if (it.id == 2L) it.copy(color = HighlightColor.Green) else it },
                        )
                    }
                }
            }
        }
    }
})
