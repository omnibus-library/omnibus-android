package com.omnibus.omnibus.data.library

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.FakeClientInfo
import com.omnibus.omnibus.data.network.FakeAuthTokenProvider
import com.omnibus.omnibus.data.network.FakeServerBaseUrlProvider
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest

class DefaultLibraryRepositoryTest : DescribeSpec({

    lateinit var server: MockWebServer
    lateinit var baseUrlProvider: FakeServerBaseUrlProvider
    lateinit var repository: DefaultLibraryRepository

    /** Paths of every request the shelf dispatcher receives, in arrival order. */
    val requested = ConcurrentLinkedQueue<String>()

    fun enqueue(code: Int, body: String = "", headers: Map<String, String> = emptyMap()) {
        server.enqueue(
            MockResponse.Builder()
                .code(code)
                .body(body)
                .apply { headers.forEach { (name, value) -> addHeader(name, value) } }
                .build(),
        )
    }

    fun booksBody(ids: List<Long>): String =
        """{"books":[${ids.joinToString(",") { """{"id":$it}""" }}]}"""

    /** Serves `/api/shelves` with [SHELVES_JSON] and each `/api/shelves/{id}/page` from [page]. */
    fun serveShelves(page: (Long) -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                requested.add(path)
                val shelfPage = Regex("/api/shelves/(\\d+)/page").matchEntire(path)
                return when {
                    path == "/api/shelves" -> MockResponse.Builder().code(200).body(SHELVES_JSON).build()
                    shelfPage != null -> page(shelfPage.groupValues[1].toLong())
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
    }

    fun shelfPage(ids: List<Long>, delay: Long = 0): MockResponse =
        MockResponse.Builder()
            .code(200)
            .body(booksBody(ids))
            .apply { if (delay > 0) headersDelay(delay, TimeUnit.MILLISECONDS) }
            .build()

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    beforeEach {
        server = MockWebServer().apply { start() }
        requested.clear()
        baseUrlProvider = FakeServerBaseUrlProvider(baseUrl = server.url("/"))

        repository = DefaultLibraryRepository(
            ioDispatcher = Dispatchers.Unconfined,
            omnibusHttpClient = OmnibusHttpClient(
                baseUrlProvider = baseUrlProvider,
                authTokenProvider = FakeAuthTokenProvider(token = "secret"),
                omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
                authClientMetadataInterceptor = AuthClientMetadataInterceptor(FakeClientInfo()),
            ),
        )
    }

    afterEach { server.close() }

    describe("getBooksPageFlow") {

        describe("request parameters") {

            it("TC-1: first page sends limit, sort and dir, and no cursor or filters") {
                enqueue(200, EMPTY_BOOKS)

                repository.getBooksPageFlow().single()

                val request = server.takeRequest()
                request.method shouldBe "GET"
                request.url.encodedPath shouldBe "/api/ebooks"
                request.url.queryParameter("limit") shouldBe "60"
                request.url.queryParameter("sort") shouldBe "newest_added"
                request.url.queryParameter("dir") shouldBe "desc"
                request.url.queryParameter("cursor").shouldBeNull()
                request.url.queryParameter("formats").shouldBeNull()
                request.url.queryParameter("exclude_formats").shouldBeNull()
            }

            it("TC-2: a cursor request still carries sort, dir and limit") {
                enqueue(200, EMPTY_BOOKS)

                repository.getBooksPageFlow(
                    BooksPageQuery(sort = LibrarySort.Title, dir = SortDirection.Asc),
                    cursor = "c1",
                ).single()

                val request = server.takeRequest()
                request.url.queryParameter("cursor") shouldBe "c1"
                request.url.queryParameter("sort") shouldBe "title"
                request.url.queryParameter("dir") shouldBe "asc"
                request.url.queryParameter("limit") shouldBe "60"
            }

            it("TC-3: formats are lowercase and comma-separated, exclude_formats is sorted") {
                enqueue(200, EMPTY_BOOKS)

                repository.getBooksPageFlow(
                    BooksPageQuery(formats = setOf("EPUB", "Pdf"), excludeFormats = setOf("m4b", "epub")),
                ).single()

                val request = server.takeRequest()
                request.url.queryParameter("formats") shouldBe "epub,pdf"
                request.url.queryParameter("exclude_formats") shouldBe "epub,m4b"
            }

            it("TC-3: both filter params are absent when the sets are empty") {
                enqueue(200, EMPTY_BOOKS)

                repository.getBooksPageFlow(BooksPageQuery(formats = emptySet(), excludeFormats = emptySet())).single()

                val request = server.takeRequest()
                request.url.queryParameter("formats").shouldBeNull()
                request.url.queryParameter("exclude_formats").shouldBeNull()
            }

            it("TC-4: every sort and direction is sent as its api name") {
                LibrarySort.entries.forEach { _ -> enqueue(200, EMPTY_BOOKS) }
                SortDirection.entries.forEach { _ -> enqueue(200, EMPTY_BOOKS) }

                LibrarySort.entries.forEach { sort -> repository.getBooksPageFlow(BooksPageQuery(sort = sort)).single() }
                SortDirection.entries.forEach { dir -> repository.getBooksPageFlow(BooksPageQuery(dir = dir)).single() }

                LibrarySort.entries.map { _ -> server.takeRequest().url.queryParameter("sort") } shouldBe
                    LibrarySort.entries.map { it.apiName }
                SortDirection.entries.map { _ -> server.takeRequest().url.queryParameter("dir") } shouldBe
                    SortDirection.entries.map { it.apiName }
            }

            it("TC-4: the api names are the server's values") {
                LibrarySort.entries.map { it.apiName } shouldBe listOf(
                    "title",
                    "author",
                    "series",
                    "recently_interacted",
                    "last_updated",
                    "newest_added",
                )
                SortDirection.entries.map { it.apiName } shouldBe listOf("asc", "desc")
            }

            it("TC-5: sends the bearer token and JSON accept header") {
                enqueue(200, EMPTY_BOOKS)

                repository.getBooksPageFlow().single()

                val request = server.takeRequest()
                request.headers["Authorization"] shouldBe "Bearer secret"
                request.headers["Accept"] shouldBe "application/json"
            }

            it("TC-5: resolves beneath a server path prefix") {
                baseUrlProvider.baseUrl = server.url("/omnibus")
                enqueue(200, EMPTY_BOOKS)

                repository.getBooksPageFlow().single()

                server.takeRequest().url.encodedPath shouldBe "/omnibus/api/ebooks"
            }
        }

        describe("page headers") {

            it("TC-6: carries the next cursor, total and hidden count from the headers") {
                enqueue(
                    200,
                    booksBody(listOf(1)),
                    mapOf("X-Next-Cursor" to "c2", "X-Total-Count" to "123", "X-Hidden-Count" to "4"),
                )

                val page = repository.getBooksPageFlow().single().shouldBeTypeOf<LibraryPageResult.Success>().page

                page.nextCursor shouldBe "c2"
                page.total shouldBe 123L
                page.hiddenCount shouldBe 4
            }

            it("TC-7: a last page without the headers gives null cursor, total and hidden count") {
                enqueue(200, booksBody(listOf(1)))

                val page = repository.getBooksPageFlow().single().shouldBeTypeOf<LibraryPageResult.Success>().page

                page.nextCursor.shouldBeNull()
                page.total.shouldBeNull()
                page.hiddenCount.shouldBeNull()
            }
        }

        describe("body decoding") {

            it("TC-8: decodes a body with every optional key and maps the books") {
                enqueue(200, """{"path":"/x","books":[{"id":1,"title":"A"}],"error":null,"total":5}""")

                val page = repository.getBooksPageFlow().single().shouldBeTypeOf<LibraryPageResult.Success>().page

                page.books.map { it.title } shouldBe listOf("A")
            }

            it("TC-8: decodes a minimal body") {
                enqueue(200, """{"books":[{"id":2}]}""")

                val page = repository.getBooksPageFlow().single().shouldBeTypeOf<LibraryPageResult.Success>().page

                page.books.map { it.id } shouldBe listOf(2L)
            }
        }

        describe("failures") {

            listOf(
                Triple(500, "internal server error", LibraryError.Http(500, "internal server error")),
                Triple(500, "", LibraryError.Http(500, null)),
                Triple(500, "   \n ", LibraryError.Http(500, null)),
                Triple(400, """{"error":"bad cursor"}""", LibraryError.Http(400, "bad cursor")),
                Triple(422, """{"message":"bad sort"}""", LibraryError.Http(422, "bad sort")),
                Triple(404, """{"detail":"no such page"}""", LibraryError.Http(404, "no such page")),
                Triple(401, "", LibraryError.SessionExpired),
                Triple(200, """{"books": [""", LibraryError.UnexpectedResponse),
            ).forEach { (code, body, error) ->

                it("TC-25: HTTP $code with body '$body' gives Failure($error) and does not throw") {
                    enqueue(code, body)

                    repository.getBooksPageFlow().single() shouldBe LibraryPageResult.Failure(error)
                }
            }

            it("TC-25: a plain-text body longer than 300 characters is truncated to 300") {
                enqueue(500, "x".repeat(400))

                repository.getBooksPageFlow().single() shouldBe
                    LibraryPageResult.Failure(LibraryError.Http(500, "x".repeat(300)))
            }

            it("TC-25: an unreachable server gives Failure(Unreachable)") {
                server.close()

                repository.getBooksPageFlow().single() shouldBe
                    LibraryPageResult.Failure(LibraryError.Unreachable)
            }
        }

        describe("no server") {

            it("TC-26: returns NoServer without making a request") {
                baseUrlProvider.baseUrl = null

                repository.getBooksPageFlow().single() shouldBe LibraryPageResult.NoServer

                server.requestCount shouldBe 0
            }
        }
    }

    describe("getRecentProgressFlow") {

        it("TC-9: GET api/progress/recent with limit=5") {
            enqueue(200, "[]")

            repository.getRecentProgressFlow().single() shouldBe RecentProgressResult.Success(emptyList())

            val request = server.takeRequest()
            request.method shouldBe "GET"
            request.url.encodedPath shouldBe "/api/progress/recent"
            request.url.queryParameter("limit") shouldBe "5"
        }

        it("TC-10: decodes a reading item and a listening item into the domain model") {
            enqueue(
                200,
                """
                [
                    {
                        "record": {
                            "book_uuid": "uuid-1",
                            "format": "epub",
                            "epub_cfi": "epubcfi(/6/4)",
                            "progress_percent": 42.5,
                            "updated_at": 1700000000,
                            "client_updated_at": 1700000001,
                            "book_file_id": 10,
                            "resolved": {
                                "chapter_ordinal": 3,
                                "chapters_total": 12,
                                "chapter_title": "Chapter Three",
                                "percent_through_book": 25.0,
                                "confidence": "high"
                            }
                        },
                        "book": {"id": 1, "unique_identifier": "uuid-1", "title": "Dune", "filename": "dune.epub", "formats": ["epub"]}
                    },
                    {
                        "record": {
                            "book_uuid": "uuid-2",
                            "format": "audio",
                            "audio_position_seconds": 3600.5,
                            "progress_percent": 12.0,
                            "updated_at": 1700000100,
                            "book_file_id": 20,
                            "total_duration_seconds": 7200.0,
                            "resolved": {"chapter_title": "Intro", "confidence": "low"}
                        },
                        "book": {"id": 2, "unique_identifier": "uuid-2", "title": "Audio", "formats": ["m4b"]},
                        "audio_part": 2,
                        "audio_part_count": 4,
                        "playback_rate": 1.25
                    }
                ]
                """,
            )

            repository.getRecentProgressFlow().single() shouldBe RecentProgressResult.Success(
                listOf(
                    ResumePoint(
                        record = ProgressRecord(
                            bookUuid = "uuid-1",
                            format = ProgressFormat.Reading,
                            epubCfi = "epubcfi(/6/4)",
                            audioPositionSeconds = null,
                            progressPercent = 42.5,
                            updatedAt = 1700000000,
                            clientUpdatedAt = 1700000001,
                            bookFileId = 10,
                            totalDurationSeconds = null,
                            resolved = ResolvedPosition(
                                chapterOrdinal = 3,
                                chaptersTotal = 12,
                                chapterTitle = "Chapter Three",
                                percentThroughBook = 25.0,
                                confidence = PositionConfidence.High,
                            ),
                        ),
                        book = testBook(
                            1,
                            title = "Dune",
                            creators = emptyList(),
                            formats = listOf("epub"),
                            uniqueIdentifier = "uuid-1",
                            filename = "dune.epub",
                        ),
                        audioPart = null,
                        audioPartCount = null,
                        playbackRate = null,
                    ),
                    ResumePoint(
                        record = ProgressRecord(
                            bookUuid = "uuid-2",
                            format = ProgressFormat.Listening,
                            epubCfi = null,
                            audioPositionSeconds = 3600.5,
                            progressPercent = 12.0,
                            updatedAt = 1700000100,
                            clientUpdatedAt = null,
                            bookFileId = 20,
                            totalDurationSeconds = 7200.0,
                            resolved = ResolvedPosition(
                                chapterOrdinal = null,
                                chaptersTotal = null,
                                chapterTitle = "Intro",
                                percentThroughBook = null,
                                confidence = PositionConfidence.Low,
                            ),
                        ),
                        book = testBook(
                            2,
                            title = "Audio",
                            creators = emptyList(),
                            formats = listOf("m4b"),
                            uniqueIdentifier = "uuid-2",
                            filename = "",
                        ),
                        audioPart = 2,
                        audioPartCount = 4,
                        playbackRate = 1.25,
                    ),
                ),
            )
        }

        it("TC-11: an item with only required fields decodes with nulls and ignores unknown keys") {
            enqueue(
                200,
                """[{"record":{"book_uuid":"uuid-3","format":"epub","updated_at":5,"future_key":"x"},"book":{"id":3,"novel_key":true}}]""",
            )

            val point = repository.getRecentProgressFlow().single()
                .shouldBeTypeOf<RecentProgressResult.Success>().points.single()

            point.record shouldBe ProgressRecord(
                bookUuid = "uuid-3",
                format = ProgressFormat.Reading,
                epubCfi = null,
                audioPositionSeconds = null,
                progressPercent = null,
                updatedAt = 5,
                clientUpdatedAt = null,
                bookFileId = null,
                totalDurationSeconds = null,
                resolved = null,
            )
            point.audioPart.shouldBeNull()
            point.audioPartCount.shouldBeNull()
            point.playbackRate.shouldBeNull()
        }

        it("TC-24: emits exactly once and then completes") {
            enqueue(200, "[]")

            repository.getRecentProgressFlow().toList().size shouldBe 1
        }

        it("TC-27: a 500 gives Failure(Http) carrying the body as serverMessage") {
            enqueue(500, "internal server error")

            repository.getRecentProgressFlow().single() shouldBe
                RecentProgressResult.Failure(LibraryError.Http(500, "internal server error"))
        }

        it("TC-27: a malformed 200 body gives Failure(UnexpectedResponse)") {
            enqueue(200, "{")

            repository.getRecentProgressFlow().single() shouldBe
                RecentProgressResult.Failure(LibraryError.UnexpectedResponse)
        }

        it("TC-27: an unreachable server gives Failure(Unreachable)") {
            server.close()

            repository.getRecentProgressFlow().single() shouldBe
                RecentProgressResult.Failure(LibraryError.Unreachable)
        }

        it("TC-26: returns NoServer without making a request") {
            baseUrlProvider.baseUrl = null

            repository.getRecentProgressFlow().single() shouldBe RecentProgressResult.NoServer

            server.requestCount shouldBe 0
        }
    }

    describe("getShelfPreviewsFlow") {

        it("TC-12: fetches pages only for shelves with books, keeping at most four covers in order") {
            serveShelves { id -> shelfPage(if (id == 2L) listOf(20, 21) else (30L..38L).toList()) }

            val previews = repository.getShelfPreviewsFlow().single()
                .shouldBeTypeOf<ShelfPreviewsResult.Success>().previews

            previews.map { it.shelf.id } shouldBe listOf(1L, 2L, 3L)
            previews.map { preview -> preview.covers.map { it.id } } shouldBe
                listOf(emptyList(), listOf(20L, 21L), listOf(30L, 31L, 32L, 33L))
            requested.toList() shouldContainExactlyInAnyOrder
                listOf("/api/shelves", "/api/shelves/2/page", "/api/shelves/3/page")
        }

        it("TC-13: keeps the server's shelf order when a page answers late, and decodes shelf fields") {
            serveShelves { id -> if (id == 2L) shelfPage(listOf(20), delay = 300) else shelfPage(listOf(30)) }

            val previews = repository.getShelfPreviewsFlow().single()
                .shouldBeTypeOf<ShelfPreviewsResult.Success>().previews

            previews.map { it.shelf.id } shouldBe listOf(1L, 2L, 3L)
            previews[1].shelf shouldBe Shelf(
                id = 2,
                ownerUserId = 7,
                ownerUsername = "hunter",
                kind = ShelfKind.Manual,
                name = "Two",
                visibility = ShelfVisibility.Public,
                accent = "#c8a04a",
                bookCount = 2,
            )
            previews[2].shelf.kind shouldBe ShelfKind.Wishlist
            previews[2].shelf.visibility shouldBe ShelfVisibility.Private
        }

        it("TC-14: a failed shelf page gives that shelf no covers and leaves the others intact") {
            serveShelves { id ->
                if (id == 2L) {
                    MockResponse.Builder().code(500).body("boom").build()
                } else {
                    shelfPage((30L..38L).toList())
                }
            }

            val previews = repository.getShelfPreviewsFlow().single()
                .shouldBeTypeOf<ShelfPreviewsResult.Success>().previews

            previews.map { it.shelf.id } shouldBe listOf(1L, 2L, 3L)
            previews[1].covers.shouldBeEmpty()
            previews[2].covers.map { it.id } shouldBe listOf(30L, 31L, 32L, 33L)
        }

        it("TC-15: a failed shelf list gives Failure(Http) with the server message and no exception") {
            enqueue(500, "internal server error")

            repository.getShelfPreviewsFlow().single() shouldBe
                ShelfPreviewsResult.Failure(LibraryError.Http(500, "internal server error"))
        }

        it("TC-24: emits exactly once and then completes") {
            enqueue(200, "[]")

            repository.getShelfPreviewsFlow().toList().size shouldBe 1
        }

        it("TC-27: a 401 gives Failure(SessionExpired)") {
            enqueue(401)

            repository.getShelfPreviewsFlow().single() shouldBe
                ShelfPreviewsResult.Failure(LibraryError.SessionExpired)
        }

        it("TC-27: a malformed shelf list gives Failure(UnexpectedResponse)") {
            enqueue(200, "[{")

            repository.getShelfPreviewsFlow().single() shouldBe
                ShelfPreviewsResult.Failure(LibraryError.UnexpectedResponse)
        }

        it("TC-27: an unreachable server gives Failure(Unreachable)") {
            server.close()

            repository.getShelfPreviewsFlow().single() shouldBe
                ShelfPreviewsResult.Failure(LibraryError.Unreachable)
        }

        it("TC-26: returns NoServer without making a request") {
            baseUrlProvider.baseUrl = null

            repository.getShelfPreviewsFlow().single() shouldBe ShelfPreviewsResult.NoServer

            server.requestCount shouldBe 0
        }
    }

    describe("category formats on the wire") {

        it("TC-107: Ebooks sends the ebook formats verbatim and in order") {
            enqueue(200, EMPTY_BOOKS)

            repository.getBooksPageFlow(LibraryCategory.Ebooks.toQuery(LibrarySort.NewestAdded, SortDirection.Desc))
                .single()

            server.takeRequest().url.queryParameter("formats") shouldBe "epub,pdf,mobi,azw3,cbz,cbr"
        }

        it("TC-107: Audiobooks sends the audio formats verbatim and in order") {
            enqueue(200, EMPTY_BOOKS)

            repository.getBooksPageFlow(LibraryCategory.Audiobooks.toQuery(LibrarySort.NewestAdded, SortDirection.Desc))
                .single()

            server.takeRequest().url.queryParameter("formats") shouldBe "m4b,m4a,mp3,aac,flac,ogg,opus"
        }

        it("TC-107: All sends no formats param") {
            enqueue(200, EMPTY_BOOKS)

            repository.getBooksPageFlow(LibraryCategory.All.toQuery(LibrarySort.NewestAdded, SortDirection.Desc))
                .single()

            server.takeRequest().url.queryParameter("formats").shouldBeNull()
        }
    }

    describe("single emission") {

        it("TC-24: a books page emits exactly once and then completes") {
            enqueue(200, EMPTY_BOOKS)

            repository.getBooksPageFlow().toList().size shouldBe 1
        }
    }
}) {
    private companion object {
        const val EMPTY_BOOKS = """{"books":[]}"""

        const val SHELVES_JSON = """
            [
                {"id":1,"owner_user_id":7,"owner_username":"hunter","kind":"smart","name":"Empty","visibility":"private","accent":null,"book_count":0},
                {"id":2,"owner_user_id":7,"owner_username":"hunter","kind":"manual","name":"Two","visibility":"public","accent":"#c8a04a","book_count":2},
                {"id":3,"owner_user_id":8,"owner_username":"ann","kind":"wishlist","name":"Nine","visibility":"private","book_count":9}
            ]
        """
    }
}
