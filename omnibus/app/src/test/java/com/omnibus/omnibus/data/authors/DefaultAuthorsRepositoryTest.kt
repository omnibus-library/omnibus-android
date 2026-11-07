package com.omnibus.omnibus.data.authors

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.TestClientInfo
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.data.network.TestAuthTokenProvider
import com.omnibus.omnibus.data.network.TestServerBaseUrlProvider
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.reply
import com.omnibus.omnibus.testing.unmockAndroidLog
import com.omnibus.omnibus.testing.withMockServer
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import kotlinx.coroutines.Dispatchers
import okhttp3.HttpUrl

/** An author with two books, the second carrying a key the app doesn't know. */
private fun authorJson(id: Long, name: String): String = """
{
  "id": $id,
  "name": "$name",
  "book_count": 2,
  "books": [
    {"id": 1, "unique_identifier": "uuid-1", "filename": "a.epub", "title": "The Dispossessed", "published": "1974-05-01",
     "creators": [{"name": "$name", "id": $id}], "formats": ["epub"]},
    {"id": 2, "unique_identifier": "uuid-2", "filename": "b.epub", "title": "Lavinia",
     "creators": [{"name": "$name", "id": $id}], "formats": ["epub"], "unknown_future_key": 1}
  ]
}
"""

class DefaultAuthorsRepositoryTest : DescribeSpec({

    fun sut(baseUrl: HttpUrl? = null): DefaultAuthorsRepository = DefaultAuthorsRepository(
        ioDispatcher = Dispatchers.Unconfined,
        omnibusHttpClient = OmnibusHttpClient(
            baseUrlProvider = TestServerBaseUrlProvider(baseUrl),
            authTokenProvider = TestAuthTokenProvider(),
            omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
            authClientMetadataInterceptor = AuthClientMetadataInterceptor(TestClientInfo()),
        ),
    )

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("reading an author") {
        context("when the server answers with the author") {
            it("#105 TC-1: returns the author with their books in the server's order") {
                withMockServer { server ->
                    val id = randomLong()
                    val name = randomString()
                    server.enqueue(reply(200, authorJson(id, name)))

                    val author = sut(server.url("/")).getAuthor(id).shouldBeTypeOf<AuthorResult.Success>().author

                    author.id shouldBe id
                    author.name shouldBe name
                    author.books.map { it.title } shouldContainExactly listOf("The Dispossessed", "Lavinia")
                }
            }

            it("#105 TC-1: reads api/authors/{id}") {
                withMockServer { server ->
                    val id = randomLong()
                    server.enqueue(reply(200, authorJson(id, randomString())))

                    sut(server.url("/")).getAuthor(id)

                    val request = server.takeRequest()
                    request.method shouldBe "GET"
                    request.url.encodedPath shouldBe "/api/authors/$id"
                }
            }
        }

        context("when the author has no books") {
            it("#105 TC-2: returns an empty book list") {
                withMockServer { server ->
                    val id = randomLong()
                    server.enqueue(reply(200, """{"id": $id, "name": "${randomString()}", "book_count": 0, "books": []}"""))

                    sut(server.url("/")).getAuthor(id).shouldBeTypeOf<AuthorResult.Success>().author.books.shouldBeEmpty()
                }
            }
        }

        context("when the read fails") {
            it("#105 TC-3: maps each status and unusable body to its error") {
                withMockServer { server ->
                    val failures = listOf(
                        reply(401) to AuthorsError.SessionExpired,
                        reply(404) to AuthorsError.NotFound,
                        reply(500) to AuthorsError.Http(500),
                        reply(200, "<html>") to AuthorsError.UnexpectedResponse,
                        reply(200, """{"name": "A"}""") to AuthorsError.UnexpectedResponse,
                    )
                    checkAll(Exhaustive.collection(failures)) { (response, error) ->
                        server.enqueue(response)

                        sut(server.url("/")).getAuthor(randomLong()) shouldBe AuthorResult.Failure(error)
                    }
                }
            }
        }

        context("when the server can't be reached") {
            it("#105 TC-4: returns Unreachable") {
                val closed = withMockServer { it.url("/") }

                sut(closed).getAuthor(randomLong()) shouldBe AuthorResult.Failure(AuthorsError.Unreachable)
            }
        }

        context("when no server is saved") {
            it("#105 TC-5: returns NoServer") {
                sut().getAuthor(randomLong()) shouldBe AuthorResult.NoServer
            }
        }
    }
})
