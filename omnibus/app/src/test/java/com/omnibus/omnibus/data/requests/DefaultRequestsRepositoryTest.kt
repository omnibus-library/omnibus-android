package com.omnibus.omnibus.data.requests

import com.omnibus.omnibus.data.cover.TestCoverRepository
import com.omnibus.omnibus.testing.ScriptedServer
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.reply
import com.omnibus.omnibus.testing.testOmnibusHttpClient
import com.omnibus.omnibus.testing.unmockAndroidLog
import com.omnibus.omnibus.testing.unreachableUrl
import com.omnibus.omnibus.testing.withScriptedServer
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl

/** Runs [block] against a scripted server whose one answer is [code] with [body]. */
private inline fun withScriptedServerAnswering(code: Int, body: String, block: ScriptedServer.() -> Unit) =
    withScriptedServer {
        script.answer(code, body)
        block()
    }

private const val NULL_BOOK = """{"id":5,"unique_identifier":"u-1","filename":"a.epub","title":"Dune"}"""

/** A request as the server writes it, with every nullable field null and no versions key. */
private fun bareRequest(id: Long = 7, state: String = "looking", kind: String = "ebook", extra: String = ""): String =
    """{"id":$id,"book_uuid":"u-1","user_id":3,"kind":"$kind","state":"$state","stuck_reason":null,
        "stuck_detail":null,"chosen_result_id":null,"created_at":100,"updated_at":200,"progress":null,
        "expected_done_at":null,"cancel_at":null,"cancelled_by":null,"note":null,"cover_offer":null$extra}"""

private val FULL_REQUEST = """
    {"id":9,"book_uuid":"u-2","user_id":4,"kind":"audiobook","state":"on_its_way","stuck_reason":"stopped_moving",
     "stuck_detail":"qBittorrent said no","chosen_result_id":12,"created_at":1000,"updated_at":2000,"progress":0.62,
     "expected_done_at":3000,"cancel_at":2500,"cancelled_by":8,"note":"Three narrators",
     "cover_offer":{"width":600,"height":900,"current_width":128,"current_height":196},
     "versions":[{"id":12,"title":"Dune.m4b","size_bytes":123456,"format":"M4B","indexer":"MAM","seeders":142,
                  "protocol":"torrent","info_url":"https://x.test/1","best_match":true,"rejected":false},
                 {"id":13,"title":"Dune.nzb","size_bytes":null,"format":null,"indexer":null,"seeders":null,
                  "protocol":"usenet","info_url":null,"best_match":false,"rejected":true}]}
"""

private fun listing(request: String, requester: String = """"requester_name":"Jordan","requester_has_avatar":true,"cancelled_by_name":null"""): String =
    """{"request":$request,"book":$NULL_BOOK,$requester}"""

