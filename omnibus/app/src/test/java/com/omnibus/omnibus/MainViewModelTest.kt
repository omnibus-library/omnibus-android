package com.omnibus.omnibus

import com.omnibus.omnibus.data.auth.AuthSessionResult
import com.omnibus.omnibus.data.auth.FakeAuthRepository
import com.omnibus.omnibus.data.configuration.FakeServerConfigurationRepository
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

    lateinit var authRepository: FakeAuthRepository

    fun createViewModel(serverRepository: FakeServerConfigurationRepository) =
        MainViewModel(serverRepository, authRepository)

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    beforeEach { authRepository = FakeAuthRepository() }

    describe("startDestination") {
        it("TC-32: opens server configuration when no server is saved") {
            val viewModel = createViewModel(FakeServerConfigurationRepository(initialAddress = null))

            viewModel.startDestination.value shouldBe StartDestination.ServerConfiguration
        }

        it("TC-32: opens server configuration when the saved server is blank") {
            val viewModel = createViewModel(FakeServerConfigurationRepository(initialAddress = "  "))

            viewModel.startDestination.value shouldBe StartDestination.ServerConfiguration
        }

        it("TC-32: opens server configuration when no server is saved, even if a token is stored") {
            authRepository.signedIn.value = true

            val viewModel = createViewModel(FakeServerConfigurationRepository(initialAddress = null))

            viewModel.startDestination.value shouldBe StartDestination.ServerConfiguration
        }

        it("TC-33: opens login when a server is saved and no token is stored") {
            authRepository.signedIn.value = false

            val viewModel =
                createViewModel(FakeServerConfigurationRepository(initialAddress = "http://10.0.0.2:3000"))

            viewModel.startDestination.value shouldBe StartDestination.Login
        }

        it("TC-34: opens the signed-in shell when a server and a token are saved") {
            authRepository.signedIn.value = true

            val viewModel =
                createViewModel(FakeServerConfigurationRepository(initialAddress = "http://10.0.0.2:3000"))

            viewModel.startDestination.value shouldBe StartDestination.SignedIn
        }

        it("TC-35: opens server configuration when the saved config can't be read") {
            val repository = FakeServerConfigurationRepository(initialAddress = "http://10.0.0.2:3000")
                .apply { readFailure = IOException("corrupt") }

            createViewModel(repository).startDestination.value shouldBe StartDestination.ServerConfiguration
        }

        it("TC-35: opens server configuration when the config can't be read, even if a token is stored") {
            authRepository.signedIn.value = true
            val repository = FakeServerConfigurationRepository(initialAddress = "http://10.0.0.2:3000")
                .apply { readFailure = IOException("corrupt") }

            createViewModel(repository).startDestination.value shouldBe StartDestination.ServerConfiguration
        }

        it("TC-37: does not change after the config is edited post-launch") {
            val repository = FakeServerConfigurationRepository(initialAddress = null)
            val viewModel = createViewModel(repository)

            repository.saveServerConfig("http://10.0.0.2:3000")

            viewModel.startDestination.value shouldBe StartDestination.ServerConfiguration
        }

        it("TC-37: stays on login when a session is saved after launch") {
            authRepository.signedIn.value = false
            val viewModel =
                createViewModel(FakeServerConfigurationRepository(initialAddress = "http://10.0.0.2:3000"))

            authRepository.signedIn.value = true

            viewModel.startDestination.value shouldBe StartDestination.Login
        }

        it("TC-37: stays on the signed-in shell when the session ends after launch") {
            authRepository.signedIn.value = true
            val viewModel =
                createViewModel(FakeServerConfigurationRepository(initialAddress = "http://10.0.0.2:3000"))

            authRepository.signedIn.value = false

            viewModel.startDestination.value shouldBe StartDestination.SignedIn
        }

        it("TC-37: stays Loading until the session state has been read") {
            val gate = CompletableDeferred<Unit>()
            authRepository.isSignedInGate = gate
            authRepository.signedIn.value = true
            val viewModel =
                createViewModel(FakeServerConfigurationRepository(initialAddress = "http://10.0.0.2:3000"))

            viewModel.startDestination.value shouldBe StartDestination.Loading

            gate.complete(Unit)

            viewModel.startDestination.value shouldBe StartDestination.SignedIn
        }
    }

    describe("session verification") {

        it("TC-38: verifies the session exactly once when launching into the signed-in shell") {
            authRepository.signedIn.value = true

            createViewModel(FakeServerConfigurationRepository(initialAddress = "http://10.0.0.2:3000"))

            authRepository.verifySessionCalls shouldBe 1
        }

        it("TC-38: doesn't verify again when the session state changes afterwards") {
            authRepository.signedIn.value = true
            createViewModel(FakeServerConfigurationRepository(initialAddress = "http://10.0.0.2:3000"))

            authRepository.signedIn.value = false
            authRepository.signedIn.value = true

            authRepository.verifySessionCalls shouldBe 1
        }

        it("TC-38: doesn't verify when launching into login") {
            authRepository.signedIn.value = false

            createViewModel(FakeServerConfigurationRepository(initialAddress = "http://10.0.0.2:3000"))

            authRepository.verifySessionCalls shouldBe 0
        }

        it("TC-38: doesn't verify when launching into server configuration") {
            authRepository.signedIn.value = true

            createViewModel(FakeServerConfigurationRepository(initialAddress = null))

            authRepository.verifySessionCalls shouldBe 0
        }

        it("TC-38: still opens the signed-in shell when verification can't reach the server") {
            authRepository.signedIn.value = true
            authRepository.verifySessionResult = AuthSessionResult.Unreachable

            val viewModel =
                createViewModel(FakeServerConfigurationRepository(initialAddress = "http://10.0.0.2:3000"))

            viewModel.startDestination.value shouldBe StartDestination.SignedIn
            authRepository.verifySessionCalls shouldBe 1
        }
    }

    describe("isSignedIn") {

        it("TC-39: is null until the session state has been read") {
            authRepository.isSignedInGate = CompletableDeferred()

            val viewModel =
                createViewModel(FakeServerConfigurationRepository(initialAddress = "http://10.0.0.2:3000"))

            viewModel.isSignedIn.value shouldBe null
        }

        it("TC-39: mirrors the repository, going from true to false when the session ends") {
            authRepository.signedIn.value = true
            val viewModel =
                createViewModel(FakeServerConfigurationRepository(initialAddress = "http://10.0.0.2:3000"))
            viewModel.isSignedIn.value shouldBe true

            authRepository.signedIn.value = false

            viewModel.isSignedIn.value shouldBe false
        }

        it("TC-39: is false when no session is stored") {
            authRepository.signedIn.value = false

            val viewModel =
                createViewModel(FakeServerConfigurationRepository(initialAddress = "http://10.0.0.2:3000"))

            viewModel.isSignedIn.value shouldBe false
        }

        it("TC-39: becomes true when a session is saved after launch") {
            authRepository.signedIn.value = false
            val viewModel =
                createViewModel(FakeServerConfigurationRepository(initialAddress = "http://10.0.0.2:3000"))

            authRepository.signedIn.value = true

            viewModel.isSignedIn.value shouldBe true
        }
    }
})
