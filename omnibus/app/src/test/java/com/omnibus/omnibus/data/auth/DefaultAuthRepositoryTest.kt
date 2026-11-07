package com.omnibus.omnibus.data.auth

import app.cash.turbine.test
import com.omnibus.omnibus.data.auth.local.StoredAuthTokenProvider
import com.omnibus.omnibus.data.auth.local.TestAuthTokenStore
import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.TestClientInfo
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.data.network.TestServerBaseUrlProvider
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.reply
import com.omnibus.omnibus.testing.unmockAndroidLog
import com.omnibus.omnibus.testing.unreachableUrl
import com.omnibus.omnibus.testing.withMockServer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import io.kotest.property.exhaustive.of
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okhttp3.HttpUrl

private fun droppedMidBody(body: String): MockResponse =
    MockResponse.Builder().code(200).body(body).onResponseBody(SocketEffect.CloseSocket()).build()

private fun userJson(id: Long = randomLong(), extraFields: String = ""): String = """
    {
        "id": $id,
        "username": "${randomString()}",
        "is_admin": false,
        "can_upload": true,
        "can_edit": true,
        "can_download": true
        $extraFields
    }
"""

private fun acceptedBody(token: String = randomString(), user: String = userJson()): String =
    """{"user": $user, "token": "$token"}"""

/** Every 2xx body that doesn't yield a session, by what is wrong with it. */
private fun unusableBodies(): List<String> = listOf(
    """{"token": "abc""",
    "",
    """{"user": ${userJson()}}""",
    """{"user": ${userJson()}, "token": null}""",
    """{"user": ${userJson()}, "token": ""}""",
    """{"user": ${userJson()}, "token": "   "}""",
    """{"token": "${randomString()}"}""",
    """{"user": ${userJson()}, "token": {"value": 1}}""",
)

private fun storeHolding(token: String = randomString()) = TestAuthTokenStore(initialToken = token)

private fun RecordedRequest.jsonField(name: String): String =
    Json.parseToJsonElement(body!!.utf8()).jsonObject[name]!!.jsonPrimitive.content

