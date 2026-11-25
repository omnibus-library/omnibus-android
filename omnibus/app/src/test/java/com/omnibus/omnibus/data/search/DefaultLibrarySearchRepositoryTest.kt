package com.omnibus.omnibus.data.search

import com.omnibus.omnibus.testing.ScriptedServer
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.testOmnibusHttpClient
import com.omnibus.omnibus.testing.unmockAndroidLog
import com.omnibus.omnibus.testing.unreachableUrl
import com.omnibus.omnibus.testing.withScriptedServer
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.Dispatchers
import okhttp3.HttpUrl

/** A palette answer holding [books] and, when given, a `book_total`. */
private fun paletteBody(books: String, total: Int? = null): String =
    """{"query":"lo","books":[$books]${total?.let { ""","book_total":$it""" } ?: ""}}"""

/** A whole-library palette answer holding [tags] and, when given, a `tag_total`, with no books. */
private fun tagsBody(tags: String, tagTotal: Int? = null): String =
    """{"query":"wood","books":[],"tags":[$tags]${tagTotal?.let { ""","tag_total":$it""" } ?: ""}}"""

/** A whole-library palette answer holding [genres] and, when given, a `genre_total`, with no books. */
private fun genresBody(genres: String, genreTotal: Int? = null): String =
    """{"query":"fan","books":[],"genres":[$genres]${genreTotal?.let { ""","genre_total":$it""" } ?: ""}}"""

/** A whole-library palette answer holding [authors] and, when given, an `author_total`, with no books. */
private fun authorsBody(authors: String, authorTotal: Int? = null): String =
    """{"query":"her","books":[],"authors":[$authors]${authorTotal?.let { ""","author_total":$it""" } ?: ""}}"""

private const val HERBERT = """{"id":7,"name":"Frank Herbert","book_count":9,"lead_book_title":"Dune"}"""
private const val HERMAN = """{"id":8,"name":"Herman Melville","book_count":2}"""

/** A whole-library palette answer holding [series] and, when given, a `series_total`, with no books. */
private fun seriesBody(series: String, seriesTotal: Int? = null): String =
    """{"query":"dun","books":[],"series":[$series]${seriesTotal?.let { ""","series_total":$it""" } ?: ""}}"""

private const val DUNE_CHRONICLES = """{"id":3,"name":"Dune Chronicles","book_count":6,"author_display":"Frank Herbert"}"""
private const val DUNGEON_CRAWL = """{"id":4,"name":"Dungeon Crawl","book_count":2}"""

private const val FANTASY = """{"name":"Fantasy","book_count":40}"""
private const val FAN_FICTION = """{"name":"Fan fiction","book_count":3}"""

private const val WOODLAND = """{"name":"Woodland","book_count":14}"""
private const val WOODWORKING = """{"name":"Woodworking","book_count":2}"""

private const val DUNE = """{"id":1,"uuid":"u-1","title":"Dune","author_display":"Frank Herbert","year":"1965","formats":["EPUB"],"cover_url":"/c","accent":"#fff","has_physical":false}"""
private const val WOOD = """{"id":3,"uuid":"u-3","title":"Mythago Wood","author_display":"Robert Holdstock","year":"1984","formats":["epub"],"has_physical":true}"""
private const val LOTR = """{"id":2,"uuid":"u-2","title":"Lord of the Rings","author_display":"J. R. R. Tolkien","formats":[]}"""

