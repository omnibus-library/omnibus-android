package com.omnibus.omnibus.feature.auth

import com.omnibus.omnibus.data.auth.AuthSignInResult
import com.omnibus.omnibus.data.auth.FakeAuthRepository
import com.omnibus.omnibus.data.configuration.FakeServerConfigurationRepository
import com.omnibus.omnibus.testing.CollectingScope
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest : DescribeSpec({

    lateinit var authRepository: FakeAuthRepository
    lateinit var serverConfigRepository: FakeServerConfigurationRepository
    lateinit var collecting: CollectingScope

    fun createViewModel() = LoginViewModel(authRepository, serverConfigRepository).also {
        collecting.collect(it.uiState)
    }

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    beforeEach {
        authRepository = FakeAuthRepository()
        serverConfigRepository = FakeServerConfigurationRepository(initialAddress = SERVER)
        collecting = CollectingScope()
    }

    afterEach { collecting.cancel() }

    describe("initial state") {

        it("is NOT_LOGGED_IN and shows the saved server") {
            createViewModel().uiState.value shouldBe
                LoginUIState(loginStatus = LoginStatus.NOT_LOGGED_IN, serverAddress = SERVER)
        }

        listOf(null, "", "   ").forEach { saved ->
            it("is MISSING_SERVER_CONFIG when the saved server is ${saved?.let { "\"$it\"" } ?: "null"}") {
                serverConfigRepository.savedAddress.value = saved

                createViewModel().uiState.value.loginStatus shouldBe LoginStatus.MISSING_SERVER_CONFIG
            }
        }

        it("becomes MISSING_SERVER_CONFIG if the saved server is removed") {
            val viewModel = createViewModel()

            serverConfigRepository.savedAddress.value = null

            viewModel.uiState.value.loginStatus shouldBe LoginStatus.MISSING_SERVER_CONFIG
        }
    }

    describe("attemptLogin") {

        it("signs in with the entered credentials") {
            val viewModel = createViewModel()

            viewModel.attemptLogin(USERNAME, PASSWORD)

            authRepository.signInAttempts shouldBe listOf(USERNAME to PASSWORD)
        }

        it("is AUTH_PENDING while signing in") {
            authRepository.signInGate = CompletableDeferred()
            val viewModel = createViewModel()

            viewModel.attemptLogin(USERNAME, PASSWORD)

            viewModel.uiState.value.loginStatus shouldBe LoginStatus.AUTH_PENDING
        }

        it("ignores further attempts while one is pending") {
            val gate = CompletableDeferred<Unit>()
            authRepository.signInGate = gate
            val viewModel = createViewModel()

            viewModel.attemptLogin(USERNAME, PASSWORD)
            viewModel.attemptLogin(USERNAME, "again")
            gate.complete(Unit)

            authRepository.signInAttempts shouldBe listOf(USERNAME to PASSWORD)
            viewModel.uiState.value.loginStatus shouldBe LoginStatus.LOGGED_IN
        }

        it("becomes LOGGED_IN when sign-in succeeds") {
            val viewModel = createViewModel()

            viewModel.attemptLogin(USERNAME, PASSWORD)

            viewModel.uiState.value.loginStatus shouldBe LoginStatus.LOGGED_IN
        }

        it("becomes MISSING_SERVER_CONFIG when there is no valid server to sign in to") {
            authRepository.signInResult = AuthSignInResult.NoServer
            val viewModel = createViewModel()

            viewModel.attemptLogin(USERNAME, PASSWORD)

            viewModel.uiState.value.loginStatus shouldBe LoginStatus.MISSING_SERVER_CONFIG
        }

        mapOf(
            "rejects the credentials" to AuthSignInResult.Failure,
            "errors" to AuthSignInResult.Error,
        ).forEach { (description, result) ->
            it("becomes AUTH_FAILED when sign-in $description, and allows a retry") {
                authRepository.signInResult = result
                val viewModel = createViewModel()

                viewModel.attemptLogin(USERNAME, PASSWORD)
                viewModel.uiState.value.loginStatus shouldBe LoginStatus.AUTH_FAILED

                authRepository.signInResult = AuthSignInResult.Success(authToken = "token")
                viewModel.attemptLogin(USERNAME, PASSWORD)
                viewModel.uiState.value.loginStatus shouldBe LoginStatus.LOGGED_IN
            }
        }
    }
}) {
    private companion object {
        const val SERVER = "http://10.0.0.2:3000"
        const val USERNAME = "hunter"
        const val PASSWORD = "hunter2"
    }
}
