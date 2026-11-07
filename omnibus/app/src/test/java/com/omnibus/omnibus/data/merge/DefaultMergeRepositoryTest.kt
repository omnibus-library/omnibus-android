package com.omnibus.omnibus.data.merge

import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.nextRequest
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.reply
import com.omnibus.omnibus.testing.testOmnibusHttpClient
import com.omnibus.omnibus.testing.unmockAndroidLog
import com.omnibus.omnibus.testing.withMockServer
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import mockwebserver3.MockWebServer

class DefaultMergeRepositoryTest : DescribeSpec({

    fun sut(server: MockWebServer? = null) = DefaultMergeRepository(
        ioDispatcher = Dispatchers.Unconfined,
        omnibusHttpClient = testOmnibusHttpClient(server?.url("/")),
    )

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("searching for merge candidates") {

        it("#120 TC-13: answers the matching books") {
            withMockServer { server ->
                server.enqueue(
                    reply(
                        200,
                        """[{"id":1,"unique_identifier":"u-1","title":"Dune","future_key":{"nested":true}},
                            {"id":2,"unique_identifier":"u-2","title":"Dune Messiah"}]""",
                    ),
                )

                val books = sut(server).searchCandidates(randomString())
                    .shouldBeTypeOf<MergeCandidatesResult.Success>().books

                books.map { it.displayTitle } shouldBe listOf("Dune", "Dune Messiah")
            }
        }

        it("#120 TC-13: asks the candidates route") {
            withMockServer { server ->
                server.enqueue(reply(200, "[]"))

                sut(server).searchCandidates(randomString())

                server.nextRequest().url.encodedPath shouldBe "/api/books/merge/candidates"
            }
        }

        context("when the query has reserved characters") {
            it("#120 TC-14: sends it as one q parameter holding the input") {
                withMockServer { server ->
                    server.enqueue(reply(200, "[]"))
                    val query = "a b&c=d é/#"

                    sut(server).searchCandidates(query)

                    val url = server.nextRequest().url
                    url.queryParameterNames shouldBe setOf("q")
                    url.queryParameterValues("q") shouldBe listOf(query)
                }
            }
        }

        context("when the query is blank") {
            it("#120 TC-15: answers an empty list") {
                checkAll(Exhaustive.of("", "   ")) { query ->
                    withMockServer { server ->
                        sut(server).searchCandidates(query) shouldBe MergeCandidatesResult.Success(emptyList())
                    }
                }
            }

            it("#120 TC-15: sends no request") {
                withMockServer { server ->
                    sut(server).searchCandidates("   ")

                    server.requestCount shouldBe 0
                }
            }
        }

        context("when a row has no usable uuid") {
            it("#120 TC-15: drops the row") {
                withMockServer { server ->
                    server.enqueue(
                        reply(200, """[{"id":1},{"id":2,"unique_identifier":""},{"id":3,"unique_identifier":"u-3"}]"""),
                    )

                    val books = sut(server).searchCandidates(randomString())
                        .shouldBeTypeOf<MergeCandidatesResult.Success>().books

                    books.map { it.uniqueIdentifier } shouldBe listOf("u-3")
                }
            }
        }

        context("when the server answers an error") {
            it("#120 TC-16: gives the failure") {
                withMockServer { server ->
                    server.enqueue(reply(400, "query too long"))

                    sut(server).searchCandidates(randomString()) shouldBe
                        MergeCandidatesResult.Failure(MergeError.Http(400, "query too long"))
                }
            }
        }

        context("when no server is saved") {
            it("#120 TC-20: gives NoServer") {
                sut().searchCandidates(randomString()) shouldBe MergeCandidatesResult.NoServer
            }
        }
    }

    describe("merging two books") {

        it("#120 TC-17: gives the server's receipt") {
            withMockServer { server ->
                server.enqueue(reply(200, """{"merge_log_id":42,"target_uuid":"tgt"}"""))

                sut(server).mergeBooks(randomString(), randomString()) shouldBe
                    MergeBooksResult.Success(MergeReceipt(42, "tgt"))
            }
        }

        it("#120 TC-17: posts both uuids to the merge route") {
            withMockServer { server ->
                server.enqueue(reply(200, """{"merge_log_id":42,"target_uuid":"tgt"}"""))

                sut(server).mergeBooks("src", "tgt")

                val request = server.nextRequest()
                request.method shouldBe "POST"
                request.url.encodedPath shouldBe "/api/books/merge"
                Json.parseToJsonElement(request.body!!.utf8()) shouldBe
                    Json.parseToJsonElement("""{"source_uuid":"src","target_uuid":"tgt"}""")
            }
        }

        context("when the server does not accept it") {
            it("#120 TC-18: gives the failure for every kind of answer") {
                checkAll(
                    Exhaustive.of(
                        reply(401, "nope") to MergeError.SessionExpired,
                        reply(403, "nope") to MergeError.Forbidden,
                        reply(404, "nope") to MergeError.NotFound,
                        reply(409, """{"error":"x"}""") to MergeError.Conflict("x"),
                        reply(422, """{"error":"same"}""") to MergeError.Http(422, "same"),
                        reply(500, "boom") to MergeError.Http(500, "boom"),
                        reply(200, """{"target_uuid":"tgt"}""") to MergeError.UnexpectedResponse,
                    ),
                ) { (answer, expected) ->
                    withMockServer { server ->
                        server.enqueue(answer)

                        sut(server).mergeBooks(randomString(), randomString()) shouldBe MergeBooksResult.Failure(expected)
                    }
                }
            }
        }

        context("when the server is unreachable") {
            it("#120 TC-18: gives Unreachable") {
                withMockServer { server ->
                    val repository = sut(server)
                    server.close()

                    repository.mergeBooks(randomString(), randomString()) shouldBe
                        MergeBooksResult.Failure(MergeError.Unreachable)
                }
            }
        }

        context("when a uuid is blank") {
            it("#120 TC-18: gives NotFound") {
                checkAll(Exhaustive.of("" to "tgt", "src" to " ")) { (source, target) ->
                    withMockServer { server ->
                        sut(server).mergeBooks(source, target) shouldBe MergeBooksResult.Failure(MergeError.NotFound)
                    }
                }
            }

            it("#120 TC-18: sends no request") {
                withMockServer { server ->
                    sut(server).mergeBooks("", "tgt")
                    sut(server).mergeBooks("src", " ")

                    server.requestCount shouldBe 0
                }
            }
        }

        context("when no server is saved") {
            it("#120 TC-20: gives NoServer") {
                sut().mergeBooks(randomString(), randomString()) shouldBe MergeBooksResult.NoServer
            }
        }
    }

    describe("undoing a merge") {

        it("#120 TC-19: gives the restored book's uuid") {
            withMockServer { server ->
                server.enqueue(reply(200, """{"restored_uuid":"src"}"""))

                sut(server).undoMerge(randomLong(1L..1_000L)) shouldBe UndoMergeResult.Success("src")
            }
        }

        it("#120 TC-19: posts the log id as a JSON number to the undo route") {
            withMockServer { server ->
                server.enqueue(reply(200, """{"restored_uuid":"src"}"""))

                sut(server).undoMerge(42)

                val request = server.nextRequest()
                request.method shouldBe "POST"
                request.url.encodedPath shouldBe "/api/books/merge/undo"
                Json.parseToJsonElement(request.body!!.utf8()) shouldBe Json.parseToJsonElement("""{"merge_log_id":42}""")
            }
        }

        context("when a later change blocks the undo") {
            it("#120 TC-19: gives Conflict with the server's message") {
                withMockServer { server ->
                    server.enqueue(reply(409, """{"error":"later merge"}"""))

                    sut(server).undoMerge(randomLong(1L..1_000L)) shouldBe
                        UndoMergeResult.Failure(MergeError.Conflict("later merge"))
                }
            }
        }

        context("when the log id is below 1") {
            it("#120 TC-19: gives NotFound") {
                checkAll(Exhaustive.of(0L, -1L)) { mergeLogId ->
                    withMockServer { server ->
                        sut(server).undoMerge(mergeLogId) shouldBe UndoMergeResult.Failure(MergeError.NotFound)
                    }
                }
            }

            it("#120 TC-19: sends no request") {
                withMockServer { server ->
                    sut(server).undoMerge(0)

                    server.requestCount shouldBe 0
                }
            }
        }

        context("when no server is saved") {
            it("#120 TC-20: gives NoServer") {
                sut().undoMerge(randomLong(1L..1_000L)) shouldBe UndoMergeResult.NoServer
            }
        }
    }
})
