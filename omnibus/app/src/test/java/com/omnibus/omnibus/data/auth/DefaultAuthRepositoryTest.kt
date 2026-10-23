package com.omnibus.omnibus.data.auth

import android.util.Log
import com.omnibus.omnibus.data.auth.local.FakeAuthTokenStore
import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.FakeClientInfo
import com.omnibus.omnibus.data.network.FakeServerBaseUrlProvider
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer

class DefaultAuthRepositoryTest : DescribeSpec({

    lateinit var server: MockWebServer
    lateinit var tokenStore: FakeAuthTokenStore
    lateinit var baseUrlProvider: FakeServerBaseUrlProvider
    lateinit var repository: DefaultAuthRepository

    fun enqueue(code: Int, body: String = "") {
        server.enqueue(MockResponse.Builder().code(code).body(body).build())
    }

    beforeSpec {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }

    afterSpec {
        unmockkStatic(Log::class)
    }

    beforeEach {
        server = MockWebServer().apply { start() }

        tokenStore = FakeAuthTokenStore()
        baseUrlProvider = FakeServerBaseUrlProvider(baseUrl = server.url("/"))

        repository = DefaultAuthRepository(
            ioDispatcher = Dispatchers.Unconfined,
            omnibusHttpClient = OmnibusHttpClient(
                baseUrlProvider = baseUrlProvider,
                omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
                authClientMetadataInterceptor = AuthClientMetadataInterceptor(FakeClientInfo()),
            ),
            authTokenStore = tokenStore,
        )
    }

    afterEach {
        server.close()
    }

    describe("signIn") {

        describe("when the server responds 200 with a valid body") {

            beforeEach { enqueue(200, VALID_RESPONSE_BODY) }

            it("returns Success with the token") {
                repository.signIn(USERNAME, PASSWORD) shouldBe AuthSignInResult.Success(TOKEN)
            }

            it("saves the returned token") {
                repository.signIn(USERNAME, PASSWORD)

                tokenStore.token.first() shouldBe TOKEN
            }

            it("POSTs the credentials and client metadata as JSON to the login path") {
                repository.signIn(USERNAME, PASSWORD)

                val request = server.takeRequest()
                request.method shouldBe "POST"
                request.url.encodedPath shouldBe "/api/auth/login"
                request.headers["Content-Type"]!! shouldStartWith "application/json"
                request.headers["Accept"] shouldBe "application/json"

                val json = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
                json["username"]!!.jsonPrimitive.content shouldBe USERNAME
                json["password"]!!.jsonPrimitive.content shouldBe PASSWORD
                json["client_kind"]!!.jsonPrimitive.content shouldBe "android"
            }
        }

        describe("when the server responds with a non-2xx status") {

            listOf(400, 401, 403, 404, 500, 503).forEach { code ->

                it("returns Failure for $code without saving a token") {
                    enqueue(code, """{"error":"nope"}""")

                    repository.signIn(USERNAME, PASSWORD) shouldBe AuthSignInResult.Failure
                    tokenStore.token.first().shouldBeNull()
                }
            }
        }

        describe("when the server responds 200 with a body that can't be parsed") {

            mapOf(
                "malformed JSON" to """{"token": "abc""",
                "an empty body" to "",
                "a missing token field" to """{"user": $VALID_USER_JSON}""",
                "a missing user field" to """{"token": "$TOKEN"}""",
                "a token of the wrong type" to """{"user": $VALID_USER_JSON, "token": {"value": 1}}""",
            ).forEach { (description, body) ->

                it("returns Error for $description without saving a token") {
                    enqueue(200, body)

                    repository.signIn(USERNAME, PASSWORD) shouldBe AuthSignInResult.Error
                    tokenStore.token.first().shouldBeNull()
                }
            }
        }

        describe("when the saved server has a path prefix") {

            it("POSTs beneath the prefix") {
                baseUrlProvider.baseUrl = server.url("/omnibus")
                enqueue(200, VALID_RESPONSE_BODY)

                repository.signIn(USERNAME, PASSWORD) shouldBe AuthSignInResult.Success(TOKEN)
                server.takeRequest().url.encodedPath shouldBe "/omnibus/api/auth/login"
            }
        }

        describe("when no server is saved") {

            it("returns NoServer without making a request") {
                baseUrlProvider.baseUrl = null

                repository.signIn(USERNAME, PASSWORD) shouldBe AuthSignInResult.NoServer
                server.requestCount shouldBe 0
                tokenStore.token.first().shouldBeNull()
            }
        }

        describe("when the server can't be reached") {

            it("returns Error") {
                server.close()

                repository.signIn(USERNAME, PASSWORD) shouldBe AuthSignInResult.Error
            }
        }

        describe("when saving the token fails") {

            it("returns Error") {
                enqueue(200, VALID_RESPONSE_BODY)
                tokenStore.saveFailure = IOException("disk full")

                repository.signIn(USERNAME, PASSWORD) shouldBe AuthSignInResult.Error
                tokenStore.token.first().shouldBeNull()
            }
        }
    }

    describe("signOut") {

        it("clears the stored token and returns Success") {
            tokenStore.save(TOKEN)

            repository.signOut() shouldBe AuthSignOutResult.Success
            tokenStore.token.first().shouldBeNull()
        }

        it("returns Failure when the token can't be cleared") {
            tokenStore.save(TOKEN)
            tokenStore.clearFailure = IOException("disk full")

            repository.signOut() shouldBe AuthSignOutResult.Failure
            tokenStore.token.first() shouldBe TOKEN
        }
    }
}) {
    private companion object {
        const val USERNAME = "hunter"
        const val PASSWORD = "hunter2"
        const val TOKEN = "abc123"

        const val VALID_USER_JSON = """
            {
                "id": 1,
                "username": "$USERNAME",
                "is_admin": false,
                "can_upload": true,
                "can_edit": true,
                "can_download": true
            }
        """

        const val VALID_RESPONSE_BODY = """{"user": $VALID_USER_JSON, "token": "$TOKEN"}"""
    }
}
