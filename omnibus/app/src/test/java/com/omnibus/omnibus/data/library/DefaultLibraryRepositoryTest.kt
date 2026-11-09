package com.omnibus.omnibus.data.library

import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.nextRequest
import com.omnibus.omnibus.testing.randomEnum
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.reply
import com.omnibus.omnibus.testing.testOmnibusHttpClient
import com.omnibus.omnibus.testing.unmockAndroidLog
import com.omnibus.omnibus.testing.withMockServer
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.single
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer

private const val EMPTY_BOOKS = """{"books":[]}"""

private const val SHELVES_JSON = """
    [
        {"id":1,"owner_user_id":7,"owner_username":"hunter","kind":"smart","name":"Empty","visibility":"private","accent":null,"book_count":0},
        {"id":2,"owner_user_id":7,"owner_username":"hunter","kind":"manual","name":"Two","visibility":"public","accent":"#c8a04a","book_count":2},
        {"id":3,"owner_user_id":8,"owner_username":"ann","kind":"wishlist","name":"Nine","visibility":"private","book_count":9}
    ]
"""

private const val LISTENING_ITEM = """
    [{"record": {
        "book_uuid": "uuid-2", "format": "audio", "audio_position_seconds": 3600.5, "progress_percent": 12.0,
        "updated_at": 1700000100, "book_file_id": 20, "total_duration_seconds": 7200.0,
        "resolved": {"chapter_title": "Intro", "confidence": "low"}},
      "book": {"id": 2},
      "audio_part": 2, "audio_part_count": 4, "playback_rate": 1.25}]
"""

private const val SERIES_JSON = """
    {"id":7,"name":"Dune Chronicles","book_count":1,"books":[
        {"id":1,"unique_identifier":"uuid-1","title":"Dune","series":"Dune Chronicles","series_index":"1","series_id":7,
         "creators":[{"name":"Frank Herbert","role":"author","id":5}],"formats":["epub","m4b"]}]}
"""

private fun booksBody(ids: List<Long>): String = """{"books":[${ids.joinToString(",") { """{"id":$it}""" }}]}"""