class DefaultRequestsRepositoryTest : DescribeSpec({

    fun sut(
        baseUrl: HttpUrl? = null,
        covers: TestCoverRepository = TestCoverRepository(),
    ) = DefaultRequestsRepository(Dispatchers.Unconfined, testOmnibusHttpClient(baseUrl), covers)

    fun ScriptedServer.repository(covers: TestCoverRepository = TestCoverRepository()) = sut(url, covers)

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("reading requests") {

        it("GETs api/requests for the list") {
            withScriptedServerAnswering(200, "[]") {
                repository().list()

                nextRequest().let { it.method to it.url.encodedPath } shouldBe ("GET" to "/api/requests")
            }
        }

        it("GETs a book's requests with the uuid as one path segment") {
            withScriptedServerAnswering(200, "[]") {
                repository().bookList("a/b c")

                nextRequest().url.encodedPath shouldBe "/api/books/a%2Fb%20c/requests"
            }
        }

        it("GETs one request by id") {
            withScriptedServerAnswering(200, bareRequest(id = 41)) {
                val result = repository().request(41)

                (result as RequestsResult.Success).value.id shouldBe 41
                nextRequest().url.encodedPath shouldBe "/api/requests/41"
            }
        }

        context("when every nullable field is null") {
            it("decodes the listing, with no versions") {
                withScriptedServerAnswering(200, "[${listing(bareRequest())}]") {
                    val listed = (repository().list() as RequestsResult.Success).value.single()

                    listed.request.run {
                        listOf(stuckReason, stuckDetail, chosenResultId, progress, expectedDoneAt, cancelAt, cancelledBy, note, coverOffer)
                            .all { it == null } shouldBe true
                        versions shouldBe emptyList()
                    }
                    listed.book.uuid shouldBe "u-1"
                }
            }
        }

        context("when every field is set") {
            it("decodes the request, its versions and its cover offer") {
                withScriptedServerAnswering(200, FULL_REQUEST) {
                    val request = (repository().request(9) as RequestsResult.Success).value

                    request shouldBe BookRequest(
                        id = 9, bookUuid = "u-2", userId = 4, kind = RequestKind.Audiobook, state = RequestState.OnItsWay,
                        stuckReason = StuckReason.StoppedMoving, stuckDetail = "qBittorrent said no", chosenResultId = 12,
                        createdAt = 1000, updatedAt = 2000, progress = 0.62, expectedDoneAt = 3000, cancelAt = 2500,
                        cancelledBy = 8, note = "Three narrators",
                        coverOffer = CoverOffer(600, 900, 128, 196),
                        versions = listOf(
                            RequestVersion(12, "Dune.m4b", 123456, "M4B", "MAM", 142, ReleaseProtocol.Torrent, "https://x.test/1", true, false),
                            RequestVersion(13, "Dune.nzb", null, null, null, null, ReleaseProtocol.Usenet, null, false, true),
                        ),
                    )
                }
            }
        }

        context("when the server sends a value this app doesn't know") {
            it("fails with an unexpected response for a state, a stuck reason, a kind and a protocol") {
                withScriptedServer {
                    checkAll(
                        Exhaustive.of(
                            bareRequest(state = "paused"),
                            bareRequest(extra = "").replace(""""stuck_reason":null""", """"stuck_reason":"gremlins""""),
                            bareRequest(kind = "comic"),
                            bareRequest(extra = ""","versions":[{"id":1,"title":"t","protocol":"carrier_pigeon"}]"""),
                        ),
                    ) { body ->
                        script.answer("GET", reply(200, body))

                        repository().request(7) shouldBe RequestsResult.Failure(RequestsError.UnexpectedResponse)
                    }
                }
            }
        }

        describe("a book's wishers") {
            it("answers the wanted_by of the row whose book is the one asked for") {
                val body = """[{"book":$NULL_BOOK,"added_at":1,"wanted_by":[{"id":3,"name":"Jordan"},{"id":4,"name":"Riley"}],"holdings":[]},
                    {"book":{"id":6,"unique_identifier":"u-9","filename":"b","title":"Other"},"added_at":1,"wanted_by":[{"id":5,"name":"Sam"}],"holdings":[]}]"""
                withScriptedServerAnswering(200, body) {
                    val result = repository().wanters("u-1")

                    result shouldBe RequestsResult.Success(listOf(Wanter(3, "Jordan"), Wanter(4, "Riley")))
                    nextRequest().url.toString().substringAfter("/api/") shouldBe "wishlists?user=all"
                }
            }

            it("answers empty when no row matches") {
                withScriptedServerAnswering(200, "[]") {
                    repository().wanters("u-1") shouldBe RequestsResult.Success(emptyList<Wanter>())
                }
            }
        }
    }

    describe("starting a request") {

        it("POSTs the kind to the book's requests route") {
            withScriptedServerAnswering(201, bareRequest()) {
                repository().start("u-1", RequestKind.Audiobook)

                nextRequest().let { Triple(it.method, it.url.encodedPath, it.body!!.utf8()) } shouldBe
                    Triple("POST", "/api/books/u-1/requests", """{"kind":"audiobook"}""")
            }
        }

        it("succeeds for a request already going (200) and a new one (201)") {
            withScriptedServer {
                checkAll(Exhaustive.of(200, 201)) { code ->
                    script.answer("POST", reply(code, bareRequest(id = 11)))

                    repository().start("u-1", RequestKind.Ebook).shouldBeInstanceOf<RequestsResult.Success<BookRequest>>()
                }
            }
        }

        it("POSTs a provider's record back untouched, unknown keys kept, as an ebook request") {
            val record = Json.parseToJsonElement(
                """{"isbn13":"9780441172719","title":"Dune","authors":["Frank Herbert"],"format":"Paperback","novel":{"x":[1,2]},"source":"hardcover"}""",
            ) as JsonObject
            val book = randomProviderBook().copy(meta = record)
            withScriptedServerAnswering(201, bareRequest()) {
                repository().startFromRecord(book)

                nextRequest().let { Triple(it.method, it.url.encodedPath, Json.parseToJsonElement(it.body!!.utf8())) } shouldBe
                    Triple("POST", "/api/requests", Json.parseToJsonElement("""{"kind":"ebook","meta":$record}"""))
            }
        }
    }

    describe("moving a request") {

        it("POSTs exactly the version id to choose") {
            withScriptedServerAnswering(200, bareRequest(id = 5)) {
                repository().choose(5, 77)

                nextRequest().let { Triple(it.method, it.url.encodedPath, it.body!!.utf8()) } shouldBe
                    Triple("POST", "/api/requests/5/choose", """{"result_id":77}""")
            }
        }

        it("sends each command to its own route") {
            withScriptedServer {
                checkAll(
                    Exhaustive.of<Pair<String, suspend DefaultRequestsRepository.() -> Any>>(
                        "POST /api/requests/5/swap" to { swap(5) },
                        "POST /api/requests/5/retry" to { retry(5) },
                        "POST /api/requests/5/cancel" to { cancel(5) },
                        "DELETE /api/requests/5/cancel" to { undoCancel(5) },
                        "POST /api/requests/5/cover" to { takeCover(5) },
                        "DELETE /api/requests/5/cover" to { keepCover(5) },
                    ),
                ) { (expected, call) ->
                    script.answer(200, bareRequest(id = 5))

                    repository().call()

                    nextRequest().let { "${it.method} ${it.url.encodedPath}" } shouldBe expected
                }
            }
        }

        it("answers the request the server returns") {
            withScriptedServerAnswering(200, bareRequest(id = 5, state = "cancelled")) {
                (repository().cancel(5) as RequestsResult.Success).value.state shouldBe RequestState.Cancelled
            }
        }

        it("drops the book's cached cover when the downloaded one is taken") {
            val covers = TestCoverRepository()
            withScriptedServerAnswering(200, bareRequest()) {
                repository(covers).takeCover(7)

                covers.invalidated shouldBe listOf("u-1")
            }
        }

        it("keeps the cached cover when taking it fails") {
            val covers = TestCoverRepository()
            withScriptedServerAnswering(500, "boom") {
                repository(covers).takeCover(7)

                covers.invalidated shouldBe emptyList()
            }
        }
    }

    describe("searching") {

        it("GETs the palette with the query") {
            val body = """{"books":[{"id":1,"uuid":"u-1","title":"Dune","author_display":"Frank Herbert","year":"1965",
                "formats":["EPUB"],"cover_url":"/c","accent":"#fff","has_physical":false}]}"""
            withScriptedServerAnswering(200, body) {
                val hits = (repository().searchLibrary("du ne") as RequestsResult.Success).value

                hits shouldBe listOf(LibraryHit("u-1", "Dune", "Frank Herbert", "1965", listOf("EPUB"), "/c", "#fff"))
                nextRequest().url.queryParameter("q") shouldBe "du ne"
            }
        }

        it("POSTs the query to the provider search and keeps each record whole") {
            val body = """{"results":[{"isbn13":"1","title":"Dune","authors":["Frank Herbert"],"year":"1965",
                "cover_url":null,"source":"hardcover","extra":true}]}"""
            withScriptedServerAnswering(200, body) {
                val found = (repository().searchProviders("dune") as RequestsResult.Success).value.single()

                found.title to found.authors shouldBe ("Dune" to listOf("Frank Herbert"))
                found.meta["extra"] shouldBe JsonPrimitive(true)
                nextRequest().let { Triple(it.method, it.url.encodedPath, it.body!!.utf8()) } shouldBe
                    Triple("POST", "/api/scan/search", """{"query":"dune"}""")
            }
        }

        it("fails with an unexpected response when a provider record has no title") {
            withScriptedServerAnswering(200, """{"results":[{"isbn13":"1"}]}""") {
                repository().searchProviders("x") shouldBe RequestsResult.Failure(RequestsError.UnexpectedResponse)
            }
        }

        describe("resolving a provider record") {
            it("lists the head and the others of a close match, in the server's order") {
                val book = { id: String -> """{"uuid":"$id","title":"T$id","authors":[],"has_physical":false,"has_files":${id == "b"}}""" }
                val body = """{"kind":"close_match","book":${book("a")},"others":[${book("b")},${book("c")}],"scanned":{"isbn13":"1","title":"T","authors":[],"source":"x"}}"""
                withScriptedServerAnswering(200, body) {
                    val match = (repository().resolve(randomProviderBook()) as RequestsResult.Success).value

                    match shouldBe ScanMatch(
                        listOf(MatchedBook("a", "Ta", false), MatchedBook("b", "Tb", true), MatchedBook("c", "Tc", false)),
                        ScanOutcomeKind.CloseMatch,
                    )
                }
            }

            it("lists no library book when the record is not in the library, or the kind is unknown") {
                withScriptedServer {
                    checkAll(
                        Exhaustive.of(
                            """{"kind":"not_in_library","online":{"isbn13":"1","title":"T","authors":[],"source":"x"}}""" to ScanOutcomeKind.NotInLibrary,
                            """{"kind":"unresolved"}""" to ScanOutcomeKind.Unresolved,
                            """{"kind":"from_the_future"}""" to ScanOutcomeKind.Unresolved,
                        ),
                    ) { (body, kind) ->
                        script.answer("POST", reply(200, body))

                        repository().resolve(randomProviderBook()) shouldBe RequestsResult.Success(ScanMatch(emptyList(), kind))
                    }
                }
            }

            it("POSTs the record under meta") {
                val book = randomProviderBook()
                withScriptedServerAnswering(200, """{"kind":"unresolved"}""") {
                    repository().resolve(book)

                    nextRequest().let { it.url.encodedPath to Json.parseToJsonElement(it.body!!.utf8()) } shouldBe
                        ("/api/scan/resolve-meta" to Json.parseToJsonElement("""{"meta":${book.meta}}"""))
                }
            }
        }
    }

    describe("a call that fails") {

        it("answers NoServer when no server is saved") {
            sut(baseUrl = null).list() shouldBe RequestsResult.NoServer
        }

        it("answers Unreachable when the server can't be reached") {
            sut(unreachableUrl()).list() shouldBe RequestsResult.Failure(RequestsError.Unreachable)
        }

        it("answers SessionExpired on a 401") {
            withScriptedServerAnswering(401, "") {
                repository().list() shouldBe RequestsResult.Failure(RequestsError.SessionExpired)
            }
        }

        it("answers the code and the trimmed plain-text body for any other status") {
            withScriptedServerAnswering(429, "  slow down \n") {
                repository().start("u", RequestKind.Ebook) shouldBe RequestsResult.Failure(RequestsError.Http(429, "slow down"))
            }
        }

        it("answers the JSON error text when the body is JSON") {
            withScriptedServerAnswering(403, """{"error":"Not allowed"}""") {
                repository().list() shouldBe RequestsResult.Failure(RequestsError.Http(403, "Not allowed"))
            }
        }

        it("answers no message when the body is blank") {
            withScriptedServerAnswering(500, "  ") {
                (repository().list() as RequestsResult.Failure).error.shouldBeInstanceOf<RequestsError.Http>().message.shouldBeNull()
            }
        }

        it("answers an unexpected response for a 2xx body that does not decode") {
            withScriptedServerAnswering(200, "not json") {
                repository().request(randomLong(1L..9L)) shouldBe RequestsResult.Failure(RequestsError.UnexpectedResponse)
            }
        }
    }
})
