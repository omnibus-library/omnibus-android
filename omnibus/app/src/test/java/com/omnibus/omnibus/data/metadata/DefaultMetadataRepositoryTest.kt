package com.omnibus.omnibus.data.metadata

import com.omnibus.omnibus.data.network.droppedConnection
import com.omnibus.omnibus.data.network.nextRequest
import com.omnibus.omnibus.data.network.pausedDispatcher
import com.omnibus.omnibus.data.network.reply
import com.omnibus.omnibus.data.network.testOmnibusHttpClient
import com.omnibus.omnibus.data.network.withMockServer
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomEnum
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer

/** A merged record as the server sends it back from the overrides route. */
private const val MERGED = """{"id":5,"unique_identifier":"u-5","title":"T","genres":["A"],"subjects":["x"]}"""

private fun saved(): MockResponse = reply(200, MERGED)

private fun randomValues(): List<String> = List(3) { randomString() }

private fun MockWebServer.nextBody(): String = nextRequest().body!!.utf8()

class DefaultMetadataRepositoryTest : DescribeSpec({

    fun sut(
        server: MockWebServer? = null,
        ioDispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    ) = DefaultMetadataRepository(
        ioDispatcher = ioDispatcher,
        omnibusHttpClient = testOmnibusHttpClient(server?.url("/")),
        applicationScope = CoroutineScope(Dispatchers.Unconfined),
    )

    /** A caller whose saves start right away, so they queue in the order the test makes them. */
    fun callers() = CoroutineScope(Dispatchers.Unconfined)

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("replacing a chip list") {

        it("TC-6, TC-7, TC-8: sends only that list's key, for every kind, empty or not") {
            checkAll(
                Exhaustive.of(
                    Triple(MetadataListKind.Genres, listOf("A", "B"), """{"genres":["A","B"]}"""),
                    Triple(MetadataListKind.Tags, listOf("x"), """{"subjects":["x"]}"""),
                    Triple(MetadataListKind.Genres, emptyList(), """{"genres":[]}"""),
                    Triple(MetadataListKind.Tags, emptyList(), """{"subjects":[]}"""),
                ),
            ) { (kind, values, body) ->
                withMockServer { server ->
                    server.enqueue(saved())

                    sut(server).replaceList(randomString(), kind, values)

                    server.nextBody() shouldBe body
                }
            }
        }

        it("TC-9: keeps the values verbatim, in order") {
            withMockServer { server ->
                server.enqueue(saved())
                val values = listOf("Say \"hi\"", "back\\slash", "Ünïcødé ✓", "Repeat", "Repeat", " leading space")

                sut(server).replaceList(randomString(), MetadataListKind.Genres, values)

                Json.parseToJsonElement(server.nextBody()).jsonObject.getValue("genres").jsonArray
                    .map { it.jsonPrimitive.content } shouldContainExactly values
            }
        }

        it("TC-10: posts to the book's overrides route, with the uuid as one segment") {
            withMockServer { server ->
                server.enqueue(saved())

                sut(server).replaceList("a/b?c", randomEnum(), randomValues())

                val request = server.nextRequest()
                request.method shouldBe "POST"
                request.url.encodedPath shouldBe "/api/ebooks/a%2Fb%3Fc/overrides"
            }
        }

        it("TC-11: gives the merged record the server sent back") {
            withMockServer { server ->
                server.enqueue(saved())

                val book = sut(server).replaceList(randomString(), randomEnum(), randomValues())
                    .shouldBeTypeOf<MetadataWriteResult.Saved>().book

                book.uuid shouldBe "u-5"
                book.genres shouldBe listOf("A")
                book.subjects shouldBe listOf("x")
            }
        }

        context("when the server does not accept it") {
            it("TC-12, TC-13, TC-14: gives the failure for every kind of answer") {
                checkAll(
                    Exhaustive.of(
                        reply(401) to MetadataError.SessionExpired,
                        reply(403) to MetadataError.Forbidden,
                        reply(404) to MetadataError.NotFound,
                        reply(400, "Too many genres") to MetadataError.Http(400, "Too many genres"),
                        reply(500) to MetadataError.Http(500, null),
                        reply(200, "{\"nope\":1}") to MetadataError.UnexpectedResponse,
                    ),
                ) { (answer, expected) ->
                    withMockServer { server ->
                        server.enqueue(answer)

                        sut(server).replaceList(randomString(), randomEnum(), randomValues()) shouldBe
                            MetadataWriteResult.Failure(expected)
                    }
                }
            }
        }

        context("when the connection drops") {
            it("TC-15: gives Unreachable") {
                withMockServer { server ->
                    // Two drops, in case the HTTP client retries the POST once on a dropped connection.
                    server.enqueue(droppedConnection())
                    server.enqueue(droppedConnection())

                    sut(server).replaceList(randomString(), randomEnum(), randomValues()) shouldBe
                        MetadataWriteResult.Failure(MetadataError.Unreachable)
                }
            }
        }

        context("when no server is saved") {
            it("TC-16: gives NoServer") {
                sut().replaceList(randomString(), randomEnum(), randomValues()) shouldBe MetadataWriteResult.NoServer
            }
        }

        context("when the uuid is blank") {
            it("TC-16: gives NotFound") {
                withMockServer { server ->
                    sut(server).replaceList("  ", randomEnum(), randomValues()) shouldBe
                        MetadataWriteResult.Failure(MetadataError.NotFound)
                }
            }

            it("TC-16: sends no request") {
                withMockServer { server ->
                    sut(server).replaceList("  ", randomEnum(), randomValues())

                    server.requestCount shouldBe 0
                }
            }
        }
    }

    describe("saving several lists in a row") {

        context("when a save is in flight") {
            it("TC-19: sends the next save behind it, in call order") {
                withMockServer { server ->
                    server.enqueue(saved())
                    server.enqueue(saved())
                    val io = pausedDispatcher()
                    val repository = sut(server, io)
                    val uuid = randomString()

                    val first = callers().async { repository.replaceList(uuid, MetadataListKind.Genres, listOf("A1")) }
                    val second = callers().async { repository.replaceList(uuid, MetadataListKind.Genres, listOf("B")) }
                    io.scheduler.runCurrent()
                    first.await()
                    second.await()

                    listOf(server.nextBody(), server.nextBody()) shouldBe
                        listOf("""{"genres":["A1"]}""", """{"genres":["B"]}""")
                }
            }

            context("when several more are queued behind it") {
                it("TC-20: sends only the newest") {
                    withMockServer { server ->
                        server.enqueue(saved())
                        server.enqueue(saved())
                        val io = pausedDispatcher()
                        val repository = sut(server, io)
                        val uuid = randomString()

                        listOf(listOf("A"), listOf("A", "B"), listOf("A", "B", "C")).forEach { values ->
                            callers().async { repository.replaceList(uuid, MetadataListKind.Genres, values) }
                        }
                        io.scheduler.runCurrent()

                        listOf(server.nextBody(), server.nextBody()) shouldBe
                            listOf("""{"genres":["A"]}""", """{"genres":["A","B","C"]}""")
                    }
                }

                it("TC-20: answers the replaced ones Superseded") {
                    withMockServer { server ->
                        server.enqueue(saved())
                        server.enqueue(saved())
                        val io = pausedDispatcher()
                        val repository = sut(server, io)
                        val uuid = randomString()

                        callers().async { repository.replaceList(uuid, MetadataListKind.Genres, randomValues()) }
                        val replaced = callers().async {
                            repository.replaceList(uuid, MetadataListKind.Genres, randomValues())
                        }
                        callers().async { repository.replaceList(uuid, MetadataListKind.Genres, randomValues()) }
                        io.scheduler.runCurrent()

                        replaced.await() shouldBe MetadataWriteResult.Superseded
                    }
                }
            }

            context("when it fails") {
                it("TC-21: answers its own failure, not Superseded") {
                    withMockServer { server ->
                        server.enqueue(reply(500, "down"))
                        server.enqueue(saved())
                        val io = pausedDispatcher()
                        val repository = sut(server, io)
                        val uuid = randomString()

                        val failing = callers().async {
                            repository.replaceList(uuid, MetadataListKind.Genres, randomValues())
                        }
                        callers().async { repository.replaceList(uuid, MetadataListKind.Genres, randomValues()) }
                        io.scheduler.runCurrent()

                        failing.await() shouldBe MetadataWriteResult.Failure(MetadataError.Http(500, "down"))
                    }
                }

                it("TC-25: still sends the next save") {
                    withMockServer { server ->
                        server.enqueue(reply(500, "down"))
                        server.enqueue(saved())
                        val io = pausedDispatcher()
                        val repository = sut(server, io)
                        val uuid = randomString()

                        callers().async { repository.replaceList(uuid, MetadataListKind.Genres, randomValues()) }
                        val next = callers().async {
                            repository.replaceList(uuid, MetadataListKind.Genres, randomValues())
                        }
                        io.scheduler.runCurrent()

                        next.await().shouldBeTypeOf<MetadataWriteResult.Saved>()
                    }
                }
            }

            context("when its caller is cancelled") {
                it("TC-24: still sends it") {
                    withMockServer { server ->
                        server.enqueue(saved())
                        val io = pausedDispatcher()
                        val repository = sut(server, io)

                        val save = callers().async {
                            repository.replaceList(randomString(), randomEnum(), randomValues())
                        }
                        save.cancel()
                        io.scheduler.runCurrent()

                        server.requestCount shouldBe 1
                    }
                }
            }
        }

        // A save for another key queued between two saves for one key would be Superseded if they shared a queue.
        context("when the queued saves are for another list of the book") {
            it("TC-22: does not supersede them") {
                withMockServer { server ->
                    repeat(3) { server.enqueue(saved()) }
                    val io = pausedDispatcher()
                    val repository = sut(server, io)
                    val uuid = randomString()

                    callers().async { repository.replaceList(uuid, MetadataListKind.Genres, randomValues()) }
                    val tags = callers().async { repository.replaceList(uuid, MetadataListKind.Tags, randomValues()) }
                    callers().async { repository.replaceList(uuid, MetadataListKind.Genres, randomValues()) }
                    io.scheduler.runCurrent()

                    tags.await().shouldBeTypeOf<MetadataWriteResult.Saved>()
                }
            }
        }

        context("when the queued saves are for another book") {
            it("TC-23: does not supersede them") {
                withMockServer { server ->
                    repeat(3) { server.enqueue(saved()) }
                    val io = pausedDispatcher()
                    val repository = sut(server, io)
                    val uuid = randomString()

                    callers().async { repository.replaceList(uuid, MetadataListKind.Genres, randomValues()) }
                    val otherBook = callers().async {
                        repository.replaceList(uuid + "-other", MetadataListKind.Genres, randomValues())
                    }
                    callers().async { repository.replaceList(uuid, MetadataListKind.Genres, randomValues()) }
                    io.scheduler.runCurrent()

                    otherBook.await().shouldBeTypeOf<MetadataWriteResult.Saved>()
                }
            }
        }
    }

    describe("reading the vocabulary") {

        it("TC-17: reads each kind from its route") {
            checkAll(
                Exhaustive.of(MetadataListKind.Genres to "/api/genres", MetadataListKind.Tags to "/api/tags"),
            ) { (kind, path) ->
                withMockServer { server ->
                    server.enqueue(reply(200, "[]"))

                    sut(server).getVocabulary(kind)

                    server.nextRequest().url.encodedPath shouldBe path
                }
            }
        }

        it("TC-17: keeps the server's order, with a missing count as 0") {
            withMockServer { server ->
                server.enqueue(reply(200, "[{\"name\":\"Noir\",\"count\":3},{\"name\":\"Epic\"}]"))

                sut(server).getVocabulary(randomEnum()) shouldBe VocabularyResult.Success(
                    listOf(VocabularyEntry("Noir", 3), VocabularyEntry("Epic", 0)),
                )
            }
        }

        context("when the read fails") {
            it("TC-18: gives the failure for every kind of answer") {
                checkAll(
                    Exhaustive.of(
                        reply(403) to MetadataError.Forbidden,
                        reply(500, "boom") to MetadataError.Http(500, "boom"),
                        reply(200, "{\"not\":\"a list\"}") to MetadataError.UnexpectedResponse,
                    ),
                ) { (answer, expected) ->
                    withMockServer { server ->
                        server.enqueue(answer)

                        sut(server).getVocabulary(randomEnum()) shouldBe VocabularyResult.Failure(expected)
                    }
                }
            }
        }

        context("when no server is saved") {
            it("TC-18: gives NoServer") {
                sut().getVocabulary(randomEnum()) shouldBe VocabularyResult.NoServer
            }
        }
    }
})
