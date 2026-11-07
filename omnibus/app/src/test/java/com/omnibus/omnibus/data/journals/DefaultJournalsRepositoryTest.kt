package com.omnibus.omnibus.data.journals

import app.cash.turbine.test
import com.omnibus.omnibus.data.network.droppedConnection
import com.omnibus.omnibus.data.network.nextRequest
import com.omnibus.omnibus.data.network.pausedDispatcher
import com.omnibus.omnibus.data.network.reply
import com.omnibus.omnibus.data.network.testOmnibusHttpClient
import com.omnibus.omnibus.data.network.withMockServer
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.unmockAndroidLog
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

/** A bare row as the server writes it. Its mapping is proved by [JournalEntryDtoTest]. */
private fun row(id: Long): String = """{"id":$id,"author_id":7}"""

private fun loaded(vararg ids: Long) =
    JournalsState.Loaded(ids.map { JournalEntry(it, 7, "", "", false, null) })

class DefaultJournalsRepositoryTest : DescribeSpec({

    fun sut(
        server: MockWebServer? = null,
        ioDispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    ) = DefaultJournalsRepository(
        ioDispatcher = ioDispatcher,
        omnibusHttpClient = testOmnibusHttpClient(server?.url("/")),
        applicationScope = CoroutineScope(Dispatchers.Unconfined),
    )

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("observing a book's journal") {

        it("TC-6: keeps the server's order") {
            withMockServer { server ->
                server.enqueue(reply(200, "[${row(9)},${row(2)},${row(5)}]"))

                sut(server).getJournalsFlow(randomString()).first() shouldBe loaded(9, 2, 5)
            }
        }

        it("TC-6: asks for the book by its encoded uuid") {
            withMockServer { server ->
                server.enqueue(reply(200, "[]"))

                sut(server).getJournalsFlow("book 1/é").first()

                server.nextRequest().url.encodedPath shouldBe "/api/journals/book/book%201%2F%C3%A9"
            }
        }

        context("when nothing has loaded") {
            it("TC-7: shows Loading until the fetch answers") {
                withMockServer { server ->
                    server.enqueue(reply(200, "[${row(1)}]"))
                    val io = pausedDispatcher()

                    sut(server, io).getJournalsFlow(randomString()).test {
                        awaitItem() shouldBe JournalsState.Loading

                        io.scheduler.runCurrent()

                        awaitItem() shouldBe loaded(1)
                    }
                }
            }
        }

        context("when a list has loaded") {
            it("TC-7: answers a later collector without a request") {
                withMockServer { server ->
                    server.enqueue(reply(200, "[${row(1)}]"))
                    val repository = sut(server)
                    val uuid = randomString()
                    repository.getJournalsFlow(uuid).first()

                    repository.getJournalsFlow(uuid).first() shouldBe loaded(1)

                    server.requestCount shouldBe 1
                }
            }
        }

        context("when a fetch is in flight") {
            it("TC-8: shares one request among all its callers") {
                withMockServer { server ->
                    server.enqueue(reply(200, "[${row(1)}]"))
                    val io = pausedDispatcher()
                    val repository = sut(server, io)
                    val uuid = randomString()

                    coroutineScope {
                        val collectors = List(2) {
                            async(start = CoroutineStart.UNDISPATCHED) {
                                repository.getJournalsFlow(uuid).first { it is JournalsState.Loaded }
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
            it("TC-9: is a loaded empty list") {
                withMockServer { server ->
                    server.enqueue(reply(200, "[]"))

                    sut(server).getJournalsFlow(randomString()).first() shouldBe loaded()
                }
            }
        }

        context("when the read fails") {
            it("TC-10, TC-11: reads as Failed for every kind of failure") {
                checkAll(
                    Exhaustive.of(
                        reply(500),
                        reply(200, "{not json"),
                        reply(200, "{}"),
                        reply(200, """[{"id":"abc","author_id":7}]"""),
                        reply(200, """[{"author_id":7}]"""),
                        reply(200, """[{"id":1}]"""),
                        droppedConnection(),
                    ),
                ) { answer ->
                    withMockServer { server ->
                        server.enqueue(answer)

                        sut(server).getJournalsFlow(randomString()).first() shouldBe JournalsState.Failed
                    }
                }
            }

            it("TC-16: fetches again for a new collector") {
                withMockServer { server ->
                    server.enqueue(reply(500))
                    server.enqueue(reply(200, "[${row(1)}]"))
                    val repository = sut(server)
                    val uuid = randomString()
                    repository.getJournalsFlow(uuid).first()

                    repository.getJournalsFlow(uuid).first() shouldBe loaded(1)
                }
            }
        }

        context("when no server is saved") {
            it("TC-12: reads as Failed") {
                sut().getJournalsFlow(randomString()).first() shouldBe JournalsState.Failed
            }
        }

        context("when the uuid is blank") {
            it("TC-12: reads as Failed") {
                withMockServer { server ->
                    checkAll(Exhaustive.of("", "   ")) { uuid ->
                        sut(server).getJournalsFlow(uuid).first() shouldBe JournalsState.Failed
                    }
                }
            }

            it("TC-12: sends no request") {
                withMockServer { server ->
                    checkAll(Exhaustive.of("", "   ")) { uuid ->
                        sut(server).getJournalsFlow(uuid).first()
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

                    repository.getJournalsFlow("A").test {
                        awaitItem() shouldBe JournalsState.Failed
                        server.enqueue(reply(200, "[${row(1)}]"))

                        repository.getJournalsFlow("B").first()

                        expectNoEvents()
                    }
                }
            }
        }
    }

    describe("refreshing a book's journal") {

        context("when a list has loaded") {
            context("when the refresh fails") {
                it("TC-13: emits nothing, leaving the list") {
                    withMockServer { server ->
                        server.enqueue(reply(200, "[${row(1)}]"))
                        val repository = sut(server)
                        val uuid = randomString()

                        repository.getJournalsFlow(uuid).test {
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

                        repository.getJournalsFlow(uuid).test {
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

                    repository.getJournalsFlow(uuid).test {
                        awaitItem() shouldBe JournalsState.Loading
                        io.scheduler.runCurrent()
                        awaitItem() shouldBe JournalsState.Failed

                        val refresh = CoroutineScope(Dispatchers.Unconfined).async { repository.refresh(uuid) }

                        awaitItem() shouldBe JournalsState.Loading
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

                    repository.getJournalsFlow(uuid).first() shouldBe loaded(1)
                }
            }
        }
    }
})
