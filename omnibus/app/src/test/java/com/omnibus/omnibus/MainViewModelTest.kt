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

    describe("startDestination") {
        it("TC-32: opens server configuration when no server is saved") {
            val viewModel = sut(TestServerConfigurationRepository(initialAddress = null))

            viewModel.startDestination.value shouldBe StartDestination.ServerConfiguration
        }

        it("TC-32: opens server configuration when the saved server is blank") {
            val viewModel = sut(TestServerConfigurationRepository(initialAddress = "  "))

            viewModel.startDestination.value shouldBe StartDestination.ServerConfiguration
        }

        it("TC-32: opens server configuration when no server is saved, even if a token is stored") {
            val authRepository = TestAuthRepository()
            authRepository.signedIn.value = true

            val viewModel = sut(TestServerConfigurationRepository(initialAddress = null), authRepository)

            viewModel.startDestination.value shouldBe StartDestination.ServerConfiguration
        }

        it("TC-33: opens login when a server is saved and no token is stored") {
            val authRepository = TestAuthRepository()
            authRepository.signedIn.value = false

            val viewModel =
                sut(TestServerConfigurationRepository(initialAddress = randomServerAddress()), authRepository)

            viewModel.startDestination.value shouldBe StartDestination.Login
        }

        it("TC-34: opens the signed-in shell when a server and a token are saved") {
            val authRepository = TestAuthRepository()
            authRepository.signedIn.value = true

            val viewModel =
                sut(TestServerConfigurationRepository(initialAddress = randomServerAddress()), authRepository)

            viewModel.startDestination.value shouldBe StartDestination.SignedIn
        }

        it("TC-35: opens server configuration when the saved config can't be read") {
            val repository = TestServerConfigurationRepository(initialAddress = randomServerAddress())
                .apply { readFailure = IOException("corrupt") }

            sut(repository).startDestination.value shouldBe StartDestination.ServerConfiguration
        }

        it("TC-35: opens server configuration when the config can't be read, even if a token is stored") {
            val authRepository = TestAuthRepository()
            authRepository.signedIn.value = true
            val repository = TestServerConfigurationRepository(initialAddress = randomServerAddress())
                .apply { readFailure = IOException("corrupt") }

            sut(repository, authRepository).startDestination.value shouldBe StartDestination.ServerConfiguration
        }

        it("TC-37: does not change after the config is edited post-launch") {
            val repository = TestServerConfigurationRepository(initialAddress = null)
            val viewModel = sut(repository)

            repository.saveServerConfig(randomServerAddress())

            viewModel.startDestination.value shouldBe StartDestination.ServerConfiguration
        }

        it("TC-37: stays on login when a session is saved after launch") {
            val authRepository = TestAuthRepository()
            authRepository.signedIn.value = false
            val viewModel =
                sut(TestServerConfigurationRepository(initialAddress = randomServerAddress()), authRepository)

            authRepository.signedIn.value = true

            viewModel.startDestination.value shouldBe StartDestination.Login
        }

        it("TC-37: stays on the signed-in shell when the session ends after launch") {
            val authRepository = TestAuthRepository()
            authRepository.signedIn.value = true
            val viewModel =
                sut(TestServerConfigurationRepository(initialAddress = randomServerAddress()), authRepository)

            authRepository.signedIn.value = false

            viewModel.startDestination.value shouldBe StartDestination.SignedIn
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

        it("TC-38: verifies the session exactly once when launching into the signed-in shell") {
            val authRepository = TestAuthRepository()
            authRepository.signedIn.value = true

            sut(TestServerConfigurationRepository(initialAddress = randomServerAddress()), authRepository)

            authRepository.verifySessionCalls shouldBe 1
        }

        it("TC-38: doesn't verify again when the session state changes afterwards") {
            val authRepository = TestAuthRepository()
            authRepository.signedIn.value = true
            sut(TestServerConfigurationRepository(initialAddress = randomServerAddress()), authRepository)

            authRepository.signedIn.value = false
            authRepository.signedIn.value = true

            authRepository.verifySessionCalls shouldBe 1
        }

        it("TC-38: doesn't verify when launching into login") {
            val authRepository = TestAuthRepository()
            authRepository.signedIn.value = false

            sut(TestServerConfigurationRepository(initialAddress = randomServerAddress()), authRepository)

            authRepository.verifySessionCalls shouldBe 0
        }

        it("TC-38: doesn't verify when launching into server configuration") {
            val authRepository = TestAuthRepository()
            authRepository.signedIn.value = true

            sut(TestServerConfigurationRepository(initialAddress = null), authRepository)

            authRepository.verifySessionCalls shouldBe 0
        }

        it("TC-38: still opens the signed-in shell when verification can't reach the server") {
            val authRepository = TestAuthRepository()
            authRepository.signedIn.value = true
            authRepository.verifySessionResult = AuthSessionResult.Unreachable

            val viewModel =
                sut(TestServerConfigurationRepository(initialAddress = randomServerAddress()), authRepository)

            viewModel.startDestination.value shouldBe StartDestination.SignedIn
            authRepository.verifySessionCalls shouldBe 1
        }
    }

    describe("isSignedIn") {

        it("TC-39: is null until the session state has been read") {
            val authRepository = TestAuthRepository()
            authRepository.isSignedInGate = CompletableDeferred()

            val viewModel =
                sut(TestServerConfigurationRepository(initialAddress = randomServerAddress()), authRepository)

            viewModel.isSignedIn.value shouldBe null
        }

        it("TC-39: mirrors the repository, going from true to false when the session ends") {
            val authRepository = TestAuthRepository()
            authRepository.signedIn.value = true
            val viewModel =
                sut(TestServerConfigurationRepository(initialAddress = randomServerAddress()), authRepository)
            viewModel.isSignedIn.value shouldBe true

            authRepository.signedIn.value = false

            viewModel.isSignedIn.value shouldBe false
        }

        it("TC-39: is false when no session is stored") {
            val authRepository = TestAuthRepository()
            authRepository.signedIn.value = false

            val viewModel =
                sut(TestServerConfigurationRepository(initialAddress = randomServerAddress()), authRepository)

            viewModel.isSignedIn.value shouldBe false
        }

        it("TC-39: becomes true when a session is saved after launch") {
            val authRepository = TestAuthRepository()
            authRepository.signedIn.value = false
            val viewModel =
                sut(TestServerConfigurationRepository(initialAddress = randomServerAddress()), authRepository)

            authRepository.signedIn.value = true

            viewModel.isSignedIn.value shouldBe true
        }
    }
})
