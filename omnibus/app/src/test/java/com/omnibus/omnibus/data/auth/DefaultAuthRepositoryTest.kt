package com.omnibus.omnibus.data.auth

import android.util.Log
import com.omnibus.omnibus.data.auth.local.FakeAuthTokenStore
import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.OmnibusHttpClient
import com.omnibus.omnibus.data.auth.remote.OmnibusNetworkInterceptor
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.mockk.every
import io.mockk.mockk
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
import okhttp3.Interceptor

class DefaultAuthRepositoryTest : DescribeSpec({

    lateinit var server: MockWebServer
    lateinit var tokenStore: FakeAuthTokenStore
    lateinit var repository: DefaultAuthRepository

    fun enqueue(code: Int, body: String = "") {
        server.enqueue(MockResponse.Builder().code(code).body(body).build())
    }

    beforeSpec {
        mockkStatic(Log::class)
        every { Log.e(any(), any<String>()) } returns 0
    }

    afterSpec {
        unmockkStatic(Log::class)
    }

    beforeEach {
        server = MockWebServer().apply { start() }

        tokenStore = FakeAuthTokenStore()

        // The real interceptor reads Build.MODEL and PackageManager, which aren't available on the JVM.
        val passThroughMetadataInterceptor = mockk<AuthClientMetadataInterceptor> {
            every { intercept(any()) } answers {
                val chain = firstArg<Interceptor.Chain>()
                chain.proceed(chain.request())
            }
        }

        repository = DefaultAuthRepository(
            ioDispatcher = Dispatchers.Unconfined,
            omnibusHttpClient = OmnibusHttpClient(
                baseUrl = server.url("/").toString(),
                omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
                authClientMetadataInterceptor = passThroughMetadataInterceptor,
            ),
            authTokenStore = tokenStore,
        )
    }

    afterEach {
        server.close()
    }

    describe("login") {

        describe("when the server responds 200 with a valid body") {

            beforeEach { enqueue(200, VALID_RESPONSE_BODY) }

            it("returns Authenticated") {
                repository.login(USERNAME, PASSWORD) shouldBe AuthLoginResult.Authenticated
            }

            it("saves the returned token") {
                repository.login(USERNAME, PASSWORD)

                tokenStore.token.first() shouldBe TOKEN
            }

            it("POSTs the credentials as JSON to the login path") {
                repository.login(USERNAME, PASSWORD)

                val request = server.takeRequest()
                request.method shouldBe "POST"
                request.url.encodedPath shouldBe DefaultAuthRepository.LOGIN_PATH
                request.headers["Content-Type"]!! shouldStartWith "application/json"
                request.headers["Accept"] shouldBe "application/json"

                val json = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
                json["username"]!!.jsonPrimitive.content shouldBe USERNAME
                json["password"]!!.jsonPrimitive.content shouldBe PASSWORD
            }
        }

        describe("when the server responds with a non-2xx status") {

            listOf(400, 401, 403, 404, 500, 503).forEach { code ->

                it("returns Unauthenticated for $code without saving a token") {
                    enqueue(code, """{"error":"nope"}""")

                    repository.login(USERNAME, PASSWORD) shouldBe AuthLoginResult.Unauthenticated
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

                    repository.login(USERNAME, PASSWORD) shouldBe AuthLoginResult.Error
                    tokenStore.token.first().shouldBeNull()
                }
            }
        }

        describe("when the server can't be reached") {

            it("returns Error") {
                server.close()

                repository.login(USERNAME, PASSWORD) shouldBe AuthLoginResult.Error
            }
        }

        describe("when saving the token fails") {

            it("returns Error") {
                enqueue(200, VALID_RESPONSE_BODY)
                tokenStore.saveFailure = IOException("disk full")

                repository.login(USERNAME, PASSWORD) shouldBe AuthLoginResult.Error
                tokenStore.token.first().shouldBeNull()
            }
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
