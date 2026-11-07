package com.omnibus.omnibus.data.household

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.TestClientInfo
import com.omnibus.omnibus.data.network.TestAuthTokenProvider
import com.omnibus.omnibus.data.network.TestServerBaseUrlProvider
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.HttpUrl

private fun reply(code: Int, body: String = ""): MockResponse = MockResponse.Builder().code(code).body(body).build()

class DefaultHouseholdRepositoryTest : DescribeSpec({

    lateinit var server: MockWebServer

    fun repositoryFor(baseUrl: HttpUrl? = server.url("/")) = DefaultHouseholdRepository(
        ioDispatcher = Dispatchers.Unconfined,
        omnibusHttpClient = OmnibusHttpClient(
            baseUrlProvider = TestServerBaseUrlProvider(baseUrl = baseUrl),
            authTokenProvider = TestAuthTokenProvider(token = "secret"),
            omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
            authClientMetadataInterceptor = AuthClientMetadataInterceptor(TestClientInfo()),
        ),
    )

    beforeSpec { mockAndroidLog() }
    afterSpec { unmockAndroidLog() }
    beforeEach { server = MockWebServer().apply { start() } }
    afterEach { server.close() }

    describe("reading the household") {

        it("TC-21: maps each reader, turning last_session_at seconds into millis") {
            server.enqueue(
                reply(
                    200,
                    """[{"id":3,"name":"Maren","has_avatar":true,"seconds":22320,"sessions":4,"last_session_at":1761000000},
                        {"id":4,"name":"Theo","has_avatar":false,"seconds":7260,"sessions":2,"last_session_at":1761775445}]""",
                ),
            )

            repositoryFor().getHousehold("book-1") shouldBe HouseholdResult.Success(
                listOf(
                    HouseholdReader(3, "Maren", true, 22_320, 4, 1_761_000_000_000L),
                    HouseholdReader(4, "Theo", false, 7_260, 2, 1_761_775_445_000L),
                ),
            )
        }

        it("TC-22: asks GET api/books/{uuid}/household with the bearer token") {
            server.enqueue(reply(200, "[]"))

            repositoryFor().getHousehold("book-1")

            val request = checkNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            request.method shouldBe "GET"
            request.url.encodedPath shouldBe "/api/books/book-1/household"
            request.headers["Authorization"] shouldBe "Bearer secret"
        }

        it("TC-23: an empty list is a success with no readers") {
            server.enqueue(reply(200, "[]"))

            repositoryFor().getHousehold("book-1") shouldBe HouseholdResult.Success(emptyList())
        }

        it("TC-24: a row without an id or a name is dropped, and unknown keys are ignored") {
            server.enqueue(
                reply(
                    200,
                    """[{"name":"No id","seconds":60},{"id":5,"name":"  ","seconds":60},
                        {"id":6,"name":"Priya","seconds":90,"sessions":1,"last_session_at":0,"extra":true}]""",
                ),
            )

            repositoryFor().getHousehold("book-1") shouldBe HouseholdResult.Success(
                listOf(HouseholdReader(6, "Priya", false, 90, 1, null)),
            )
        }

        it("TC-25: a 404 is NotFound, other errors and bad bodies are Failed, and nothing throws") {
            val repository = repositoryFor()
            server.enqueue(reply(404))
            repository.getHousehold("book-1") shouldBe HouseholdResult.NotFound

            server.enqueue(reply(500))
            repository.getHousehold("book-1") shouldBe HouseholdResult.Failed

            server.enqueue(reply(200, "not json"))
            repository.getHousehold("book-1") shouldBe HouseholdResult.Failed

            server.enqueue(reply(200, "{\"id\":1}"))
            repository.getHousehold("book-1") shouldBe HouseholdResult.Failed
        }

        it("TC-26: an unreachable server is Failed") {
            server.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build())

            repositoryFor().getHousehold("book-1") shouldBe HouseholdResult.Failed
        }

        it("TC-27: a blank uuid or no saved server makes no request and is NotFound") {
            repositoryFor().getHousehold(" ") shouldBe HouseholdResult.NotFound
            repositoryFor(baseUrl = null).getHousehold("book-1") shouldBe HouseholdResult.NotFound
            server.requestCount shouldBe 0
        }
    }

    describe("loading an avatar") {

        it("TC-28: a failed request or a body that is not an image gives no avatar") {
            val repository = repositoryFor()
            server.enqueue(reply(404))
            repository.getAvatar(3, 64) shouldBe null

            val request = checkNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            request.url.encodedPath shouldBe "/api/users/3/avatar"
        }

        it("TC-29: no saved server gives no avatar") {
            repositoryFor(baseUrl = null).getAvatar(3, 64) shouldBe null
        }
    }
})
