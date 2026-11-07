package com.omnibus.omnibus

import com.omnibus.omnibus.data.auth.AuthSessionResult
import com.omnibus.omnibus.data.auth.TestAuthRepository
import com.omnibus.omnibus.data.configuration.TestServerConfigurationRepository
import com.omnibus.omnibus.data.configuration.randomServerAddress
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
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
    ) = MainViewModel(serverRepository, authRepository)

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
})
