package com.omnibus.omnibus.data.auth

import app.cash.turbine.test
import com.omnibus.omnibus.data.auth.local.StoredAuthTokenProvider
import com.omnibus.omnibus.data.auth.local.TestAuthTokenStore
import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.TestClientInfo
import com.omnibus.omnibus.data.libraries.TestActiveLibrary
import com.omnibus.omnibus.data.libraries.TestLibrariesRepository
import com.omnibus.omnibus.data.libraries.local.LibraryTokenStore
import com.omnibus.omnibus.data.libraries.local.TestLibraryTokenStore
import com.omnibus.omnibus.data.libraries.randomLibrary
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
import io.kotest.matchers.collections.shouldBeEmpty
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

private fun userJson(id: Long = randomLong(), extraFields: String = "", username: String = randomString()): String = """
    {
        "id": $id,
        "username": "$username",
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
        libraries: TestLibrariesRepository = TestLibrariesRepository(),
        activeLibrary: TestActiveLibrary = TestActiveLibrary(),
        libraryTokens: LibraryTokenStore = TestLibraryTokenStore(),
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
        libraries = libraries,
        activeLibrary = activeLibrary,
        libraryTokens = libraryTokens,
    )

    suspend fun signInAnswering(
        response: MockResponse,
        tokenStore: TestAuthTokenStore = TestAuthTokenStore(),
        libraries: TestLibrariesRepository = TestLibrariesRepository(),
        activeLibrary: TestActiveLibrary = TestActiveLibrary(),
    ): AuthSignInResult = withMockServer { server ->
        server.enqueue(response)
        sut(server.url("/"), tokenStore, libraries, activeLibrary).signIn(randomString(), randomString())
    }

    suspend fun registerAnswering(
        response: MockResponse,
        tokenStore: TestAuthTokenStore = TestAuthTokenStore(),
        libraries: TestLibrariesRepository = TestLibrariesRepository(),
        activeLibrary: TestActiveLibrary = TestActiveLibrary(),
    ): AuthRegisterResult = withMockServer { server ->
        server.enqueue(response)
        sut(server.url("/"), tokenStore, libraries, activeLibrary).register(randomString(), randomString())
    }

    suspend fun verifyAnswering(
        response: MockResponse,
        tokenStore: TestAuthTokenStore = storeHolding(),
        libraries: TestLibrariesRepository = TestLibrariesRepository(),
        activeLibrary: TestActiveLibrary = TestActiveLibrary(),
    ): AuthSessionResult = withMockServer { server ->
        server.enqueue(response)
        sut(server.url("/"), tokenStore, libraries, activeLibrary).verifySession()
    }

    // #371
    suspend fun addAnswering(
        response: MockResponse,
        createAccount: Boolean = false,
        tokenStore: TestAuthTokenStore = storeHolding(),
        libraries: TestLibrariesRepository = TestLibrariesRepository(),
        libraryTokens: LibraryTokenStore = TestLibraryTokenStore(),
    ): AuthNewLibraryResult = withMockServer { server ->
        server.enqueue(response)
        sut(tokenStore = tokenStore, libraries = libraries, libraryTokens = libraryTokens)
            .signInToNewLibrary(server.url("/").toString(), randomString(), randomString(), createAccount)
    }

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("signing in") {
        context("when the server accepts the credentials") {
            it("#366 TC-9: records the username on the active library") {
                val libraries = TestLibrariesRepository()
                val activeId = randomString()
                val username = randomString()

                signInAnswering(
                    reply(200, acceptedBody(user = userJson(username = username))),
                    libraries = libraries,
                    activeLibrary = TestActiveLibrary(current = activeId),
                )

                libraries.usernameUpdates shouldBe listOf(activeId to username)
            }

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
            it("#366 TC-11: records no username") {
                checkAll(Exhaustive.of(401, 403)) { code ->
                    val libraries = TestLibrariesRepository()

                    signInAnswering(reply(code), libraries = libraries, activeLibrary = TestActiveLibrary(current = randomString()))

                    libraries.usernameUpdates.shouldBeEmpty()
                }
            }

            it("TC-2: returns InvalidCredentials for 401 and 403") {
                checkAll(Exhaustive.of(401, 403)) { code ->
                    signInAnswering(reply(code)) shouldBe AuthSignInResult.InvalidCredentials
                }
            }
        }

        context("when the server is rate limiting") {
            it("#366 TC-11: records no username") {
                val libraries = TestLibrariesRepository()

                signInAnswering(reply(429), libraries = libraries, activeLibrary = TestActiveLibrary(current = randomString()))

                libraries.usernameUpdates.shouldBeEmpty()
            }

            it("TC-3: returns TooManyAttempts") {
                signInAnswering(reply(429)) shouldBe AuthSignInResult.TooManyAttempts
            }
        }

        context("when the server responds with any other non-2xx status") {
            it("#366 TC-11: records no username") {
                checkAll(Exhaustive.of(400, 404, 500, 503)) { code ->
                    val libraries = TestLibrariesRepository()

                    signInAnswering(reply(code), libraries = libraries, activeLibrary = TestActiveLibrary(current = randomString()))

                    libraries.usernameUpdates.shouldBeEmpty()
                }
            }

            it("TC-4: returns ServerError for each status") {
                checkAll(Exhaustive.of(400, 404, 500, 503)) { code ->
                    signInAnswering(reply(code)) shouldBe AuthSignInResult.ServerError
                }
            }
        }

        context("when the server responds 200 with a body that can't be used") {
            it("#366 TC-11: records no username") {
                checkAll(Exhaustive.collection(unusableBodies())) { body ->
                    val libraries = TestLibrariesRepository()

                    signInAnswering(reply(200, body), libraries = libraries, activeLibrary = TestActiveLibrary(current = randomString()))

                    libraries.usernameUpdates.shouldBeEmpty()
                }
            }

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
            it("#366 TC-11: records no username") {
                val libraries = TestLibrariesRepository()

                sut(unreachableUrl(), libraries = libraries, activeLibrary = TestActiveLibrary(current = randomString()))
                    .signIn(randomString(), randomString())

                libraries.usernameUpdates.shouldBeEmpty()
            }

            it("TC-7: returns Unreachable") {
                sut(unreachableUrl()).signIn(randomString(), randomString()) shouldBe AuthSignInResult.Unreachable
            }
        }

        context("when saving the token fails") {
            it("#366 TC-11: records no username") {
                checkAll(Exhaustive.of(IOException("disk full"), IllegalStateException("keystore unavailable"))) { failure ->
                    val libraries = TestLibrariesRepository()
                    val tokenStore = TestAuthTokenStore().apply { saveFailure = failure }

                    signInAnswering(
                        reply(200, acceptedBody()),
                        tokenStore = tokenStore,
                        libraries = libraries,
                        activeLibrary = TestActiveLibrary(current = randomString()),
                    )

                    libraries.usernameUpdates.shouldBeEmpty()
                }
            }

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
            it("#366 TC-10: records the username on the active library") {
                val libraries = TestLibrariesRepository()
                val activeId = randomString()
                val username = randomString()

                registerAnswering(
                    reply(200, acceptedBody(user = userJson(username = username))),
                    libraries = libraries,
                    activeLibrary = TestActiveLibrary(current = activeId),
                )

                libraries.usernameUpdates shouldBe listOf(activeId to username)
            }

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
            it("#366 TC-11: records no username") {
                val libraries = TestLibrariesRepository()

                registerAnswering(reply(409), libraries = libraries, activeLibrary = TestActiveLibrary(current = randomString()))

                libraries.usernameUpdates.shouldBeEmpty()
            }

            it("TC-41: returns UsernameTaken") {
                registerAnswering(reply(409)) shouldBe AuthRegisterResult.UsernameTaken
            }
        }

        context("when registration is closed") {
            it("#366 TC-11: records no username") {
                val libraries = TestLibrariesRepository()

                registerAnswering(reply(403), libraries = libraries, activeLibrary = TestActiveLibrary(current = randomString()))

                libraries.usernameUpdates.shouldBeEmpty()
            }

            it("TC-42: returns RegistrationDisabled") {
                registerAnswering(reply(403)) shouldBe AuthRegisterResult.RegistrationDisabled
            }
        }

        context("when the details are rejected") {
            it("#366 TC-11: records no username") {
                val libraries = TestLibrariesRepository()

                registerAnswering(reply(400), libraries = libraries, activeLibrary = TestActiveLibrary(current = randomString()))

                libraries.usernameUpdates.shouldBeEmpty()
            }

            it("TC-43: returns InvalidDetails") {
                registerAnswering(reply(400)) shouldBe AuthRegisterResult.InvalidDetails
            }
        }

        context("when the server is rate limiting") {
            it("#366 TC-11: records no username") {
                val libraries = TestLibrariesRepository()

                registerAnswering(reply(429), libraries = libraries, activeLibrary = TestActiveLibrary(current = randomString()))

                libraries.usernameUpdates.shouldBeEmpty()
            }

            it("TC-44: returns TooManyAttempts") {
                registerAnswering(reply(429)) shouldBe AuthRegisterResult.TooManyAttempts
            }
        }

        context("when the server responds with any other non-2xx status") {
            it("#366 TC-11: records no username") {
                checkAll(Exhaustive.of(401, 404, 500, 503)) { code ->
                    val libraries = TestLibrariesRepository()

                    registerAnswering(reply(code), libraries = libraries, activeLibrary = TestActiveLibrary(current = randomString()))

                    libraries.usernameUpdates.shouldBeEmpty()
                }
            }

            it("TC-45: returns ServerError for each status") {
                checkAll(Exhaustive.of(401, 404, 500, 503)) { code ->
                    registerAnswering(reply(code)) shouldBe AuthRegisterResult.ServerError
                }
            }
        }

        context("when the server responds 200 with a body that can't be used") {
            it("#366 TC-11: records no username") {
                checkAll(Exhaustive.collection(unusableBodies())) { body ->
                    val libraries = TestLibrariesRepository()

                    registerAnswering(reply(200, body), libraries = libraries, activeLibrary = TestActiveLibrary(current = randomString()))

                    libraries.usernameUpdates.shouldBeEmpty()
                }
            }

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
            it("#366 TC-11: records no username") {
                val libraries = TestLibrariesRepository()

                sut(unreachableUrl(), libraries = libraries, activeLibrary = TestActiveLibrary(current = randomString()))
                    .register(randomString(), randomString())

                libraries.usernameUpdates.shouldBeEmpty()
            }

            it("TC-49: returns Unreachable") {
                sut(unreachableUrl()).register(randomString(), randomString()) shouldBe AuthRegisterResult.Unreachable
            }
        }

        context("when saving the token fails") {
            it("#366 TC-11: records no username") {
                checkAll(Exhaustive.of(IOException("disk full"), IllegalStateException("keystore unavailable"))) { failure ->
                    val libraries = TestLibrariesRepository()
                    val tokenStore = TestAuthTokenStore().apply { saveFailure = failure }

                    registerAnswering(
                        reply(200, acceptedBody()),
                        tokenStore = tokenStore,
                        libraries = libraries,
                        activeLibrary = TestActiveLibrary(current = randomString()),
                    )

                    libraries.usernameUpdates.shouldBeEmpty()
                }
            }

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
            context("when the connection drops mid-body") {
                it("#366 TC-13: records no username") {
                    val libraries = TestLibrariesRepository()

                    verifyAnswering(
                        droppedMidBody(userJson()),
                        libraries = libraries,
                        activeLibrary = TestActiveLibrary(current = randomString()),
                    )

                    libraries.usernameUpdates.shouldBeEmpty()
                }
            }

            context("when no server is saved") {
                it("TC-22: returns NoServer") {
                    sut(tokenStore = storeHolding()).verifySession() shouldBe AuthSessionResult.NoServer
                }
            }

            context("when the server accepts the session") {
                it("#366 TC-12: records the username on the active library") {
                    val libraries = TestLibrariesRepository()
                    val activeId = randomString()
                    val username = randomString()

                    verifyAnswering(
                        reply(200, userJson(username = username)),
                        libraries = libraries,
                        activeLibrary = TestActiveLibrary(current = activeId),
                    )

                    libraries.usernameUpdates shouldBe listOf(activeId to username)
                }

                context("when the body can't be read") {
                    it("#366 TC-13: records no username") {
                        val libraries = TestLibrariesRepository()

                        verifyAnswering(reply(200, "not json"), libraries = libraries, activeLibrary = TestActiveLibrary(current = randomString()))

                        libraries.usernameUpdates.shouldBeEmpty()
                    }
                }

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
                it("#366 TC-13: records no username") {
                    val libraries = TestLibrariesRepository()

                    verifyAnswering(reply(401), libraries = libraries, activeLibrary = TestActiveLibrary(current = randomString()))

                    libraries.usernameUpdates.shouldBeEmpty()
                }

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
                it("#366 TC-13: records no username") {
                    checkAll(Exhaustive.of(403, 404, 500, 503)) { code ->
                        val libraries = TestLibrariesRepository()

                        verifyAnswering(reply(code), libraries = libraries, activeLibrary = TestActiveLibrary(current = randomString()))

                        libraries.usernameUpdates.shouldBeEmpty()
                    }
                }

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

        context("when the server reports the book request permissions") {
            it("exposes each of the three, whatever the others are") {
                checkAll(Exhaustive.of(true, false), Exhaustive.of(true, false), Exhaustive.of(true, false)) { requests, start, choose ->
                    withMockServer { server ->
                        val extra = """, "book_requests": $requests, "can_start_requests": $start, "can_choose_request_versions": $choose"""
                        server.enqueue(reply(200, userJson(extraFields = extra)))
                        val repository = sut(server.url("/"), storeHolding())

                        repository.verifySession()

                        val user = repository.currentUser.first()!!
                        listOf(user.bookRequests, user.canStartRequests, user.canChooseRequestVersions) shouldBe
                            listOf(requests, start, choose)
                    }
                }
            }

            it("exposes them from a sign-in response too") {
                withMockServer { server ->
                    val extra = """, "book_requests": true, "can_start_requests": true, "can_choose_request_versions": true"""
                    server.enqueue(reply(200, acceptedBody(user = userJson(extraFields = extra))))
                    val repository = sut(server.url("/"))

                    repository.signIn(randomString(), randomString())

                    repository.currentUser.first()!!.canChooseRequestVersions shouldBe true
                }
            }
        }

        context("when the server is older and omits the book request permissions") {
            it("reads all three as false and still signs in") {
                withMockServer { server ->
                    server.enqueue(reply(200, userJson()))
                    val repository = sut(server.url("/"), storeHolding())

                    val result = repository.verifySession()

                    val user = repository.currentUser.first()!!
                    listOf(result is AuthSessionResult.Valid, user.bookRequests, user.canStartRequests, user.canChooseRequestVersions) shouldBe
                        listOf(true, false, false, false)
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

    describe("recording the username") {
        context("when there is no active library") {
            it("#366 TC-14: records no username on sign-in, registration or a session check") {
                val libraries = TestLibrariesRepository()
                val activeLibrary = TestActiveLibrary(current = null)

                signInAnswering(reply(200, acceptedBody()), libraries = libraries, activeLibrary = activeLibrary)
                registerAnswering(reply(200, acceptedBody()), libraries = libraries, activeLibrary = activeLibrary)
                verifyAnswering(reply(200, userJson()), libraries = libraries, activeLibrary = activeLibrary)

                libraries.usernameUpdates.shouldBeEmpty()
            }

            it("#366 TC-14: still returns each call's success result") {
                val token = randomString()
                val activeLibrary = TestActiveLibrary(current = null)

                signInAnswering(reply(200, acceptedBody(token)), activeLibrary = activeLibrary) shouldBe
                    AuthSignInResult.Success(token)
                registerAnswering(reply(200, acceptedBody(token)), activeLibrary = activeLibrary) shouldBe
                    AuthRegisterResult.Success(token)
                verifyAnswering(reply(200, userJson()), activeLibrary = activeLibrary) shouldBe AuthSessionResult.Valid
            }
        }

        context("when storing it fails") {
            it("#366 TC-15: returns each call's success result") {
                val token = randomString()
                val libraries = TestLibrariesRepository().apply { setUsernameFailure = IllegalStateException("db") }
                val activeLibrary = TestActiveLibrary(current = randomString())

                signInAnswering(reply(200, acceptedBody(token)), libraries = libraries, activeLibrary = activeLibrary) shouldBe
                    AuthSignInResult.Success(token)
                registerAnswering(reply(200, acceptedBody(token)), libraries = libraries, activeLibrary = activeLibrary) shouldBe
                    AuthRegisterResult.Success(token)
                verifyAnswering(reply(200, userJson()), libraries = libraries, activeLibrary = activeLibrary) shouldBe
                    AuthSessionResult.Valid
            }
        }

        context("when storing it is cancelled") {
            it("#366 TC-15: rethrows the cancellation from each call") {
                val libraries = TestLibrariesRepository().apply { setUsernameFailure = CancellationException("cancelled") }
                val activeLibrary = TestActiveLibrary(current = randomString())

                shouldThrow<CancellationException> {
                    signInAnswering(reply(200, acceptedBody()), libraries = libraries, activeLibrary = activeLibrary)
                }
                shouldThrow<CancellationException> {
                    registerAnswering(reply(200, acceptedBody()), libraries = libraries, activeLibrary = activeLibrary)
                }
                shouldThrow<CancellationException> {
                    verifyAnswering(reply(200, userJson()), libraries = libraries, activeLibrary = activeLibrary)
                }
            }
        }

        context("when the server's username is blank") {
            it("#366 TC-16: records no username on sign-in, registration or a session check") {
                checkAll(Exhaustive.of("", "   ")) { blank ->
                    val libraries = TestLibrariesRepository()
                    val activeLibrary = TestActiveLibrary(current = randomString())

                    signInAnswering(
                        reply(200, acceptedBody(user = userJson(username = blank))),
                        libraries = libraries,
                        activeLibrary = activeLibrary,
                    )
                    registerAnswering(
                        reply(200, acceptedBody(user = userJson(username = blank))),
                        libraries = libraries,
                        activeLibrary = activeLibrary,
                    )
                    verifyAnswering(reply(200, userJson(username = blank)), libraries = libraries, activeLibrary = activeLibrary)

                    libraries.usernameUpdates.shouldBeEmpty()
                }
            }
        }
    }

    // #371
    describe("signing in to a library that isn't saved yet") {
        it("TC-5: posts once to the sign-in or registration path, without an Authorization header") {
            checkAll(Exhaustive.of(false, true)) { createAccount ->
                val tokenStore = storeHolding()
                withMockServer { server ->
                    server.enqueue(reply(200, acceptedBody()))

                    sut(tokenStore = tokenStore).signInToNewLibrary(
                        server.url("/").toString(), randomString(), randomString(), createAccount,
                    )

                    val request = server.takeRequest()
                    server.requestCount shouldBe 1
                    request.method shouldBe "POST"
                    request.url.encodedPath shouldBe if (createAccount) "/api/auth/register" else "/api/auth/login"
                    request.headers["Authorization"].shouldBeNull()
                }
            }
        }

        it("TC-6: maps each status to its result and saves nothing") {
            val failures = listOf(
                Triple(false, reply(401), AuthNewLibraryResult.InvalidCredentials),
                Triple(false, reply(403), AuthNewLibraryResult.InvalidCredentials),
                Triple(false, reply(429), AuthNewLibraryResult.TooManyAttempts),
                Triple(false, reply(500), AuthNewLibraryResult.ServerError),
                Triple(true, reply(409), AuthNewLibraryResult.UsernameTaken),
                Triple(true, reply(403), AuthNewLibraryResult.RegistrationDisabled),
                Triple(true, reply(400), AuthNewLibraryResult.InvalidDetails),
                Triple(true, reply(429), AuthNewLibraryResult.TooManyAttempts),
            )
            checkAll(Exhaustive.collection(failures)) { (createAccount, response, expected) ->
                val tokenStore = storeHolding()
                val token = tokenStore.token.first()
                val libraries = TestLibrariesRepository()

                addAnswering(response, createAccount, tokenStore, libraries) shouldBe expected

                libraries.getLibrariesFlow().first().shouldBeEmpty()
                tokenStore.token.first() shouldBe token
            }
        }

        it("TC-6: maps an unreachable server to Unreachable and saves nothing") {
            val libraries = TestLibrariesRepository()

            sut(libraries = libraries).signInToNewLibrary(
                unreachableUrl().toString(), randomString(), randomString(),
            ) shouldBe AuthNewLibraryResult.Unreachable

            libraries.getLibrariesFlow().first().shouldBeEmpty()
        }

        it("TC-6: maps each unusable 2xx body to ServerError and saves nothing") {
            checkAll(Exhaustive.collection(unusableBodies())) { body ->
                val libraries = TestLibrariesRepository()

                addAnswering(reply(200, body), libraries = libraries) shouldBe AuthNewLibraryResult.ServerError

                libraries.getLibrariesFlow().first().shouldBeEmpty()
            }
        }

        it("TC-7: adds the library inactive, at the normalized address") {
            val active = randomLibrary().copy(isActive = true)
            val libraries = TestLibrariesRepository(listOf(active))

            withMockServer { server ->
                server.enqueue(reply(200, acceptedBody()))
                val address = server.url("/").toString()

                sut(libraries = libraries).signInToNewLibrary("$address/", randomString(), randomString())

                val added = libraries.getLibrariesFlow().first().last()
                added.address shouldBe address.trimEnd('/')
                added.isActive shouldBe false
            }
        }

        it("TC-7: keeps the active library active") {
            val active = randomLibrary().copy(isActive = true)
            val libraries = TestLibrariesRepository(listOf(active))

            addAnswering(reply(200, acceptedBody()), libraries = libraries)

            libraries.getActiveLibraryFlow().first() shouldBe active
        }

        it("TC-7: stores the new token under the new library and leaves the active token alone") {
            val active = randomLibrary().copy(isActive = true)
            val activeToken = randomString()
            val tokenStore = storeHolding(activeToken)
            val libraryTokens = TestLibraryTokenStore(initialTokens = mapOf(active.id to activeToken))
            val libraries = TestLibrariesRepository(listOf(active))
            val newToken = randomString()

            val result = addAnswering(
                reply(200, acceptedBody(token = newToken)),
                tokenStore = tokenStore,
                libraries = libraries,
                libraryTokens = libraryTokens,
            )

            val id = (result as AuthNewLibraryResult.Success).libraryId
            libraryTokens.getTokenFlow(id).first() shouldBe newToken
            libraryTokens.getTokenFlow(active.id).first() shouldBe activeToken
            tokenStore.token.first() shouldBe activeToken
        }

        it("TC-7: records the username on the new library only") {
            val active = randomLibrary().copy(isActive = true)
            val libraries = TestLibrariesRepository(listOf(active))
            val username = randomString()

            val result = addAnswering(
                reply(200, acceptedBody(user = userJson(username = username))),
                libraries = libraries,
            )

            val id = (result as AuthNewLibraryResult.Success).libraryId
            libraries.usernameUpdates shouldBe listOf(id to username)
        }

        it("TC-7: leaves the active library and the current user as they were") {
            withMockServer(reply(200, userJson()), reply(200, acceptedBody())) { server ->
                val active = randomLibrary().copy(isActive = true)
                val libraries = TestLibrariesRepository(listOf(active))
                val activeLibrary = TestActiveLibrary(current = active.id)
                val repository = sut(
                    server.url("/"),
                    storeHolding(),
                    libraries,
                    activeLibrary,
                )
                repository.verifySession()
                val user = repository.currentUser.first()

                repository.signInToNewLibrary(server.url("/").toString(), randomString(), randomString())

                repository.currentUser.first() shouldBe user
                activeLibrary.id() shouldBe active.id
                libraries.activations.shouldBeEmpty()
            }
        }

        it("TC-8: removes the new library when its token can't be saved, and reports ServerError") {
            val libraries = TestLibrariesRepository()

            val result = addAnswering(
                reply(200, acceptedBody()),
                libraries = libraries,
                libraryTokens = FailingSaveTokenStore(),
            )

            result shouldBe AuthNewLibraryResult.ServerError
            libraries.removedIds.size shouldBe 1
            libraries.getLibrariesFlow().first().shouldBeEmpty()
        }

        it("TC-9: returns AlreadySaved without sending a request when the address is saved") {
            withMockServer { server ->
                val saved = randomLibrary().copy(address = server.url("/").toString().trimEnd('/'))
                val libraries = TestLibrariesRepository(listOf(saved))

                sut(libraries = libraries).signInToNewLibrary(
                    server.url("/").toString(), randomString(), randomString(),
                ) shouldBe AuthNewLibraryResult.AlreadySaved

                server.requestCount shouldBe 0
            }
        }
    }
})

/** Token store whose save fails, as a full disk does. Reads go to [inner]. */
private class FailingSaveTokenStore(inner: LibraryTokenStore = TestLibraryTokenStore()) :
    LibraryTokenStore by inner {
    override suspend fun save(libraryId: String, token: String): Unit = throw IOException("disk full")
}
