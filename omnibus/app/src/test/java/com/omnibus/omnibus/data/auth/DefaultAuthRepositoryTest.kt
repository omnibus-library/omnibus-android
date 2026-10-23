package com.omnibus.omnibus.data.auth

import com.omnibus.omnibus.data.auth.local.FakeAuthTokenStore
import com.omnibus.omnibus.data.auth.local.StoredAuthTokenProvider
import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.FakeClientInfo
import com.omnibus.omnibus.data.network.FakeServerBaseUrlProvider
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect

class DefaultAuthRepositoryTest : DescribeSpec({

    lateinit var server: MockWebServer
    lateinit var tokenStore: FakeAuthTokenStore
    lateinit var baseUrlProvider: FakeServerBaseUrlProvider
    lateinit var repository: DefaultAuthRepository

    fun enqueue(code: Int, body: String = "") {
        server.enqueue(MockResponse.Builder().code(code).body(body).build())
    }

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    beforeEach {
        server = MockWebServer().apply { start() }

        tokenStore = FakeAuthTokenStore()
        baseUrlProvider = FakeServerBaseUrlProvider(baseUrl = server.url("/"))

        repository = DefaultAuthRepository(
            ioDispatcher = Dispatchers.Unconfined,
            omnibusHttpClient = OmnibusHttpClient(
                baseUrlProvider = baseUrlProvider,
                // The real provider, so a 401 handled by the client really clears the store.
                authTokenProvider = StoredAuthTokenProvider(tokenStore),
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

        describe("when the server responds 2xx with a valid body") {

            beforeEach { enqueue(200, VALID_RESPONSE_BODY) }

            it("TC-1: returns Success with the token") {
                repository.signIn(USERNAME, PASSWORD) shouldBe AuthSignInResult.Success(TOKEN)
            }

            it("TC-1: saves the returned token") {
                repository.signIn(USERNAME, PASSWORD)

                tokenStore.token.first() shouldBe TOKEN
            }

            it("TC-1: replaces a previously stored token") {
                tokenStore.save("stale")

                repository.signIn(USERNAME, PASSWORD) shouldBe AuthSignInResult.Success(TOKEN)
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

        it("TC-1: treats any 2xx as success, e.g. 201") {
            enqueue(201, VALID_RESPONSE_BODY)

            repository.signIn(USERNAME, PASSWORD) shouldBe AuthSignInResult.Success(TOKEN)
            tokenStore.token.first() shouldBe TOKEN
        }

        describe("when the server rejects the credentials") {

            listOf(401, 403).forEach { code ->

                it("TC-2: returns InvalidCredentials for $code without saving a token") {
                    enqueue(code, """{"error":"nope"}""")

                    repository.signIn(USERNAME, PASSWORD) shouldBe AuthSignInResult.InvalidCredentials
                    tokenStore.token.first().shouldBeNull()
                }
            }
        }

        describe("when the server is rate limiting") {

            it("TC-3: returns TooManyAttempts for 429 without saving a token") {
                enqueue(429, """{"error":"slow down"}""")

                repository.signIn(USERNAME, PASSWORD) shouldBe AuthSignInResult.TooManyAttempts
                tokenStore.token.first().shouldBeNull()
            }
        }

        describe("when the server responds with any other non-2xx status") {

            listOf(400, 404, 500, 502, 503).forEach { code ->

                it("TC-4: returns ServerError for $code without saving a token") {
                    enqueue(code, """{"error":"nope"}""")

                    repository.signIn(USERNAME, PASSWORD) shouldBe AuthSignInResult.ServerError
                    tokenStore.token.first().shouldBeNull()
                }
            }
        }

        describe("when the server responds 200 with a body that can't be used") {

            mapOf(
                "malformed JSON" to """{"token": "abc""",
                "an empty body" to "",
                "a missing token field" to """{"user": $VALID_USER_JSON}""",
                "a null token" to """{"user": $VALID_USER_JSON, "token": null}""",
                "a blank token" to """{"user": $VALID_USER_JSON, "token": ""}""",
                "a whitespace-only token" to """{"user": $VALID_USER_JSON, "token": "   "}""",
                "a missing user field" to """{"token": "$TOKEN"}""",
                "a token of the wrong type" to """{"user": $VALID_USER_JSON, "token": {"value": 1}}""",
            ).forEach { (description, body) ->

                it("TC-5: returns ServerError for $description without saving a token") {
                    enqueue(200, body)

                    repository.signIn(USERNAME, PASSWORD) shouldBe AuthSignInResult.ServerError
                    tokenStore.token.first().shouldBeNull()
                }
            }

            it("TC-5: returns ServerError, not Unreachable, when the connection drops mid-body") {
                server.enqueue(
                    MockResponse.Builder()
                        .code(200)
                        .body(VALID_RESPONSE_BODY)
                        .onResponseBody(SocketEffect.CloseSocket())
                        .build(),
                )

                repository.signIn(USERNAME, PASSWORD) shouldBe AuthSignInResult.ServerError
                tokenStore.token.first().shouldBeNull()
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

            it("TC-8: returns NoServer without making a request") {
                baseUrlProvider.baseUrl = null

                repository.signIn(USERNAME, PASSWORD) shouldBe AuthSignInResult.NoServer
                server.requestCount shouldBe 0
                tokenStore.token.first().shouldBeNull()
            }
        }

        describe("when the server can't be reached") {

            it("TC-7: returns Unreachable without saving a token") {
                server.close()

                repository.signIn(USERNAME, PASSWORD) shouldBe AuthSignInResult.Unreachable
                tokenStore.token.first().shouldBeNull()
            }
        }

        describe("when saving the token fails") {

            it("TC-6: returns ServerError without a token being stored") {
                enqueue(200, VALID_RESPONSE_BODY)
                tokenStore.saveFailure = IOException("disk full")

                repository.signIn(USERNAME, PASSWORD) shouldBe AuthSignInResult.ServerError
                tokenStore.token.first().shouldBeNull()
            }

            it("TC-6: returns ServerError for an unexpected runtime failure too") {
                enqueue(200, VALID_RESPONSE_BODY)
                tokenStore.saveFailure = IllegalStateException("keystore unavailable")

                repository.signIn(USERNAME, PASSWORD) shouldBe AuthSignInResult.ServerError
                tokenStore.token.first().shouldBeNull()
            }
        }

        describe("when the coroutine is cancelled") {

            it("TC-9: rethrows CancellationException from saving the token instead of returning a result") {
                enqueue(200, VALID_RESPONSE_BODY)
                tokenStore.saveFailure = CancellationException("cancelled")

                shouldThrow<CancellationException> { repository.signIn(USERNAME, PASSWORD) }
                tokenStore.token.first().shouldBeNull()
            }
        }

        describe("when a token is already stored") {

            listOf(401, 403).forEach { code ->
                it("TC-14: a $code from login returns InvalidCredentials and does not clear the stored token") {
                    tokenStore.save(TOKEN)
                    enqueue(code)

                    repository.signIn(USERNAME, PASSWORD) shouldBe AuthSignInResult.InvalidCredentials

                    tokenStore.clearCalls shouldBe 0
                    tokenStore.token.first() shouldBe TOKEN
                }
            }
        }
    }

    describe("register") {

        describe("when the server responds 2xx with a valid body") {

            beforeEach { enqueue(200, VALID_RESPONSE_BODY) }

            it("TC-40: returns Success with the token") {
                repository.register(USERNAME, PASSWORD) shouldBe AuthRegisterResult.Success(TOKEN)
            }

            it("TC-40: saves the returned token") {
                repository.register(USERNAME, PASSWORD)

                tokenStore.token.first() shouldBe TOKEN
            }

            it("TC-40: replaces a previously stored token") {
                tokenStore.save("stale")

                repository.register(USERNAME, PASSWORD) shouldBe AuthRegisterResult.Success(TOKEN)
                tokenStore.token.first() shouldBe TOKEN
            }

            it("TC-40: does not follow registration with a sign-in request") {
                enqueue(200, VALID_RESPONSE_BODY)

                repository.register(USERNAME, PASSWORD) shouldBe AuthRegisterResult.Success(TOKEN)

                server.requestCount shouldBe 1
                server.takeRequest().url.encodedPath shouldBe "/api/auth/register"
            }
        }

        it("TC-40: treats any 2xx as success, e.g. 201") {
            enqueue(201, VALID_RESPONSE_BODY)

            repository.register(USERNAME, PASSWORD) shouldBe AuthRegisterResult.Success(TOKEN)
            tokenStore.token.first() shouldBe TOKEN
        }

        describe("when the username is already taken") {

            it("TC-41: returns UsernameTaken for 409 without saving a token") {
                enqueue(409, """{"error":"username taken"}""")

                repository.register(USERNAME, PASSWORD) shouldBe AuthRegisterResult.UsernameTaken
                tokenStore.token.first().shouldBeNull()
            }
        }

        describe("when registration is closed") {

            it("TC-42: returns RegistrationDisabled for 403 without saving a token") {
                enqueue(403, """{"error":"registration disabled"}""")

                repository.register(USERNAME, PASSWORD) shouldBe AuthRegisterResult.RegistrationDisabled
                tokenStore.token.first().shouldBeNull()
            }
        }

        describe("when the username or password is rejected") {

            it("TC-43: returns InvalidDetails for 400 without saving a token") {
                enqueue(400, """{"error":"password"}""")

                repository.register(USERNAME, PASSWORD) shouldBe AuthRegisterResult.InvalidDetails
                tokenStore.token.first().shouldBeNull()
            }

            it("TC-43: still posts a password shorter than 10 characters") {
                val shortPassword = "short"
                enqueue(400, """{"error":"password"}""")

                repository.register(USERNAME, shortPassword) shouldBe AuthRegisterResult.InvalidDetails
                server.requestCount shouldBe 1

                val json = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
                json["password"]!!.jsonPrimitive.content shouldBe shortPassword
            }
        }

        describe("when the server is rate limiting") {

            it("TC-44: returns TooManyAttempts for 429 without saving a token") {
                enqueue(429, """{"error":"slow down"}""")

                repository.register(USERNAME, PASSWORD) shouldBe AuthRegisterResult.TooManyAttempts
                tokenStore.token.first().shouldBeNull()
            }
        }

        describe("when the server responds with any other non-2xx status") {

            listOf(401, 404, 500, 502, 503).forEach { code ->

                it("TC-45: returns ServerError for $code without saving a token") {
                    enqueue(code, """{"error":"nope"}""")

                    repository.register(USERNAME, PASSWORD) shouldBe AuthRegisterResult.ServerError
                    tokenStore.token.first().shouldBeNull()
                }
            }
        }

        describe("when the server responds 200 with a body that can't be used") {

            mapOf(
                "malformed JSON" to """{"token": "abc""",
                "an empty body" to "",
                "a missing token field" to """{"user": $VALID_USER_JSON}""",
                "a null token" to """{"user": $VALID_USER_JSON, "token": null}""",
                "a blank token" to """{"user": $VALID_USER_JSON, "token": ""}""",
                "a whitespace-only token" to """{"user": $VALID_USER_JSON, "token": "   "}""",
                "a missing user field" to """{"token": "$TOKEN"}""",
                "a token of the wrong type" to """{"user": $VALID_USER_JSON, "token": {"value": 1}}""",
            ).forEach { (description, body) ->

                it("TC-46: returns ServerError for $description without saving a token") {
                    enqueue(200, body)

                    repository.register(USERNAME, PASSWORD) shouldBe AuthRegisterResult.ServerError
                    tokenStore.token.first().shouldBeNull()
                }
            }

            it("TC-46: returns ServerError, not Unreachable, when the connection drops mid-body") {
                server.enqueue(
                    MockResponse.Builder()
                        .code(200)
                        .body(VALID_RESPONSE_BODY)
                        .onResponseBody(SocketEffect.CloseSocket())
                        .build(),
                )

                repository.register(USERNAME, PASSWORD) shouldBe AuthRegisterResult.ServerError
                tokenStore.token.first().shouldBeNull()
            }
        }

        describe("when the saved server has a path prefix") {

            it("TC-47: POSTs beneath the prefix") {
                baseUrlProvider.baseUrl = server.url("/omnibus")
                enqueue(200, VALID_RESPONSE_BODY)

                repository.register(USERNAME, PASSWORD) shouldBe AuthRegisterResult.Success(TOKEN)
                server.takeRequest().url.encodedPath shouldBe "/omnibus/api/auth/register"
            }
        }

        describe("when the server responds 2xx") {

            beforeEach { enqueue(200, VALID_RESPONSE_BODY) }

            it("TC-47: POSTs the credentials and client metadata as JSON to the register path") {
                repository.register(USERNAME, PASSWORD)

                val request = server.takeRequest()
                request.method shouldBe "POST"
                request.url.encodedPath shouldBe "/api/auth/register"
                request.headers["Content-Type"]!! shouldStartWith "application/json"
                request.headers["Accept"] shouldBe "application/json"

                val json = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
                json["username"]!!.jsonPrimitive.content shouldBe USERNAME
                json["password"]!!.jsonPrimitive.content shouldBe PASSWORD
                json["client_kind"]!!.jsonPrimitive.content shouldBe "android"
                json["device_name"]!!.jsonPrimitive.content shouldBe "Pixel 9"
                json["client_version"]!!.jsonPrimitive.content shouldBe "1.2.3"
            }
        }

        describe("when no server is saved") {

            it("TC-48: returns NoServer without making a request") {
                baseUrlProvider.baseUrl = null

                repository.register(USERNAME, PASSWORD) shouldBe AuthRegisterResult.NoServer
                server.requestCount shouldBe 0
                tokenStore.token.first().shouldBeNull()
            }

            it("TC-48: does not clear a stored token when no server is saved") {
                tokenStore.save(TOKEN)
                baseUrlProvider.baseUrl = null

                repository.register(USERNAME, PASSWORD) shouldBe AuthRegisterResult.NoServer

                server.requestCount shouldBe 0
                tokenStore.clearCalls shouldBe 0
                tokenStore.token.first() shouldBe TOKEN
            }
        }

        describe("when the server can't be reached") {

            it("TC-49: returns Unreachable without saving a token") {
                server.close()

                repository.register(USERNAME, PASSWORD) shouldBe AuthRegisterResult.Unreachable
                tokenStore.token.first().shouldBeNull()
            }
        }

        describe("when saving the token fails") {

            it("TC-50: returns ServerError without a token being stored") {
                enqueue(200, VALID_RESPONSE_BODY)
                tokenStore.saveFailure = IOException("disk full")

                repository.register(USERNAME, PASSWORD) shouldBe AuthRegisterResult.ServerError
                tokenStore.token.first().shouldBeNull()
            }

            it("TC-50: returns ServerError for an unexpected runtime failure too") {
                enqueue(200, VALID_RESPONSE_BODY)
                tokenStore.saveFailure = IllegalStateException("keystore unavailable")

                repository.register(USERNAME, PASSWORD) shouldBe AuthRegisterResult.ServerError
                tokenStore.token.first().shouldBeNull()
            }
        }

        describe("when the coroutine is cancelled") {

            it("TC-51: rethrows CancellationException from saving the token instead of returning a result") {
                enqueue(200, VALID_RESPONSE_BODY)
                tokenStore.saveFailure = CancellationException("cancelled")

                shouldThrow<CancellationException> { repository.register(USERNAME, PASSWORD) }
                tokenStore.token.first().shouldBeNull()
            }

            it("TC-51: does not clear an existing token when saving is cancelled") {
                tokenStore.save(TOKEN)
                enqueue(200, VALID_RESPONSE_BODY)
                tokenStore.saveFailure = CancellationException("cancelled")

                shouldThrow<CancellationException> { repository.register(USERNAME, PASSWORD) }
                tokenStore.clearCalls shouldBe 0
                tokenStore.token.first() shouldBe TOKEN
            }
        }

        describe("when a token is already stored") {

            listOf(
                409 to AuthRegisterResult.UsernameTaken,
                403 to AuthRegisterResult.RegistrationDisabled,
                400 to AuthRegisterResult.InvalidDetails,
                429 to AuthRegisterResult.TooManyAttempts,
                500 to AuthRegisterResult.ServerError,
            ).forEach { (code, expected) ->

                it("TC-52: a $code returns $expected and does not clear the stored token") {
                    tokenStore.save(TOKEN)
                    enqueue(code, """{"error":"nope"}""")

                    repository.register(USERNAME, PASSWORD) shouldBe expected

                    tokenStore.clearCalls shouldBe 0
                    tokenStore.token.first() shouldBe TOKEN
                }
            }

            it("TC-52: an unreachable server does not clear the stored token") {
                tokenStore.save(TOKEN)
                server.close()

                repository.register(USERNAME, PASSWORD) shouldBe AuthRegisterResult.Unreachable

                tokenStore.clearCalls shouldBe 0
                tokenStore.token.first() shouldBe TOKEN
            }

            listOf(
                "a blank token" to """{"user": $VALID_USER_JSON, "token": ""}""",
                "a null token" to """{"user": $VALID_USER_JSON, "token": null}""",
                "malformed JSON" to """{"token": "abc""",
            ).forEach { (description, body) ->

                it("TC-52: $description does not replace or clear the stored token") {
                    tokenStore.save(TOKEN)
                    enqueue(200, body)

                    repository.register(USERNAME, PASSWORD) shouldBe AuthRegisterResult.ServerError

                    tokenStore.clearCalls shouldBe 0
                    tokenStore.token.first() shouldBe TOKEN
                }
            }

            it("TC-52: a failed save does not clear the stored token") {
                tokenStore.save(TOKEN)
                enqueue(200, VALID_RESPONSE_BODY)
                tokenStore.saveFailure = IOException("disk full")

                repository.register(USERNAME, PASSWORD) shouldBe AuthRegisterResult.ServerError

                tokenStore.clearCalls shouldBe 0
                tokenStore.token.first() shouldBe TOKEN
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

        it("TC-9: rethrows CancellationException from clearing the token instead of returning Failure") {
            tokenStore.save(TOKEN)
            tokenStore.clearFailure = CancellationException("cancelled")

            shouldThrow<CancellationException> { repository.signOut() }
        }
    }

    describe("isSignedIn") {

        it("TC-18: is true when a token is stored") {
            tokenStore.save(TOKEN)

            repository.isSignedIn.first() shouldBe true
        }

        it("TC-18: is false when no token is stored") {
            repository.isSignedIn.first() shouldBe false
        }

        listOf("", "   ").forEach { blank ->
            it("TC-18: is false when the stored token is \"$blank\"") {
                tokenStore.save(blank)

                repository.isSignedIn.first() shouldBe false
            }
        }

        it("TC-19: is false, and the flow still completes, when reading the token fails") {
            tokenStore.save(TOKEN)
            tokenStore.readFailure = IOException("corrupt")

            repository.isSignedIn.toList() shouldBe listOf(false)
        }

        it("TC-20: follows the token being saved and cleared, without repeating a value") {
            val emissions = mutableListOf<Boolean>()
            val scope = CoroutineScope(Dispatchers.Unconfined)
            scope.launch { repository.isSignedIn.toList(emissions) }

            emissions shouldBe listOf(false)

            tokenStore.save(TOKEN)
            emissions shouldBe listOf(false, true)

            tokenStore.save("rotated")
            emissions shouldBe listOf(false, true)

            tokenStore.clear()
            emissions shouldBe listOf(false, true, false)

            tokenStore.save("   ")
            emissions shouldBe listOf(false, true, false)

            scope.cancel()
        }
    }

    describe("verifySession") {

        describe("when no token is stored") {

            it("TC-21: returns NoSession without making a request") {
                repository.verifySession() shouldBe AuthSessionResult.NoSession

                server.requestCount shouldBe 0
            }

            it("TC-21: returns NoSession for a blank token without making a request") {
                tokenStore.save("   ")

                repository.verifySession() shouldBe AuthSessionResult.NoSession

                server.requestCount shouldBe 0
            }

            it("TC-21: returns NoSession when reading the token fails with an IOException") {
                tokenStore.save(TOKEN)
                tokenStore.readFailure = IOException("corrupt")

                repository.verifySession() shouldBe AuthSessionResult.NoSession

                server.requestCount shouldBe 0
            }
        }

        describe("when no server is saved") {

            it("TC-22: returns NoServer without making a request") {
                tokenStore.save(TOKEN)
                baseUrlProvider.baseUrl = null

                repository.verifySession() shouldBe AuthSessionResult.NoServer

                server.requestCount shouldBe 0
                tokenStore.token.first() shouldBe TOKEN
            }
        }

        describe("when the server accepts the session") {

            beforeEach { tokenStore.save(TOKEN) }

            it("TC-23: returns Valid and keeps the token") {
                enqueue(200, """{"id": 1}""")

                repository.verifySession() shouldBe AuthSessionResult.Valid

                tokenStore.token.first() shouldBe TOKEN
            }

            it("TC-23: sends GET api/auth/me with the bearer token") {
                enqueue(200)

                repository.verifySession()

                val request = server.takeRequest()
                request.method shouldBe "GET"
                request.url.encodedPath shouldBe "/api/auth/me"
                request.headers["Authorization"] shouldBe "Bearer $TOKEN"
            }

            it("TC-23: requests beneath a server path prefix") {
                baseUrlProvider.baseUrl = server.url("/omnibus")
                enqueue(200)

                repository.verifySession() shouldBe AuthSessionResult.Valid

                server.takeRequest().url.encodedPath shouldBe "/omnibus/api/auth/me"
            }

            it("TC-23: doesn't depend on the response body") {
                enqueue(200, "not json")

                repository.verifySession() shouldBe AuthSessionResult.Valid
            }
        }

        describe("when the server rejects the session") {

            it("TC-24: returns Rejected and the token is cleared, exactly once") {
                tokenStore.save(TOKEN)
                enqueue(401)

                repository.verifySession() shouldBe AuthSessionResult.Rejected

                tokenStore.token.first().shouldBeNull()
                tokenStore.clearCalls shouldBe 1
            }
        }

        describe("when the outcome is not a verdict on the session") {

            beforeEach { tokenStore.save(TOKEN) }

            it("TC-25: returns Unreachable when the server can't be reached, and keeps the token") {
                server.close()

                repository.verifySession() shouldBe AuthSessionResult.Unreachable

                tokenStore.token.first() shouldBe TOKEN
                tokenStore.clearCalls shouldBe 0
            }

            listOf(403, 404, 500, 503).forEach { code ->
                it("TC-25: returns ServerError for $code, and keeps the token") {
                    enqueue(code)

                    repository.verifySession() shouldBe AuthSessionResult.ServerError

                    tokenStore.token.first() shouldBe TOKEN
                    tokenStore.clearCalls shouldBe 0
                }
            }

            it("TC-25: rethrows CancellationException instead of returning a result") {
                tokenStore.readFailure = CancellationException("cancelled")

                shouldThrow<CancellationException> { repository.verifySession() }
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