class DefaultLibraryRepositoryTest : DescribeSpec({

    fun sut(server: MockWebServer? = null) = DefaultLibraryRepository(
        ioDispatcher = Dispatchers.Unconfined,
        omnibusHttpClient = testOmnibusHttpClient(server?.url("/")),
    )

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("reading a page of books") {

        it("TC-1: asks for the ebooks in the query's order, with no cursor or filters") {
            withMockServer { server ->
                server.enqueue(reply(200, EMPTY_BOOKS))

                sut(server).getBooksPageFlow().single()

                val url = server.nextRequest().url
                url.encodedPath shouldBe "/api/ebooks"
                url.queryParameter("sort") shouldBe "newest_added"
                url.queryParameter("dir") shouldBe "desc"
                url.queryParameter("cursor").shouldBeNull()
                url.queryParameter("formats").shouldBeNull()
                url.queryParameter("exclude_formats").shouldBeNull()
            }
        }

        it("TC-4: sends every sort and direction as the server's token") {
            checkAll(
                Exhaustive.of(
                    LibrarySort.Title to "title",
                    LibrarySort.Author to "author",
                    LibrarySort.Series to "series",
                    LibrarySort.RecentlyInteracted to "recently_interacted",
                    LibrarySort.LastUpdated to "last_updated",
                    LibrarySort.NewestAdded to "newest_added",
                ),
                Exhaustive.of(SortDirection.Asc to "asc", SortDirection.Desc to "desc"),
            ) { (sort, sortToken), (dir, dirToken) ->
                withMockServer { server ->
                    server.enqueue(reply(200, EMPTY_BOOKS))

                    sut(server).getBooksPageFlow(BooksPageQuery(sort = sort, dir = dir)).single()

                    val url = server.nextRequest().url
                    (url.queryParameter("sort") to url.queryParameter("dir")) shouldBe (sortToken to dirToken)
                }
            }
        }

        it("TC-107: sends each category's formats verbatim, in order") {
            checkAll(
                Exhaustive.of(
                    LibraryCategory.All to null,
                    LibraryCategory.Ebooks to "epub,pdf,mobi,azw3,cbz,cbr",
                    LibraryCategory.Audiobooks to "m4b,m4a,mp3,aac,flac,ogg,opus",
                ),
            ) { (category, formats) ->
                withMockServer { server ->
                    server.enqueue(reply(200, EMPTY_BOOKS))

                    sut(server).getBooksPageFlow(category.toQuery(randomEnum(), randomEnum())).single()

                    server.nextRequest().url.queryParameter("formats") shouldBe formats
                }
            }
        }

        context("when given a cursor") {
            it("TC-2: still carries the ordering, which the server requires") {
                withMockServer { server ->
                    server.enqueue(reply(200, EMPTY_BOOKS))
                    val cursor = randomString()

                    sut(server).getBooksPageFlow(BooksPageQuery(LibrarySort.Title, SortDirection.Asc), cursor).single()

                    val url = server.nextRequest().url
                    url.queryParameter("cursor") shouldBe cursor
                    url.queryParameter("sort") shouldBe "title"
                    url.queryParameter("dir") shouldBe "asc"
                }
            }
        }

        context("when filtering by format") {
            it("TC-3: normalizes the format filters") {
                withMockServer { server ->
                    server.enqueue(reply(200, EMPTY_BOOKS))

                    sut(server).getBooksPageFlow(
                        BooksPageQuery(formats = setOf("EPUB", "Pdf"), excludeFormats = setOf("m4b", "epub")),
                    ).single()

                    val url = server.nextRequest().url
                    url.queryParameter("formats") shouldBe "epub,pdf"
                    url.queryParameter("exclude_formats") shouldBe "epub,m4b"
                }
            }
        }

        context("when the server sends page headers") {
            it("TC-6: carries the paging details") {
                withMockServer { server ->
                    server.enqueue(
                        MockResponse.Builder()
                            .code(200)
                            .body(booksBody(listOf(1)))
                            .addHeader("X-Next-Cursor", "c2")
                            .addHeader("X-Total-Count", "123")
                            .addHeader("X-Hidden-Count", "4")
                            .build(),
                    )

                    val page = sut(server).getBooksPageFlow().single().shouldBeTypeOf<LibraryPageResult.Success>().page

                    page.nextCursor shouldBe "c2"
                    page.total shouldBe 123L
                    page.hiddenCount shouldBe 4
                }
            }
        }

        context("when the server sends no page headers") {
            it("TC-7: has no paging details") {
                withMockServer { server ->
                    server.enqueue(reply(200, booksBody(listOf(1))))

                    val page = sut(server).getBooksPageFlow().single().shouldBeTypeOf<LibraryPageResult.Success>().page

                    page.nextCursor.shouldBeNull()
                    page.total.shouldBeNull()
                    page.hiddenCount.shouldBeNull()
                }
            }
        }

        it("TC-8: maps the books, with or without the body's optional keys") {
            checkAll(
                Exhaustive.of(
                    """{"path":"/x","books":[{"id":1}],"error":null,"total":5}""",
                    """{"books":[{"id":1}]}""",
                ),
            ) { body ->
                withMockServer { server ->
                    server.enqueue(reply(200, body))

                    val page = sut(server).getBooksPageFlow().single().shouldBeTypeOf<LibraryPageResult.Success>().page

                    page.books.map { it.id } shouldBe listOf(1L)
                }
            }
        }

        context("when the server answers an error") {
            it("TC-25: gives the failure for every kind of answer") {
                checkAll(
                    Exhaustive.of(
                        Triple(500, "internal server error", LibraryError.Http(500, "internal server error")),
                        Triple(500, "", LibraryError.Http(500, null)),
                        Triple(500, "   \n ", LibraryError.Http(500, null)),
                        Triple(400, """{"error":"bad cursor"}""", LibraryError.Http(400, "bad cursor")),
                        Triple(422, """{"message":"bad sort"}""", LibraryError.Http(422, "bad sort")),
                        Triple(404, """{"detail":"no such page"}""", LibraryError.Http(404, "no such page")),
                        Triple(401, "", LibraryError.SessionExpired),
                        Triple(200, """{"books": [""", LibraryError.UnexpectedResponse),
                    ),
                ) { (code, body, error) ->
                    withMockServer { server ->
                        server.enqueue(reply(code, body))

                        sut(server).getBooksPageFlow().single() shouldBe LibraryPageResult.Failure(error)
                    }
                }
            }
        }

        context("when the error's text is longer than 300 characters") {
            it("TC-25: truncates it to 300") {
                withMockServer { server ->
                    server.enqueue(reply(500, "x".repeat(400)))

                    sut(server).getBooksPageFlow().single() shouldBe
                        LibraryPageResult.Failure(LibraryError.Http(500, "x".repeat(300)))
                }
            }
        }

        context("when the server is unreachable") {
            it("TC-25: gives Unreachable") {
                withMockServer { server ->
                    val repository = sut(server)
                    server.close()

                    repository.getBooksPageFlow().single() shouldBe LibraryPageResult.Failure(LibraryError.Unreachable)
                }
            }
        }

        context("when no server is saved") {
            it("TC-26: gives NoServer") {
                sut().getBooksPageFlow().single() shouldBe LibraryPageResult.NoServer
            }
        }
    }

    describe("reading the recent progress") {

        it("TC-9: asks for the recent progress") {
            withMockServer { server ->
                server.enqueue(reply(200, "[]"))

                sut(server).getRecentProgressFlow().single() shouldBe RecentProgressResult.Success(emptyList())

                server.nextRequest().url.encodedPath shouldBe "/api/progress/recent"
            }
        }

        context("when an item is a reading position") {
            it("TC-10: maps its record") {
                withMockServer { server ->
                    server.enqueue(
                        reply(
                            200,
                            """
                            [{"record": {
                                "book_uuid": "uuid-1", "format": "epub", "epub_cfi": "epubcfi(/6/4)",
                                "progress_percent": 42.5, "updated_at": 1700000000, "client_updated_at": 1700000001,
                                "book_file_id": 10,
                                "resolved": {"chapter_ordinal": 3, "chapters_total": 12, "chapter_title": "Chapter Three",
                                             "percent_through_book": 25.0, "confidence": "high"}},
                              "book": {"id": 1}}]
                            """,
                        ),
                    )

                    val point = sut(server).getRecentProgressFlow().single()
                        .shouldBeTypeOf<RecentProgressResult.Success>().points.single()

                    point.record shouldBe ProgressRecord(
                        bookUuid = "uuid-1",
                        format = ProgressFormat.Reading,
                        epubCfi = "epubcfi(/6/4)",
                        audioPositionSeconds = null,
                        progressPercent = 42.5,
                        updatedAt = 1700000000,
                        clientUpdatedAt = 1700000001,
                        bookFileId = 10,
                        totalDurationSeconds = null,
                        resolved = ResolvedPosition(3, 12, "Chapter Three", 25.0, PositionConfidence.High),
                    )
                }
            }
        }

        context("when an item is a listening position") {
            it("TC-10: maps its record") {
                withMockServer { server ->
                    server.enqueue(reply(200, LISTENING_ITEM))

                    val point = sut(server).getRecentProgressFlow().single()
                        .shouldBeTypeOf<RecentProgressResult.Success>().points.single()

                    point.record shouldBe ProgressRecord(
                        bookUuid = "uuid-2",
                        format = ProgressFormat.Listening,
                        epubCfi = null,
                        audioPositionSeconds = 3600.5,
                        progressPercent = 12.0,
                        updatedAt = 1700000100,
                        clientUpdatedAt = null,
                        bookFileId = 20,
                        totalDurationSeconds = 7200.0,
                        resolved = ResolvedPosition(null, null, "Intro", null, PositionConfidence.Low),
                    )
                }
            }

            it("TC-10: carries the listening details") {
                withMockServer { server ->
                    server.enqueue(reply(200, LISTENING_ITEM))

                    val point = sut(server).getRecentProgressFlow().single()
                        .shouldBeTypeOf<RecentProgressResult.Success>().points.single()

                    point.audioPart shouldBe 2
                    point.audioPartCount shouldBe 4
                    point.playbackRate shouldBe 1.25
                }
            }
        }

        context("when an item has only the required fields") {
            it("TC-11: reads the rest as empty, ignoring unknown keys") {
                withMockServer { server ->
                    server.enqueue(
                        reply(
                            200,
                            """[{"record":{"book_uuid":"uuid-3","format":"epub","updated_at":5,"future_key":"x"},"book":{"id":3,"novel_key":true}}]""",
                        ),
                    )

                    val point = sut(server).getRecentProgressFlow().single()
                        .shouldBeTypeOf<RecentProgressResult.Success>().points.single()

                    point shouldBe ResumePoint(
                        record = ProgressRecord("uuid-3", ProgressFormat.Reading, null, null, null, 5, null, null, null, null),
                        book = point.book,
                        audioPart = null,
                        audioPartCount = null,
                        playbackRate = null,
                    )
                }
            }
        }

        context("when an item's format is unknown") {
            it("drops the item") {
                withMockServer { server ->
                    server.enqueue(reply(200, """[{"record":{"book_uuid":"u","format":"kobo"},"book":{"id":3}}]"""))

                    sut(server).getRecentProgressFlow().single() shouldBe RecentProgressResult.Success(emptyList())
                }
            }
        }

        context("when the read fails") {
            it("TC-27: gives the failure") {
                withMockServer { server ->
                    server.enqueue(reply(500, "internal server error"))

                    sut(server).getRecentProgressFlow().single() shouldBe
                        RecentProgressResult.Failure(LibraryError.Http(500, "internal server error"))
                }
            }
        }

        context("when no server is saved") {
            it("TC-26: gives NoServer") {
                sut().getRecentProgressFlow().single() shouldBe RecentProgressResult.NoServer
            }
        }
    }

    describe("reading the shelf previews") {

        it("TC-1: reads the cover uuids, at most four, in order") {
            withMockServer { server ->
                server.enqueue(
                    reply(
                        200,
                        """
                        [
                            {"id":1,"owner_user_id":7,"kind":"manual","cover_uuids":[]},
                            {"id":2,"owner_user_id":7,"kind":"manual","cover_uuids":["a","b","c"]},
                            {"id":3,"owner_user_id":7,"kind":"manual","cover_uuids":["a","b","c","d","e","f"]},
                            {"id":4,"owner_user_id":7,"kind":"manual"},
                            {"id":5,"owner_user_id":7,"kind":"manual","cover_uuids":null}
                        ]
                        """,
                    ),
                )

                val previews = sut(server).getShelfPreviewsFlow().single()
                    .shouldBeTypeOf<ShelfPreviewsResult.Success>().previews

                previews.map { it.shelf.id to it.coverUuids } shouldBe listOf(
                    1L to emptyList(),
                    2L to listOf("a", "b", "c"),
                    3L to listOf("a", "b", "c", "d"),
                    4L to emptyList(),
                    5L to emptyList(),
                )
            }
        }

        it("TC-2: makes one request however many shelves there are") {
            // The two 404s answer any page read, so a read that still fans out gets an answer instead of hanging.
            withMockServer(reply(200, SHELVES_JSON), reply(404), reply(404)) { server ->
                sut(server).getShelfPreviewsFlow().single()

                server.requestCount shouldBe 1
                server.nextRequest().url.encodedPath shouldBe "/api/shelves"
            }
        }

        it("TC-13: maps each shelf's fields") {
            withMockServer { server ->
                server.enqueue(reply(200, SHELVES_JSON))

                val previews = sut(server).getShelfPreviewsFlow().single()
                    .shouldBeTypeOf<ShelfPreviewsResult.Success>().previews

                previews.map { it.shelf } shouldBe listOf(
                    Shelf(1, 7, "hunter", ShelfKind.Smart, "Empty", ShelfVisibility.Private, null, 0),
                    Shelf(2, 7, "hunter", ShelfKind.Manual, "Two", ShelfVisibility.Public, "#c8a04a", 2),
                    Shelf(3, 8, "ann", ShelfKind.Wishlist, "Nine", ShelfVisibility.Private, null, 9),
                )
            }
        }

        context("when the shelf list fails") {
            it("TC-15: gives the failure") {
                withMockServer { server ->
                    server.enqueue(reply(500, "internal server error"))

                    sut(server).getShelfPreviewsFlow().single() shouldBe
                        ShelfPreviewsResult.Failure(LibraryError.Http(500, "internal server error"))
                }
            }
        }

        context("when no server is saved") {
            it("TC-26: gives NoServer") {
                sut().getShelfPreviewsFlow().single() shouldBe ShelfPreviewsResult.NoServer
            }
        }
    }

    describe("reading one book") {

        it("TC-44: gives the book") {
            withMockServer { server ->
                server.enqueue(reply(200, """{"id": 42, "title": "Dune"}"""))

                val book = sut(server).getBookFlow(randomString()).single().shouldBeTypeOf<BookResult.Success>().book

                book.title shouldBe "Dune"
            }
        }

        it("TC-48: asks for the uuid as one encoded path segment") {
            withMockServer { server ->
                server.enqueue(reply(200, """{"id": 1}"""))

                sut(server).getBookFlow("a b/c").single()

                server.nextRequest().url.encodedPath shouldBe "/api/ebooks/a%20b%2Fc"
            }
        }

        context("when the server answers 404") {
            it("TC-45: gives NotFound") {
                withMockServer { server ->
                    server.enqueue(reply(404, """{"error":"no such book"}"""))

                    sut(server).getBookFlow(randomString()).single() shouldBe BookResult.NotFound
                }
            }
        }

        context("when the server answers another error") {
            it("TC-47: gives the failure") {
                withMockServer { server ->
                    server.enqueue(reply(500, "boom"))

                    sut(server).getBookFlow(randomString()).single() shouldBe
                        BookResult.Failure(LibraryError.Http(500, "boom"))
                }
            }
        }

        context("when no server is saved") {
            it("TC-46: gives NoServer") {
                sut().getBookFlow(randomString()).single() shouldBe BookResult.NoServer
            }
        }

        context("when the uuid is blank") {
            it("TC-48: gives NotFound") {
                withMockServer { server ->
                    sut(server).getBookFlow("   ").single() shouldBe BookResult.NotFound
                }
            }

            it("TC-48: sends no request") {
                withMockServer { server ->
                    sut(server).getBookFlow("   ").single()

                    server.requestCount shouldBe 0
                }
            }
        }
    }

    describe("reading a series' books") {

        it("#235 TC-6: decodes the series' name and books the way the books page does") {
            withMockServer { server ->
                server.enqueue(reply(200, SERIES_JSON))

                val series = sut(server).getSeriesBooksFlow(7).single()
                    .shouldBeTypeOf<SeriesBooksResult.Success>().series

                series.name shouldBe "Dune Chronicles"
                val book = series.books.single()
                book.uuid shouldBe "uuid-1"
                book.seriesId shouldBe 7L
                book.creators shouldBe listOf(Creator(name = "Frank Herbert", role = "author", fileAs = null, id = 5L))
                book.formats shouldBe listOf("epub", "m4b")
            }
        }

        it("#235 TC-6: asks for the series by its id") {
            withMockServer { server ->
                server.enqueue(reply(200, SERIES_JSON))

                sut(server).getSeriesBooksFlow(7).single()

                server.nextRequest().url.encodedPath shouldBe "/api/series/7"
            }
        }
    }
})
