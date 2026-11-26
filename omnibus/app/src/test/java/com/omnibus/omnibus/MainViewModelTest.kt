package com.omnibus.omnibus

import com.omnibus.omnibus.data.auth.AuthSessionResult
import com.omnibus.omnibus.data.auth.TestAuthRepository
import com.omnibus.omnibus.data.configuration.TestServerConfigurationRepository
import com.omnibus.omnibus.data.configuration.randomServerAddress
import com.omnibus.omnibus.data.libraries.PendingOpening
import com.omnibus.omnibus.data.libraries.SwitchKind
import com.omnibus.omnibus.data.libraries.TestLibrariesRepository
import com.omnibus.omnibus.data.libraries.TestLibrarySwitch
import com.omnibus.omnibus.data.libraries.randomLibrary
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.enum
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest : DescribeSpec({

    fun sut(
        serverRepository: TestServerConfigurationRepository = TestServerConfigurationRepository(),
        authRepository: TestAuthRepository = TestAuthRepository(),
        librarySwitch: TestLibrarySwitch = TestLibrarySwitch(),
        libraries: TestLibrariesRepository = TestLibrariesRepository(),
    ) = MainViewModel(serverRepository, authRepository, librarySwitch, libraries)

    /** Ways a saved marker can be stale at launch. */
    fun staleMarkerKinds() = Exhaustive.enum<StaleMarker>()

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    describe("the start destination") {
        context("when no server is saved") {
            it("TC-32: opens server configuration") {
                val viewModel = sut(TestServerConfigurationRepository(initialAddress = null))

                viewModel.startDestination.value shouldBe StartDestination.ServerConfiguration
            }

            context("when a token is stored") {
                it("TC-32: opens server configuration") {
                    val authRepository = TestAuthRepository()
                    authRepository.signedIn.value = true

                    val viewModel = sut(TestServerConfigurationRepository(initialAddress = null), authRepository)

                    viewModel.startDestination.value shouldBe StartDestination.ServerConfiguration
                }
            }
        }

        context("when the saved server is blank") {
            it("TC-32: opens server configuration") {
                val viewModel = sut(TestServerConfigurationRepository(initialAddress = "  "))

                viewModel.startDestination.value shouldBe StartDestination.ServerConfiguration
            }
        }

        context("when a server is saved") {
            context("when no token is stored") {
                it("TC-33: opens login") {
                    val authRepository = TestAuthRepository()
                    authRepository.signedIn.value = false

                    val viewModel =
                        sut(TestServerConfigurationRepository(initialAddress = randomServerAddress()), authRepository)

                    viewModel.startDestination.value shouldBe StartDestination.Login
                }
            }

            context("when a token is stored") {
                it("TC-34: opens the signed-in shell") {
                    val authRepository = TestAuthRepository()
                    authRepository.signedIn.value = true

                    val viewModel =
                        sut(TestServerConfigurationRepository(initialAddress = randomServerAddress()), authRepository)

                    viewModel.startDestination.value shouldBe StartDestination.SignedIn
                }
            }
        }

        context("when the saved config can't be read") {
            it("TC-35: opens server configuration") {
                val repository = TestServerConfigurationRepository(initialAddress = randomServerAddress())
                    .apply { readFailure = IOException("corrupt") }

                sut(repository).startDestination.value shouldBe StartDestination.ServerConfiguration
            }

            context("when a token is stored") {
                it("TC-35: opens server configuration") {
                    val authRepository = TestAuthRepository()
                    authRepository.signedIn.value = true
                    val repository = TestServerConfigurationRepository(initialAddress = randomServerAddress())
                        .apply { readFailure = IOException("corrupt") }

                    sut(repository, authRepository).startDestination.value shouldBe StartDestination.ServerConfiguration
                }
            }
        }

        context("when the config is edited after launch") {
            it("TC-37: does not change") {
                val repository = TestServerConfigurationRepository(initialAddress = null)
                val viewModel = sut(repository)

                repository.saveServerConfig(randomServerAddress())

                viewModel.startDestination.value shouldBe StartDestination.ServerConfiguration
            }
        }

        context("when a session is saved after launch") {
            it("TC-37: stays on login") {
                val authRepository = TestAuthRepository()
                authRepository.signedIn.value = false
                val viewModel =
                    sut(TestServerConfigurationRepository(initialAddress = randomServerAddress()), authRepository)

                authRepository.signedIn.value = true

                viewModel.startDestination.value shouldBe StartDestination.Login
            }
        }

        context("when the session ends after launch") {
            it("TC-37: stays on the signed-in shell") {
                val authRepository = TestAuthRepository()
                authRepository.signedIn.value = true
                val viewModel =
                    sut(TestServerConfigurationRepository(initialAddress = randomServerAddress()), authRepository)

                authRepository.signedIn.value = false

                viewModel.startDestination.value shouldBe StartDestination.SignedIn
            }
        }

        it("TC-37: stays Loading until the session state has been read") {
            val authRepository = TestAuthRepository()
            val gate = CompletableDeferred<Unit>()
            authRepository.isSignedInGate = gate
            authRepository.signedIn.value = true
            val viewModel =
                sut(TestServerConfigurationRepository(initialAddress = randomServerAddress()), authRepository)

            viewModel.startDestination.value shouldBe StartDestination.Loading

            gate.complete(Unit)

            viewModel.startDestination.value shouldBe StartDestination.SignedIn
        }
    }

    describe("session verification") {

        context("when launching into the signed-in shell") {
            it("TC-38: verifies the session exactly once") {
                val authRepository = TestAuthRepository()
                authRepository.signedIn.value = true

                sut(TestServerConfigurationRepository(initialAddress = randomServerAddress()), authRepository)

                authRepository.verifySessionCalls shouldBe 1
            }

            context("when the session state changes afterwards") {
                it("TC-38: doesn't verify again") {
                    val authRepository = TestAuthRepository()
                    authRepository.signedIn.value = true
                    sut(TestServerConfigurationRepository(initialAddress = randomServerAddress()), authRepository)

                    authRepository.signedIn.value = false
                    authRepository.signedIn.value = true

                    authRepository.verifySessionCalls shouldBe 1
                }
            }

            context("when verification can't reach the server") {
                it("TC-38: still opens the signed-in shell") {
                    val authRepository = TestAuthRepository()
                    authRepository.signedIn.value = true
                    authRepository.verifySessionResult = AuthSessionResult.Unreachable

                    val viewModel =
                        sut(TestServerConfigurationRepository(initialAddress = randomServerAddress()), authRepository)

                    viewModel.startDestination.value shouldBe StartDestination.SignedIn
                    authRepository.verifySessionCalls shouldBe 1
                }
            }
        }

        context("when launching into login") {
            it("TC-38: doesn't verify") {
                val authRepository = TestAuthRepository()
                authRepository.signedIn.value = false

                sut(TestServerConfigurationRepository(initialAddress = randomServerAddress()), authRepository)

                authRepository.verifySessionCalls shouldBe 0
            }
        }

        context("when launching into server configuration") {
            it("TC-38: doesn't verify") {
                val authRepository = TestAuthRepository()
                authRepository.signedIn.value = true

                sut(TestServerConfigurationRepository(initialAddress = null), authRepository)

                authRepository.verifySessionCalls shouldBe 0
            }
        }
    }

    describe("the signed-in state") {

        it("TC-39: is null until the session state has been read") {
            val authRepository = TestAuthRepository()
            authRepository.isSignedInGate = CompletableDeferred()

            val viewModel =
                sut(TestServerConfigurationRepository(initialAddress = randomServerAddress()), authRepository)

            viewModel.isSignedIn.value shouldBe null
        }

        context("when the session ends") {
            it("TC-39: follows the repository from true to false") {
                val authRepository = TestAuthRepository()
                authRepository.signedIn.value = true
                val viewModel =
                    sut(TestServerConfigurationRepository(initialAddress = randomServerAddress()), authRepository)
                viewModel.isSignedIn.value shouldBe true

                authRepository.signedIn.value = false

                viewModel.isSignedIn.value shouldBe false
            }
        }

        context("when no session is stored") {
            it("TC-39: is false") {
                val authRepository = TestAuthRepository()
                authRepository.signedIn.value = false

                val viewModel =
                    sut(TestServerConfigurationRepository(initialAddress = randomServerAddress()), authRepository)

                viewModel.isSignedIn.value shouldBe false
            }
        }

        context("when a session is saved after launch") {
            it("TC-39: becomes true") {
                val authRepository = TestAuthRepository()
                authRepository.signedIn.value = false
                val viewModel =
                    sut(TestServerConfigurationRepository(initialAddress = randomServerAddress()), authRepository)

                authRepository.signedIn.value = true

                viewModel.isSignedIn.value shouldBe true
            }
        }
    }

    describe("switching libraries") {

        context("when a marker names the active library and a session is saved") {
            it("#369 TC-12: opens the switching screen's Opening stage for it") {
                val library = randomLibrary().copy(isActive = true)
                val authRepository = TestAuthRepository()
                authRepository.signedIn.value = true
                val librarySwitch = TestLibrarySwitch()
                librarySwitch.pending.value = PendingOpening(library.id, SwitchKind.Switch)

                val viewModel = sut(
                    TestServerConfigurationRepository(initialAddress = randomServerAddress()),
                    authRepository,
                    librarySwitch,
                    TestLibrariesRepository(listOf(library)),
                )

                viewModel.startDestination.value shouldBe
                    StartDestination.SwitchOpening(PendingOpening(library.id, SwitchKind.Switch))
            }

            it("#369 TC-12: verifies the session exactly once") {
                val library = randomLibrary().copy(isActive = true)
                val authRepository = TestAuthRepository()
                authRepository.signedIn.value = true
                val librarySwitch = TestLibrarySwitch()
                librarySwitch.pending.value = PendingOpening(library.id, SwitchKind.Switch)

                sut(
                    TestServerConfigurationRepository(initialAddress = randomServerAddress()),
                    authRepository,
                    librarySwitch,
                    TestLibrariesRepository(listOf(library)),
                )

                authRepository.verifySessionCalls shouldBe 1
            }

            it("#369 TC-12: leaves the marker for the opening to clear") {
                val library = randomLibrary().copy(isActive = true)
                val authRepository = TestAuthRepository()
                authRepository.signedIn.value = true
                val librarySwitch = TestLibrarySwitch()
                librarySwitch.pending.value = PendingOpening(library.id, SwitchKind.Switch)

                sut(
                    TestServerConfigurationRepository(initialAddress = randomServerAddress()),
                    authRepository,
                    librarySwitch,
                    TestLibrariesRepository(listOf(library)),
                )

                librarySwitch.clears shouldBe 0
            }
        }

        context("when a marker is stale and a session is saved") {
            it("#369 TC-13: clears the marker once and opens the signed-in shell") {
                checkAll(staleMarkerKinds()) { kind ->
                    val target = randomLibrary().copy(isActive = false)
                    val other = randomLibrary().copy(isActive = true)
                    val libraries = when (kind) {
                        StaleMarker.NotActive -> listOf(target, other)
                        StaleMarker.Missing -> listOf(other)
                    }
                    val authRepository = TestAuthRepository()
                    authRepository.signedIn.value = true
                    val librarySwitch = TestLibrarySwitch()
                    librarySwitch.pending.value = PendingOpening(target.id, SwitchKind.Switch)

                    val viewModel = sut(
                        TestServerConfigurationRepository(initialAddress = randomServerAddress()),
                        authRepository,
                        librarySwitch,
                        TestLibrariesRepository(libraries),
                    )

                    librarySwitch.clears shouldBe 1
                    viewModel.startDestination.value shouldBe StartDestination.SignedIn
                }
            }
        }

        context("when a marker names the active library and no session is saved") {
            it("#369 TC-14: clears the marker once and opens login") {
                val library = randomLibrary().copy(isActive = true)
                val authRepository = TestAuthRepository()
                authRepository.signedIn.value = false
                val librarySwitch = TestLibrarySwitch()
                librarySwitch.pending.value = PendingOpening(library.id, SwitchKind.Switch)

                val viewModel = sut(
                    TestServerConfigurationRepository(initialAddress = randomServerAddress()),
                    authRepository,
                    librarySwitch,
                    TestLibrariesRepository(listOf(library)),
                )

                librarySwitch.clears shouldBe 1
                viewModel.startDestination.value shouldBe StartDestination.Login
            }

            it("#369 TC-14: does not verify a session") {
                val library = randomLibrary().copy(isActive = true)
                val authRepository = TestAuthRepository()
                authRepository.signedIn.value = false
                val librarySwitch = TestLibrarySwitch()
                librarySwitch.pending.value = PendingOpening(library.id, SwitchKind.Switch)

                sut(
                    TestServerConfigurationRepository(initialAddress = randomServerAddress()),
                    authRepository,
                    librarySwitch,
                    TestLibrariesRepository(listOf(library)),
                )

                authRepository.verifySessionCalls shouldBe 0
            }
        }
    }
})

/** The ways a saved switch marker can be stale at launch. */
private enum class StaleMarker { NotActive, Missing }
