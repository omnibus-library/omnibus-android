package com.omnibus.omnibus.data.highlights

import app.cash.turbine.test
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import mockwebserver3.MockWebServer

/** A bare row as the server writes it. Its mapping is proved by [HighlightDtoTest]. */
private fun row(id: Long): String = """{"id":$id}"""

private fun loaded(vararg ids: Long) =
    HighlightsState.Loaded(ids.map { Highlight(it, null, null, HighlightColor.Amber, null, null, null, null) })

class DefaultHighlightsRepositoryTest : DescribeSpec({

    fun sut(
        server: MockWebServer? = null,
        ioDispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    ) = DefaultHighlightsRepository(
        ioDispatcher = ioDispatcher,
        omnibusHttpClient = testOmnibusHttpClient(server?.url("/")),
        applicationScope = CoroutineScope(Dispatchers.Unconfined),
    )

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
})
