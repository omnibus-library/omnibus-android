package com.omnibus.omnibus.data.progress

import com.omnibus.omnibus.data.progress.local.ReadingPositionStore
import com.omnibus.omnibus.data.progress.local.TestReadingPositionStore
import com.omnibus.omnibus.testing.dropped
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.nextRequest
import com.omnibus.omnibus.testing.pausedDispatcher
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
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockWebServer

class DefaultProgressRepositoryTest : DescribeSpec({

    fun sut(
        server: MockWebServer? = null,
        store: ReadingPositionStore = TestReadingPositionStore(),
        ioDispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    ) = DefaultProgressRepository(
        ioDispatcher = ioDispatcher,
        omnibusHttpClient = testOmnibusHttpClient(server?.url("/")),
        store = store,
    )

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("saving a position") {

        it("TC-9: stores it locally, whether the push happens or not") {
            checkAll(Exhaustive.of(true, false)) { push ->
                withMockServer { server ->
                    server.enqueue(reply(500))
                    val store = TestReadingPositionStore()
                    val position = randomReadingPosition()

                    sut(server, store).savePosition(position, push)

                    store.getPositionFlow(position.bookUuid).first() shouldBe position
                }
            }
        }

        context("when asked to push") {
            it("TC-9: reports Pushed") {
                withMockServer { server ->
                    server.enqueue(reply(200))

                    sut(server).savePosition(randomReadingPosition(), push = true) shouldBe PositionSaveResult.Pushed
                }
            }

            it("TC-7: posts the update payload") {
                withMockServer { server ->
                    server.enqueue(reply(200))

                    sut(server).savePosition(ReadingPosition("book-1", "epubcfi(/6/4)", 40, 1_700_000_000), push = true)

                    val request = server.nextRequest()
                    request.method shouldBe "POST"
                    request.url.encodedPath shouldBe "/api/progress"
                    Json.parseToJsonElement(request.body!!.utf8()) shouldBe Json.parseToJsonElement(
                        """{"book_uuid":"book-1","format":"epub","epub_cfi":"epubcfi(/6/4)","progress_percent":40,"client_updated_at":1700000000}""",
                    )
                }
            }

            context("when an optional field is absent") {
                it("TC-8: omits it from the payload") {
                    withMockServer { server ->
                        server.enqueue(reply(200))

                        sut(server).savePosition(randomReadingPosition().copy(cfi = randomString(), percent = null), push = true)

                        Json.parseToJsonElement(server.nextRequest().body!!.utf8()).jsonObject.keys shouldBe
                            setOf("book_uuid", "format", "epub_cfi", "client_updated_at")
                    }
                }
            }

            context("when no server is saved") {
                it("TC-9: reports NoServer") {
                    sut().savePosition(randomReadingPosition(), push = true) shouldBe PositionSaveResult.NoServer
                }
            }

            context("when the push fails") {
                it("TC-12: reports why, for every kind of failure") {
                    checkAll(
                        Exhaustive.of(
                            reply(500) to ProgressError.Http(500),
                            reply(401) to ProgressError.SessionExpired,
                            dropped() to ProgressError.Unreachable,
                        ),
                    ) { (answer, error) ->
                        withScriptedServer {
                            script.answer(answer)

                            sut(server).savePosition(randomReadingPosition(), push = true) shouldBe
                                PositionSaveResult.Failed(error)
                        }
                    }
                }
            }
        }

        context("when asked not to push") {
            it("TC-9: reports NotPushed") {
                sut().savePosition(randomReadingPosition(), push = false) shouldBe PositionSaveResult.NotPushed
            }

            it("TC-9: sends no request") {
                withMockServer { server ->
                    sut(server).savePosition(randomReadingPosition(), push = false)

                    server.requestCount shouldBe 0
                }
            }
        }

        context("when a push is in flight") {
            it("TC-14: holds a later save back until it ends") {
                withMockServer { server ->
                    server.enqueue(reply(200))
                    server.enqueue(reply(200))
                    val store = TestReadingPositionStore()
                    val repository = sut(server, store, pausedDispatcher())
                    val first = randomReadingPosition()
                    val callers = CoroutineScope(Dispatchers.Unconfined)

                    callers.launch { repository.savePosition(first, push = true) }
                    callers.launch { repository.savePosition(randomReadingPosition(), push = true) }

                    store.saves shouldBe listOf(first)
                }
            }

            it("TC-14: pushes the saves in call order") {
                withMockServer { server ->
                    server.enqueue(reply(200))
                    server.enqueue(reply(200))
                    val io = pausedDispatcher()
                    val repository = sut(server, ioDispatcher = io)
                    val callers = CoroutineScope(Dispatchers.Unconfined)

                    callers.launch {
                        repository.savePosition(randomReadingPosition().copy(cfi = "epubcfi(/6/4)"), push = true)
                    }
                    callers.launch {
                        repository.savePosition(randomReadingPosition().copy(cfi = "epubcfi(/6/8)"), push = true)
                    }
                    io.scheduler.runCurrent()

                    List(2) {
                        Json.parseToJsonElement(server.nextRequest().body!!.utf8()).jsonObject["epub_cfi"]!!.jsonPrimitive.content
                    } shouldBe listOf("epubcfi(/6/4)", "epubcfi(/6/8)")
                }
            }
        }
    }

    describe("reading the server's position") {

        it("TC-47: asks for the epub progress, with the uuid as one encoded segment") {
            withMockServer { server ->
                server.enqueue(reply(200, "null"))

                sut(server).getServerPosition("a b/c")

                val url = server.nextRequest().url
                url.encodedPath shouldBe "/api/progress/a%20b%2Fc"
                url.queryParameter("format") shouldBe "epub"
            }
        }

        it("TC-50: maps an epub record, ignoring fields the app does not read") {
            withMockServer { server ->
                server.enqueue(
                    reply(
                        200,
                        """{"book_uuid":"book-1","records":[{"format":"epub","epub_cfi":"epubcfi(/6/8)","progress_percent":57,
                            "updated_at":1700000500,"client_updated_at":1700000400,"derived_epub_cfi":"epubcfi(/6/8!/4)",
                            "kobo_location":null,"resolved":true,"cross_format":false,"furthest":true,"linked":["mobi"]}]}""",
                    ),
                )

                sut(server).getServerPosition(randomString()) shouldBe ServerPositionResult.Found(
                    ServerReadingPosition(
                        cfi = "epubcfi(/6/8)",
                        percent = 57,
                        derivedCfi = "epubcfi(/6/8!/4)",
                        updatedAt = 1_700_000_500,
                        clientUpdatedAt = 1_700_000_400,
                    ),
                )
            }
        }

        context("when an old server sends no client clock") {
            it("TC-50: orders by the server's clock") {
                withMockServer { server ->
                    server.enqueue(
                        reply(200, """{"records":[{"format":"epub","epub_cfi":"epubcfi(/6/2)","updated_at":1700000600}]}"""),
                    )

                    val found = sut(server).getServerPosition(randomString()) as ServerPositionResult.Found

                    found.position.orderingClock shouldBe 1_700_000_600
                }
            }
        }

        context("when the body has no epub record") {
            it("TC-48: is None") {
                checkAll(
                    Exhaustive.of(
                        "null",
                        """{"book_uuid":"b","records":[]}""",
                        """{"book_uuid":"b","records":[{"format":"audio","progress_percent":10,"updated_at":1}]}""",
                    ),
                ) { body ->
                    withMockServer { server ->
                        server.enqueue(reply(200, body))

                        sut(server).getServerPosition(randomString()) shouldBe ServerPositionResult.None
                    }
                }
            }
        }

        context("when the uuid is blank") {
            it("is None") {
                sut().getServerPosition("  ") shouldBe ServerPositionResult.None
            }
        }

        context("when the read fails") {
            it("TC-53: is Error, for every kind of failure") {
                checkAll(Exhaustive.of(reply(500), dropped(), reply(200, "{not json"))) { answer ->
                    withScriptedServer {
                        script.answer(answer)

                        sut(server).getServerPosition(randomString()) shouldBe ServerPositionResult.Error
                    }
                }
            }
        }

        context("when no server is saved") {
            it("TC-53: is Error") {
                sut().getServerPosition(randomString()) shouldBe ServerPositionResult.Error
            }
        }
    }
})