class DefaultLibrarySearchRepositoryTest : DescribeSpec({

    fun sut(baseUrl: HttpUrl? = null): DefaultLibrarySearchRepository =
        DefaultLibrarySearchRepository(Dispatchers.Unconfined, testOmnibusHttpClient(baseUrl))

    fun ScriptedServer.repository(): DefaultLibrarySearchRepository = sut(url)

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("searching the library's books") {

        it("#335 TC-66: returns the books and their total") {
            withScriptedServer {
                script.answer(200, paletteBody("$DUNE,$LOTR", total = 23))

                val result = repository().searchBooks("lo")

                result shouldBe LibrarySearchResult.Success(
                    LibraryBookMatches(
                        books = listOf(
                            LibraryHit("u-1", "Dune", "Frank Herbert", "1965", listOf("EPUB"), "/c", "#fff"),
                            LibraryHit("u-2", "Lord of the Rings", "J. R. R. Tolkien", null, emptyList(), null, null),
                        ),
                        total = 23,
                    ),
                )
            }
        }

        it("GETs api/search/palette") {
            withScriptedServer {
                script.answer(200, paletteBody(DUNE, total = 1))

                repository().searchBooks("lo")

                nextRequest().let { it.method to it.url.encodedPath } shouldBe ("GET" to "/api/search/palette")
            }
        }

        it("sends the query as the q parameter") {
            withScriptedServer {
                script.answer(200, paletteBody(DUNE, total = 1))

                repository().searchBooks("du ne")

                nextRequest().url.queryParameter("q") shouldBe "du ne"
            }
        }

        it("#335 TC-66: takes the number of books as the total when book_total is missing") {
            withScriptedServer {
                script.answer(200, paletteBody("$DUNE,$LOTR"))

                val matches = (repository().searchBooks("lo") as LibrarySearchResult.Success).matches

                matches.total shouldBe 2
            }
        }

        it("maps a 401 to SessionExpired") {
            withScriptedServer {
                script.answer(401, "{}")

                repository().searchBooks("lo") shouldBe LibrarySearchResult.Failure(LibrarySearchError.SessionExpired)
            }
        }

        it("maps any other failure status to Http with its code") {
            withScriptedServer {
                script.answer(500, "boom")

                repository().searchBooks("lo") shouldBe LibrarySearchResult.Failure(LibrarySearchError.Http(500))
            }
        }

        it("maps a body that doesn't decode to UnexpectedResponse") {
            withScriptedServer {
                script.answer(200, "not json")

                repository().searchBooks("lo") shouldBe LibrarySearchResult.Failure(LibrarySearchError.UnexpectedResponse)
            }
        }

        it("maps a dropped connection to Unreachable") {
            sut(unreachableUrl()).searchBooks("lo") shouldBe LibrarySearchResult.Failure(LibrarySearchError.Unreachable)
        }

        it("reports NoServer when no server is saved") {
            sut(baseUrl = null).searchBooks("lo") shouldBe LibrarySearchResult.NoServer
        }
    }

    describe("searching the whole library") {

        it("#351 TC-1: returns the books with their total") {
            withScriptedServer {
                script.answer(200, paletteBody("$WOOD,$LOTR", total = 23))

                val result = repository().searchLibrary("lo")

                result shouldBe PaletteSearchResult.Success(
                    LibrarySearchResults(
                        books = SearchMatches(
                            items = listOf(
                                LibraryHit("u-3", "Mythago Wood", "Robert Holdstock", "1984", listOf("epub"), null, null, hasPhysical = true),
                                LibraryHit("u-2", "Lord of the Rings", "J. R. R. Tolkien", null, emptyList(), null, null, hasPhysical = false),
                            ),
                            total = 23,
                        ),
                    ),
                )
            }
        }

        it("#351 TC-2: GETs api/search/palette") {
            withScriptedServer {
                script.answer(200, paletteBody(DUNE, total = 1))

                repository().searchLibrary("lo")

                nextRequest().let { it.method to it.url.encodedPath } shouldBe ("GET" to "/api/search/palette")
            }
        }

        it("#351 TC-2: sends the query as the q parameter") {
            withScriptedServer {
                script.answer(200, paletteBody(DUNE, total = 1))

                repository().searchLibrary("du ne")

                nextRequest().url.queryParameter("q") shouldBe "du ne"
            }
        }

        it("#351 TC-2: sends no limit parameter") {
            withScriptedServer {
                script.answer(200, paletteBody(DUNE, total = 1))

                repository().searchLibrary("lo")

                nextRequest().url.queryParameter("limit") shouldBe null
            }
        }

        context("when the answer has tags") {
            it("#352 TC-1: returns the tags in order with the tag total") {
                withScriptedServer {
                    script.answer(200, tagsBody("$WOODLAND,$WOODWORKING", tagTotal = 9))

                    val result = repository().searchLibrary("wood") as PaletteSearchResult.Success

                    result.results.tags shouldBe SearchMatches(
                        items = listOf(TagMatch("Woodland", 14), TagMatch("Woodworking", 2)),
                        total = 9,
                    )
                }
            }

            context("when the answer has no tag total") {
                it("#352 TC-2: takes the number of tags as the total") {
                    withScriptedServer {
                        script.answer(200, tagsBody("$WOODLAND,$WOODWORKING"))

                        val result = repository().searchLibrary("wood") as PaletteSearchResult.Success

                        result.results.tags.total shouldBe 2
                    }
                }
            }
        }

        context("when the answer has authors") {
            it("#354 TC-1: returns the authors in order with the author total") {
                withScriptedServer {
                    script.answer(200, authorsBody("$HERBERT,$HERMAN", authorTotal = 6))

                    val result = repository().searchLibrary("her") as PaletteSearchResult.Success

                    result.results.authors shouldBe SearchMatches(
                        items = listOf(AuthorMatch(7, "Frank Herbert", 9, "Dune"), AuthorMatch(8, "Herman Melville", 2, null)),
                        total = 6,
                    )
                }
            }

            it("#354 TC-2: takes the number of authors as the total when the answer has none") {
                withScriptedServer {
                    script.answer(200, authorsBody("$HERBERT,$HERMAN"))

                    val result = repository().searchLibrary("her") as PaletteSearchResult.Success

                    result.results.authors.total shouldBe 2
                }
            }
        }

        context("when the answer has no authors") {
            it("#354 TC-3: has no authors") {
                withScriptedServer {
                    script.answer(200, paletteBody(DUNE, total = 1))

                    val result = repository().searchLibrary("lo") as PaletteSearchResult.Success

                    result.results.authors shouldBe SearchMatches.empty()
                }
            }
        }

        context("when the answer has series") {
            it("#355 TC-1: returns the series in order with the series total") {
                withScriptedServer {
                    script.answer(200, seriesBody("$DUNE_CHRONICLES,$DUNGEON_CRAWL", seriesTotal = 6))

                    val result = repository().searchLibrary("dun") as PaletteSearchResult.Success

                    result.results.series shouldBe SearchMatches(
                        items = listOf(
                            SeriesMatch(3, "Dune Chronicles", 6, "Frank Herbert"),
                            SeriesMatch(4, "Dungeon Crawl", 2, null),
                        ),
                        total = 6,
                    )
                }
            }

            it("#355 TC-2: takes the number of series as the total when the answer has none") {
                withScriptedServer {
                    script.answer(200, seriesBody("$DUNE_CHRONICLES,$DUNGEON_CRAWL"))

                    val result = repository().searchLibrary("dun") as PaletteSearchResult.Success

                    result.results.series.total shouldBe 2
                }
            }
        }

        context("when the answer has genres") {
            it("#353 TC-1: returns the genres in order with the genre total") {
                withScriptedServer {
                    script.answer(200, genresBody("$FANTASY,$FAN_FICTION", genreTotal = 7))

                    val result = repository().searchLibrary("fan") as PaletteSearchResult.Success

                    result.results.genres shouldBe SearchMatches(
                        items = listOf(GenreMatch("Fantasy", 40), GenreMatch("Fan fiction", 3)),
                        total = 7,
                    )
                }
            }

            it("#353 TC-2: takes the number of genres as the total when the answer has none") {
                withScriptedServer {
                    script.answer(200, genresBody("$FANTASY,$FAN_FICTION"))

                    val result = repository().searchLibrary("fan") as PaletteSearchResult.Success

                    result.results.genres.total shouldBe 2
                }
            }
        }

        context("when the answer has no genres") {
            it("#353 TC-3: has no genres") {
                withScriptedServer {
                    script.answer(200, paletteBody(DUNE, total = 1))

                    val result = repository().searchLibrary("lo") as PaletteSearchResult.Success

                    result.results.genres shouldBe SearchMatches.empty()
                }
            }
        }

        context("when the answer has no tags") {
            it("#352 TC-3: has no tags") {
                withScriptedServer {
                    script.answer(200, paletteBody(DUNE, total = 1))

                    val result = repository().searchLibrary("lo") as PaletteSearchResult.Success

                    result.results.tags shouldBe SearchMatches.empty()
                }
            }

            it("#352 TC-3: still reads the books") {
                withScriptedServer {
                    script.answer(200, paletteBody(DUNE, total = 1))

                    val result = repository().searchLibrary("lo") as PaletteSearchResult.Success

                    result.results.books.items.map { it.uuid } shouldBe listOf("u-1")
                }
            }
        }

        context("when the answer has no book total") {
            it("#351 TC-3: takes the number of books as the total") {
                withScriptedServer {
                    script.answer(200, paletteBody("$DUNE,$LOTR"))

                    val result = repository().searchLibrary("lo") as PaletteSearchResult.Success

                    result.results.books.total shouldBe 2
                }
            }
        }

        context("when a limit is given") {
            it("#351 TC-39: sends the limit as the limit parameter") {
                withScriptedServer {
                    script.answer(200, paletteBody(DUNE, total = 1))

                    repository().searchLibrary("lo", limit = 500)

                    nextRequest().url.queryParameter("limit") shouldBe "500"
                }
            }
        }
    }

    describe("failing to search the whole library") {

        it("#351 TC-4: reports NoServer when no server is saved") {
            sut(baseUrl = null).searchLibrary("lo") shouldBe PaletteSearchResult.NoServer
        }

        it("#351 TC-4: maps a dropped connection to Unreachable") {
            sut(unreachableUrl()).searchLibrary("lo") shouldBe
                PaletteSearchResult.Failure(LibrarySearchError.Unreachable)
        }

        it("#351 TC-4: maps a 401 to SessionExpired") {
            withScriptedServer {
                script.answer(401, "{}")

                repository().searchLibrary("lo") shouldBe PaletteSearchResult.Failure(LibrarySearchError.SessionExpired)
            }
        }

        it("#351 TC-4: maps every other failure status to Http with its code") {
            checkAll(Exhaustive.of(429, 500, 503)) { code ->
                withScriptedServer {
                    script.answer(code, "boom")

                    repository().searchLibrary("lo") shouldBe PaletteSearchResult.Failure(LibrarySearchError.Http(code))
                }
            }
        }

        it("#351 TC-4: maps a body that doesn't decode to UnexpectedResponse") {
            withScriptedServer {
                script.answer(200, "not json")

                repository().searchLibrary("lo") shouldBe PaletteSearchResult.Failure(LibrarySearchError.UnexpectedResponse)
            }
        }
    }
})
