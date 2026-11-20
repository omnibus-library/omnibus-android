package com.omnibus.omnibus.data.search

import com.omnibus.omnibus.testing.ScriptedServer
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.testOmnibusHttpClient
import com.omnibus.omnibus.testing.unmockAndroidLog
import com.omnibus.omnibus.testing.unreachableUrl
import com.omnibus.omnibus.testing.withScriptedServer
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import okhttp3.HttpUrl

/** A palette answer holding [books] and, when given, a `book_total`. */
private fun paletteBody(books: String, total: Int? = null): String =
    """{"query":"lo","books":[$books]${total?.let { ""","book_total":$it""" } ?: ""}}"""

private const val DUNE = """{"id":1,"uuid":"u-1","title":"Dune","author_display":"Frank Herbert","year":"1965","formats":["EPUB"],"cover_url":"/c","accent":"#fff","has_physical":false}"""
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
})
