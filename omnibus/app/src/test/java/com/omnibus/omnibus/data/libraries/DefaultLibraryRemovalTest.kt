package com.omnibus.omnibus.data.libraries

import com.omnibus.omnibus.data.libraries.local.LibraryTokenStore
import com.omnibus.omnibus.data.libraries.local.TestLibraryTokenStore
import com.omnibus.omnibus.data.libraries.remote.InactiveLibraryApi
import com.omnibus.omnibus.data.libraries.remote.TestInactiveLibraryApi
import com.omnibus.omnibus.testing.randomString
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.enum
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl

/** The setups in which a removed library can't be logged out: it has no token, or its address doesn't normalize. */
private enum class NoLogout { NoToken, AddressDoesNotParse }

class DefaultLibraryRemovalTest : DescribeSpec({

    fun TestScope.sut(
        libraries: LibrariesRepository = TestLibrariesRepository(),
        tokens: LibraryTokenStore = TestLibraryTokenStore(),
        active: ActiveLibrary = TestActiveLibrary(),
        sync: LibrarySync = TestLibrarySync(),
        api: InactiveLibraryApi = TestInactiveLibraryApi(),
    ): DefaultLibraryRemoval = DefaultLibraryRemoval(libraries, tokens, active, sync, api, backgroundScope)

    describe("removing a library") {

        it("#373 TC-9: ends the session on the library's server with its own token") {
            runTest {
                val library = randomLibrary().copy(isActive = false)
                val token = randomString()
                val api = TestInactiveLibraryApi()

                sut(
                    libraries = TestLibrariesRepository(listOf(library)),
                    tokens = TestLibraryTokenStore(mapOf(library.id to token)),
                    api = api,
                ).remove(library.id)
                runCurrent()

                api.calls shouldBe listOf(TestInactiveLibraryApi.Logout(library.address.toHttpUrl(), token))
            }
        }

        it("#373 TC-9: removes the library") {
            runTest {
                val library = randomLibrary().copy(isActive = false)
                val libraries = TestLibrariesRepository(listOf(library))

                sut(
                    libraries = libraries,
                    tokens = TestLibraryTokenStore(mapOf(library.id to randomString())),
                ).remove(library.id)

                libraries.removedIds shouldBe listOf(library.id)
            }
        }

        // R9: removal doesn't wait for the server
        it("#373 TC-10: removes the library when the logout never answers") {
            runTest {
                val library = randomLibrary().copy(isActive = false)
                val libraries = TestLibrariesRepository(listOf(library))
                val api = TestInactiveLibraryApi().also { it.logoutGate = CompletableDeferred() }

                sut(
                    libraries = libraries,
                    tokens = TestLibraryTokenStore(mapOf(library.id to randomString())),
                    api = api,
                ).remove(library.id)

                libraries.removedIds shouldBe listOf(library.id)
            }
        }

        // R9, R11: nothing to log out with, so the library is removed without a request
        context("when the library can't be logged out") {

            it("#373 TC-10: removes the library and makes no logout call") {
                runTest {
                    checkAll(Exhaustive.enum<NoLogout>()) { setup ->
                        val library = randomLibrary().copy(isActive = false)
                        val address = if (setup == NoLogout.AddressDoesNotParse) "" else library.address
                        val token = if (setup == NoLogout.NoToken) null else randomString()
                        val libraries = TestLibrariesRepository(listOf(library.copy(address = address)))
                        val tokens = TestLibraryTokenStore(listOfNotNull(token?.let { library.id to it }).toMap())
                        val api = TestInactiveLibraryApi()

                        sut(libraries = libraries, tokens = tokens, api = api).remove(library.id)
                        runCurrent()

                        libraries.removedIds shouldBe listOf(library.id)
                        api.calls shouldBe emptyList()
                    }
                }
            }
        }

        // R12
        context("when the library is the active one") {

            it("#373 TC-11: throws IllegalStateException") {
                runTest {
                    val library = randomLibrary().copy(isActive = true)

                    shouldThrow<IllegalStateException> {
                        sut(
                            libraries = TestLibrariesRepository(listOf(library)),
                            tokens = TestLibraryTokenStore(mapOf(library.id to randomString())),
                            active = TestActiveLibrary(library.id),
                        ).remove(library.id)
                    }
                }
            }

            it("#373 TC-11: removes nothing and ends no session") {
                runTest {
                    val library = randomLibrary().copy(isActive = true)
                    val libraries = TestLibrariesRepository(listOf(library))
                    val api = TestInactiveLibraryApi()

                    runCatching {
                        sut(
                            libraries = libraries,
                            tokens = TestLibraryTokenStore(mapOf(library.id to randomString())),
                            active = TestActiveLibrary(library.id),
                            api = api,
                        ).remove(library.id)
                    }
                    runCurrent()

                    libraries.removedIds shouldBe emptyList()
                    api.calls shouldBe emptyList()
                }
            }
        }
    }

    describe("syncing a library before removal") {

        // R14: sync hands to syncInactive for the same library, unchanged
        it("#373 TC-12: calls syncInactive once and returns its result") {
            runTest {
                checkAll(Exhaustive.of(LibrarySyncResult.Synced, LibrarySyncResult.Failed)) { result ->
                    val library = randomLibrary().copy(isActive = false)
                    val sync = TestLibrarySync().also { it.inactiveResult = result }

                    sut(sync = sync).sync(library.id) shouldBe result

                    sync.inactiveSyncs shouldBe listOf(library.id)
                }
            }
        }
    }
})