class DefaultAuthRepositoryTest : DescribeSpec({

    fun sut(
        baseUrl: HttpUrl? = null,
        tokenStore: TestAuthTokenStore = TestAuthTokenStore(),
    ): DefaultAuthRepository = DefaultAuthRepository(
        ioDispatcher = Dispatchers.Unconfined,
        omnibusHttpClient = OmnibusHttpClient(
            baseUrlProvider = TestServerBaseUrlProvider(baseUrl),
            // The real provider, so a 401 handled by the client really clears the store.
            authTokenProvider = StoredAuthTokenProvider(tokenStore),
            omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
            authClientMetadataInterceptor = AuthClientMetadataInterceptor(TestClientInfo()),
        ),
        authTokenStore = tokenStore,
    )

    suspend fun signInAnswering(
        response: MockResponse,
        tokenStore: TestAuthTokenStore = TestAuthTokenStore(),
    ): AuthSignInResult = withMockServer { server ->
        server.enqueue(response)
        sut(server.url("/"), tokenStore).signIn(randomString(), randomString())
    }

    suspend fun registerAnswering(
        response: MockResponse,
        tokenStore: TestAuthTokenStore = TestAuthTokenStore(),
    ): AuthRegisterResult = withMockServer { server ->
        server.enqueue(response)
        sut(server.url("/"), tokenStore).register(randomString(), randomString())
    }

    suspend fun verifyAnswering(
        response: MockResponse,
        tokenStore: TestAuthTokenStore = storeHolding(),
    ): AuthSessionResult = withMockServer { server ->
        server.enqueue(response)
        sut(server.url("/"), tokenStore).verifySession()
    }

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("signing in") {
        context("when the server accepts the credentials") {
            it("TC-1: returns Success with the token for every 2xx status") {
                checkAll(Exhaustive.of(200, 201, 299)) { code ->
                    val token = randomString()

                    signInAnswering(reply(code, acceptedBody(token))) shouldBe AuthSignInResult.Success(token)
                }
            }

            it("TC-1: stores the returned token") {
                val token = randomString()
                val tokenStore = TestAuthTokenStore()

                signInAnswering(reply(200, acceptedBody(token)), tokenStore)

                tokenStore.token.first() shouldBe token
            }

            it("POSTs the credentials as JSON to the login path") {
                withMockServer { server ->
                    val username = randomString()
                    val password = randomString()
                    server.enqueue(reply(200, acceptedBody()))

                    sut(server.url("/")).signIn(username, password)

                    val request = server.takeRequest()
                    request.method shouldBe "POST"
                    request.url.encodedPath shouldBe "/api/auth/login"
                    request.jsonField("username") shouldBe username
                    request.jsonField("password") shouldBe password
                }
            }
        }

        context("when the response carries fields the app doesn't know") {
            it("returns Success") {
                val token = randomString()
                val body = """{"user": ${userJson()}, "token": "$token", "expires_at": null, "share_stats": true}"""

                signInAnswering(reply(200, body)) shouldBe AuthSignInResult.Success(token)
            }
        }

        context("when the server rejects the credentials") {
            it("TC-2: returns InvalidCredentials for 401 and 403") {
                checkAll(Exhaustive.of(401, 403)) { code ->
                    signInAnswering(reply(code)) shouldBe AuthSignInResult.InvalidCredentials
                }
            }
        }

        context("when the server is rate limiting") {
            it("TC-3: returns TooManyAttempts") {
                signInAnswering(reply(429)) shouldBe AuthSignInResult.TooManyAttempts
            }
        }

        context("when the server responds with any other non-2xx status") {
            it("TC-4: returns ServerError for each status") {
                checkAll(Exhaustive.of(400, 404, 500, 503)) { code ->
                    signInAnswering(reply(code)) shouldBe AuthSignInResult.ServerError
                }
            }
        }

        context("when the server responds 200 with a body that can't be used") {
            it("TC-5: returns ServerError for each kind of body") {
                checkAll(Exhaustive.collection(unusableBodies())) { body ->
                    signInAnswering(reply(200, body)) shouldBe AuthSignInResult.ServerError
                }
            }

            it("TC-5: stores no token for each kind of body") {
                checkAll(Exhaustive.collection(unusableBodies())) { body ->
                    val tokenStore = TestAuthTokenStore()

                    signInAnswering(reply(200, body), tokenStore)

                    tokenStore.token.first().shouldBeNull()
                }
            }
        }

        context("when the connection drops mid-body") {
            it("TC-5: returns ServerError") {
                signInAnswering(droppedMidBody(acceptedBody())) shouldBe AuthSignInResult.ServerError
            }
        }

        context("when no server is saved") {
            it("TC-8: returns NoServer") {
                sut().signIn(randomString(), randomString()) shouldBe AuthSignInResult.NoServer
            }
        }

        context("when the server can't be reached") {
            it("TC-7: returns Unreachable") {
                sut(unreachableUrl()).signIn(randomString(), randomString()) shouldBe AuthSignInResult.Unreachable
            }
        }

        context("when saving the token fails") {
            it("TC-6: returns ServerError for an I/O and an unexpected failure") {
                checkAll(Exhaustive.of(IOException("disk full"), IllegalStateException("keystore unavailable"))) { failure ->
                    val tokenStore = TestAuthTokenStore().apply { saveFailure = failure }

                    signInAnswering(reply(200, acceptedBody()), tokenStore) shouldBe AuthSignInResult.ServerError
                }
            }
        }

        context("when saving the token is cancelled") {
            it("TC-9: rethrows the cancellation") {
                val tokenStore = TestAuthTokenStore().apply { saveFailure = CancellationException("cancelled") }

                shouldThrow<CancellationException> { signInAnswering(reply(200, acceptedBody()), tokenStore) }
            }
        }
    }

    describe("registering") {
        context("when the server accepts the registration") {
            it("TC-40: returns Success with the token for every 2xx status") {
                checkAll(Exhaustive.of(200, 201, 299)) { code ->
                    val token = randomString()

                    registerAnswering(reply(code, acceptedBody(token))) shouldBe AuthRegisterResult.Success(token)
                }
            }

            it("TC-40: stores the returned token") {
                val token = randomString()
                val tokenStore = TestAuthTokenStore()

                registerAnswering(reply(200, acceptedBody(token)), tokenStore)

                tokenStore.token.first() shouldBe token
            }

            it("TC-47: POSTs the credentials as JSON to the register path") {
                withMockServer { server ->
                    val username = randomString()
                    val password = randomString()
                    server.enqueue(reply(200, acceptedBody()))

                    sut(server.url("/")).register(username, password)

                    val request = server.takeRequest()
                    request.method shouldBe "POST"
                    request.url.encodedPath shouldBe "/api/auth/register"
                    request.jsonField("username") shouldBe username
                    request.jsonField("password") shouldBe password
                }
            }
        }

        context("when the username is already taken") {
            it("TC-41: returns UsernameTaken") {
                registerAnswering(reply(409)) shouldBe AuthRegisterResult.UsernameTaken
            }
        }

        context("when registration is closed") {
            it("TC-42: returns RegistrationDisabled") {
                registerAnswering(reply(403)) shouldBe AuthRegisterResult.RegistrationDisabled
            }
        }

        context("when the details are rejected") {
            it("TC-43: returns InvalidDetails") {
                registerAnswering(reply(400)) shouldBe AuthRegisterResult.InvalidDetails
            }
        }

        context("when the server is rate limiting") {
            it("TC-44: returns TooManyAttempts") {
                registerAnswering(reply(429)) shouldBe AuthRegisterResult.TooManyAttempts
            }
        }

        context("when the server responds with any other non-2xx status") {
            it("TC-45: returns ServerError for each status") {
                checkAll(Exhaustive.of(401, 404, 500, 503)) { code ->
                    registerAnswering(reply(code)) shouldBe AuthRegisterResult.ServerError
                }
            }
        }

        context("when the server responds 200 with a body that can't be used") {
            it("TC-46: returns ServerError for each kind of body") {
                checkAll(Exhaustive.collection(unusableBodies())) { body ->
                    registerAnswering(reply(200, body)) shouldBe AuthRegisterResult.ServerError
                }
            }

            it("TC-46: stores no token for each kind of body") {
                checkAll(Exhaustive.collection(unusableBodies())) { body ->
                    val tokenStore = TestAuthTokenStore()

                    registerAnswering(reply(200, body), tokenStore)

                    tokenStore.token.first().shouldBeNull()
                }
            }
        }

        context("when the connection drops mid-body") {
            it("TC-46: returns ServerError") {
                registerAnswering(droppedMidBody(acceptedBody())) shouldBe AuthRegisterResult.ServerError
            }
        }

        context("when no server is saved") {
            it("TC-48: returns NoServer") {
                sut().register(randomString(), randomString()) shouldBe AuthRegisterResult.NoServer
            }
        }

        context("when the server can't be reached") {
            it("TC-49: returns Unreachable") {
                sut(unreachableUrl()).register(randomString(), randomString()) shouldBe AuthRegisterResult.Unreachable
            }
        }

        context("when saving the token fails") {
            it("TC-50: returns ServerError for an I/O and an unexpected failure") {
                checkAll(Exhaustive.of(IOException("disk full"), IllegalStateException("keystore unavailable"))) { failure ->
                    val tokenStore = TestAuthTokenStore().apply { saveFailure = failure }

                    registerAnswering(reply(200, acceptedBody()), tokenStore) shouldBe AuthRegisterResult.ServerError
                }
            }
        }

        context("when saving the token is cancelled") {
            it("TC-51: rethrows the cancellation") {
                val tokenStore = TestAuthTokenStore().apply { saveFailure = CancellationException("cancelled") }

                shouldThrow<CancellationException> { registerAnswering(reply(200, acceptedBody()), tokenStore) }
            }
        }
    }

    describe("signing out") {
        it("returns Success") {
            sut().signOut() shouldBe AuthSignOutResult.Success
        }

        it("clears the stored token") {
            val tokenStore = storeHolding()

            sut(tokenStore = tokenStore).signOut()

            tokenStore.token.first().shouldBeNull()
        }

        context("when the token can't be cleared") {
            it("returns Failure") {
                val tokenStore = TestAuthTokenStore().apply { clearFailure = IOException("disk full") }

                sut(tokenStore = tokenStore).signOut() shouldBe AuthSignOutResult.Failure
            }
        }

        context("when clearing the token is cancelled") {
            it("TC-9: rethrows the cancellation") {
                val tokenStore = TestAuthTokenStore().apply { clearFailure = CancellationException("cancelled") }

                shouldThrow<CancellationException> { sut(tokenStore = tokenStore).signOut() }
            }
        }
    }

    describe("observing whether the user is signed in") {
        context("when a token is stored") {
            it("TC-18: is true") {
                sut(tokenStore = storeHolding()).isSignedIn.first() shouldBe true
            }
        }

        context("when no token is stored") {
            it("TC-18: is false") {
                sut().isSignedIn.first() shouldBe false
            }
        }

        context("when the stored token is blank") {
            it("TC-18: is false for empty and whitespace tokens") {
                checkAll(Exhaustive.of("", "   ")) { blank ->
                    sut(tokenStore = storeHolding(blank)).isSignedIn.first() shouldBe false
                }
            }
        }

        context("when reading the token fails") {
            it("TC-19: emits false, then completes") {
                val tokenStore = TestAuthTokenStore().apply { readFailure = IOException("corrupt") }

                sut(tokenStore = tokenStore).isSignedIn.test {
                    awaitItem() shouldBe false
                    awaitComplete()
                }
            }
        }

        context("when the token changes") {
            it("TC-20: follows the token being saved then cleared") {
                val tokenStore = TestAuthTokenStore()

                sut(tokenStore = tokenStore).isSignedIn.test {
                    awaitItem() shouldBe false

                    tokenStore.save(randomString())
                    awaitItem() shouldBe true

                    tokenStore.clear()
                    awaitItem() shouldBe false
                }
            }
        }

        context("when one token replaces another") {
            it("TC-20: stays silent") {
                val tokenStore = storeHolding()

                sut(tokenStore = tokenStore).isSignedIn.test {
                    awaitItem() shouldBe true

                    tokenStore.save(randomString())

                    expectNoEvents()
                }
            }
        }
    }

    describe("verifying the session") {
        context("when no token is stored") {
            it("TC-21: returns NoSession") {
                sut().verifySession() shouldBe AuthSessionResult.NoSession
            }
        }

        context("when the stored token is blank") {
            it("TC-21: returns NoSession") {
                sut(tokenStore = storeHolding("   ")).verifySession() shouldBe AuthSessionResult.NoSession
            }
        }

        context("when reading the token fails") {
            it("TC-21: returns NoSession") {
                val tokenStore = TestAuthTokenStore().apply { readFailure = IOException("corrupt") }

                sut(tokenStore = tokenStore).verifySession() shouldBe AuthSessionResult.NoSession
            }
        }

        context("when reading the token is cancelled") {
            it("TC-25: rethrows the cancellation") {
                val tokenStore = TestAuthTokenStore().apply { readFailure = CancellationException("cancelled") }

                shouldThrow<CancellationException> { sut(tokenStore = tokenStore).verifySession() }
            }
        }

        context("when a token is stored") {
            context("when no server is saved") {
                it("TC-22: returns NoServer") {
                    sut(tokenStore = storeHolding()).verifySession() shouldBe AuthSessionResult.NoServer
                }
            }

            context("when the server accepts the session") {
                it("TC-23: returns Valid, with a body it can read or not") {
                    checkAll(Exhaustive.of(userJson(), "not json")) { body ->
                        verifyAnswering(reply(200, body)) shouldBe AuthSessionResult.Valid
                    }
                }

                it("TC-23: sends GET api/auth/me with the bearer token") {
                    withMockServer { server ->
                        val token = randomString()
                        server.enqueue(reply(200))

                        sut(server.url("/"), storeHolding(token)).verifySession()

                        val request = server.takeRequest()
                        request.method shouldBe "GET"
                        request.url.encodedPath shouldBe "/api/auth/me"
                        request.headers["Authorization"] shouldBe "Bearer $token"
                    }
                }
            }

            context("when the server rejects the session") {
                it("TC-24: returns Rejected") {
                    verifyAnswering(reply(401)) shouldBe AuthSessionResult.Rejected
                }

                it("TC-24: clears the token") {
                    val tokenStore = storeHolding()

                    verifyAnswering(reply(401), tokenStore)

                    tokenStore.token.first().shouldBeNull()
                }
            }

            context("when the server can't be reached") {
                it("TC-25: returns Unreachable") {
                    sut(unreachableUrl(), storeHolding()).verifySession() shouldBe AuthSessionResult.Unreachable
                }
            }

            context("when the server answers with a status other than 401") {
                it("TC-25: returns ServerError for each status") {
                    checkAll(Exhaustive.of(403, 404, 500, 503)) { code ->
                        verifyAnswering(reply(code)) shouldBe AuthSessionResult.ServerError
                    }
                }
            }
        }
    }

    describe("observing the current user") {
        context("when a session check succeeds") {
            it("TC-16: exposes the reported user with lowercase hidden formats") {
                withMockServer { server ->
                    val id = randomLong()
                    server.enqueue(reply(200, userJson(id, """, "hidden_formats": ["CBZ"], "stack_series": true""")))
                    val repository = sut(server.url("/"), storeHolding())

                    repository.verifySession()

                    repository.currentUser.first() shouldBe CurrentUser(
                        id = id,
                        canEdit = true,
                        canUpload = true,
                        canDownload = true,
                        hiddenFormats = setOf("cbz"),
                    )
                }
            }

            it("#93 TC-12: exposes the admin flag, the download permission and the Kindle address") {
                withMockServer { server ->
                    val isAdmin = randomBoolean()
                    val canDownload = randomBoolean()
                    val kindleEmail = "${randomString()}@kindle.com"
                    val body = """
                        {
                            "id": ${randomLong()},
                            "username": "${randomString()}",
                            "is_admin": $isAdmin,
                            "can_upload": ${randomBoolean()},
                            "can_edit": ${randomBoolean()},
                            "can_download": $canDownload,
                            "kindle_email": "$kindleEmail"
                        }
                    """
                    server.enqueue(reply(200, body))
                    val repository = sut(server.url("/"), storeHolding())

                    repository.verifySession()

                    val user = repository.currentUser.first()!!
                    user.isAdmin shouldBe isAdmin
                    user.canDownload shouldBe canDownload
                    user.kindleEmail shouldBe kindleEmail
                }
            }
        }

        context("when a session check body can't be read") {
            it("TC-16: is null") {
                withMockServer { server ->
                    server.enqueue(reply(200, "not json"))
                    val repository = sut(server.url("/"), storeHolding())

                    repository.verifySession()

                    repository.currentUser.first().shouldBeNull()
                }
            }
        }

        context("when registering succeeds") {
            it("TC-16: is the registered user") {
                withMockServer { server ->
                    val id = randomLong()
                    server.enqueue(reply(200, acceptedBody(user = userJson(id))))
                    val repository = sut(server.url("/"))

                    repository.register(randomString(), randomString())

                    repository.currentUser.first()!!.id shouldBe id
                }
            }
        }

        context("when signing in succeeds") {
            it("TC-16: is the signed-in user") {
                withMockServer { server ->
                    val id = randomLong()
                    server.enqueue(reply(200, acceptedBody(user = userJson(id))))
                    val repository = sut(server.url("/"))

                    repository.signIn(randomString(), randomString())

                    repository.currentUser.first()!!.id shouldBe id
                }
            }

            context("when the user signs out") {
                it("TC-16: is null") {
                    withMockServer { server ->
                        server.enqueue(reply(200, acceptedBody()))
                        val repository = sut(server.url("/"))
                        repository.signIn(randomString(), randomString())

                        repository.signOut()

                        repository.currentUser.first().shouldBeNull()
                    }
                }
            }

            context("when the session is rejected") {
                it("TC-16: is null") {
                    withMockServer { server ->
                        server.enqueue(reply(200, acceptedBody()))
                        server.enqueue(reply(401))
                        val repository = sut(server.url("/"))
                        repository.signIn(randomString(), randomString())

                        repository.verifySession()

                        repository.currentUser.first().shouldBeNull()
                    }
                }
            }

            context("when the token is cleared elsewhere") {
                it("TC-16: is null") {
                    withMockServer { server ->
                        val tokenStore = TestAuthTokenStore()
                        server.enqueue(reply(200, acceptedBody()))
                        val repository = sut(server.url("/"), tokenStore)
                        repository.signIn(randomString(), randomString())

                        tokenStore.clear()

                        repository.currentUser.first().shouldBeNull()
                    }
                }
            }
        }
    }
})
