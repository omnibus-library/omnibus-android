package com.omnibus.omnibus.data.series

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.TestClientInfo
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.data.network.TestAuthTokenProvider
import com.omnibus.omnibus.data.network.TestServerBaseUrlProvider
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.Dispatchers
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl

/** [value] as a JSON string, or JSON null. */
private fun quoted(value: String?): String = value?.let { "\"$it\"" } ?: "null"

/** A series response holding [books], in that order. */
private fun seriesBody(vararg books: String): String = """{"books":[${books.joinToString(",")}]}"""

/** This member as the server writes it. */
private fun SeriesMember.toJson(): String =
    """{"unique_identifier":${quoted(uuid)},"filename":"${randomString()}","title":${quoted(title)},""" +
        """"series_index":${quoted(seriesIndex)},"cover_url":${quoted(coverUrl)},"accent":${quoted(accent)}}"""

/** A book whose order and uuid are all a test cares about. */
private fun bookWithIndex(uuid: String, seriesIndex: String?): String =
    randomSeriesMember().copy(uuid = uuid, seriesIndex = seriesIndex).toJson()

private fun reply(code: Int, body: String = ""): MockResponse = MockResponse.Builder().code(code).body(body).build()

class DefaultSeriesRepositoryTest : DescribeSpec({

    fun sut(baseUrl: HttpUrl? = null): DefaultSeriesRepository = DefaultSeriesRepository(
        ioDispatcher = Dispatchers.Unconfined,
        omnibusHttpClient = OmnibusHttpClient(
            baseUrlProvider = TestServerBaseUrlProvider(baseUrl = baseUrl),
            authTokenProvider = TestAuthTokenProvider(),
            omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
            authClientMetadataInterceptor = AuthClientMetadataInterceptor(TestClientInfo()),
        ),
    )

    /** The members the server's answer [body] reads as. */
    suspend fun membersOf(body: String): List<SeriesMember> = MockWebServer().use { server ->
        server.start()
        server.enqueue(reply(200, body))
        sut(server.url("/")).getSeries(randomLong()).shouldBeTypeOf<SeriesResult.Success>().members
    }

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("reading a series") {

        it("#104 TC-1: maps every book field") {
            val member = randomSeriesMember()

            membersOf(seriesBody(member.toJson())) shouldBe listOf(member)
        }

        context("when a book has no unique identifier") {

            it("#104 TC-2: uses its id as the uuid") {
                val id = randomLong()

                val members = membersOf(seriesBody("""{"id":$id,"unique_identifier":null,"filename":"${randomString()}"}"""))

                members.single().uuid shouldBe id.toString()
            }
        }

        it("#104 TC-3: uses the filename as the title regardless of how the title is absent") {
            val filename = randomString()

            checkAll(Exhaustive.of("\"\"", "\"   \"", "null")) { title ->
                val members = membersOf(seriesBody("""{"filename":"$filename","title":$title}"""))

                members.single().title shouldBe filename
            }
        }

        context("when a book has no cover URL") {

            it("#104 TC-4: says it has no cover") {
                val member = randomSeriesMember().copy(coverUrl = null)

                membersOf(seriesBody(member.toJson())).single().hasCover shouldBe false
            }
        }

        context("when a book has a cover URL") {

            it("#104 TC-4: says it has a cover") {
                val member = randomSeriesMember().copy(coverUrl = "/covers/${randomString()}.jpg")

                membersOf(seriesBody(member.toJson())).single().hasCover shouldBe true
            }
        }

        it("#104 TC-5: sorts the books by their numeric series index") {
            val members = membersOf(
                seriesBody(
                    bookWithIndex("tenth", "10"),
                    bookWithIndex("second", "2"),
                    bookWithIndex("between", "1.5"),
                    bookWithIndex("first", "1"),
                ),
            )

            members.map { it.uuid } shouldBe listOf("first", "between", "second", "tenth")
        }

        it("#104 TC-6: puts missing and non-numeric indexes last, in the server's order") {
            val members = membersOf(
                seriesBody(
                    bookWithIndex("roman", "II"),
                    bookWithIndex("missing", null),
                    bookWithIndex("second", "2"),
                    bookWithIndex("empty", ""),
                    bookWithIndex("first", "1"),
                    bookWithIndex("word", "Omnibus"),
                ),
            )

            members.map { it.uuid } shouldBe listOf("first", "second", "roman", "missing", "empty", "word")
        }

        it("#104 TC-7: keeps equal indexes in the server's order") {
            val members = membersOf(
                seriesBody(
                    bookWithIndex("a", "2"),
                    bookWithIndex("b", "1"),
                    bookWithIndex("c", "2"),
                    bookWithIndex("d", "2"),
                ),
            )

            members.map { it.uuid } shouldBe listOf("b", "a", "c", "d")
        }

        it("#104 TC-8: is an empty success regardless of how the books are absent") {
            checkAll(Exhaustive.of("""{"books":[]}""", "{}")) { body ->
                membersOf(body) shouldBe emptyList()
            }
        }

        it("#104 TC-8: ignores keys it does not know") {
            val member = randomSeriesMember()

            val body = """{"sort":"${randomString()}","reading":{"x":1},"books":[${member.toJson()}]}"""

            membersOf(body) shouldBe listOf(member)
        }

        it("#104 TC-9: asks for the series by its id") {
            MockWebServer().use { server ->
                server.start()
                val id = randomLong()
                server.enqueue(reply(200, "{}"))

                sut(server.url("/")).getSeries(id)

                val request = server.takeRequest()
                request.method shouldBe "GET"
                request.url.encodedPath shouldBe "/api/series/$id"
            }
        }

        it("#104 TC-10, TC-11, TC-12: reports each failure status as its own error") {
            MockWebServer().use { server ->
                server.start()
                checkAll(
                    Exhaustive.of(
                        401 to SeriesError.SessionExpired,
                        404 to SeriesError.NotFound,
                        403 to SeriesError.Http(403),
                        500 to SeriesError.Http(500),
                    ),
                ) { (code, error) ->
                    server.enqueue(reply(code))

                    sut(server.url("/")).getSeries(randomLong()) shouldBe SeriesResult.Failure(error)
                }
            }
        }

        context("when the server cannot be reached") {

            it("#104 TC-13: reports Unreachable") {
                val closed = MockWebServer().use { server ->
                    server.start()
                    server.url("/")
                }

                sut(closed).getSeries(randomLong()) shouldBe SeriesResult.Failure(SeriesError.Unreachable)
            }
        }

        it("#104 TC-14: reports UnexpectedResponse regardless of how the body is unreadable") {
            MockWebServer().use { server ->
                server.start()
                checkAll(Exhaustive.of("not json", "[]", "", """{"books":"nope"}""")) { body ->
                    server.enqueue(reply(200, body))

                    sut(server.url("/")).getSeries(randomLong()) shouldBe
                        SeriesResult.Failure(SeriesError.UnexpectedResponse)
                }
            }
        }

        context("when no server is saved") {

            it("#104 TC-15: reports NoServer") {
                sut().getSeries(randomLong()) shouldBe SeriesResult.NoServer
            }
        }
    }
})
