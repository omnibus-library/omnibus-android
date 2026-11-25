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

        context("when the answer has no book total") {
            it("#351 TC-3: takes the number of books as the total") {
                withScriptedServer {
                    script.answer(200, paletteBody("$DUNE,$LOTR"))

                    val result = repository().searchLibrary("lo") as PaletteSearchResult.Success

                    result.results.books.total shouldBe 2
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
