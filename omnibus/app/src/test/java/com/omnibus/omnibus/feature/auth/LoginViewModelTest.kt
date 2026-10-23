package com.omnibus.omnibus.feature.auth

import com.omnibus.omnibus.data.auth.AuthRegisterResult
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

        it("TC-30: is AUTH_PENDING while signing in") {
            authRepository.signInGate = CompletableDeferred()
            val viewModel = createViewModel()

            viewModel.attemptLogin(USERNAME, PASSWORD)

            viewModel.uiState.value.loginStatus shouldBe LoginStatus.AUTH_PENDING
        }

        it("TC-30: ignores further attempts while one is pending") {
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

        listOf(
            Triple("TC-26", AuthSignInResult.InvalidCredentials, LoginStatus.INVALID_CREDENTIALS),
            Triple("TC-27", AuthSignInResult.TooManyAttempts, LoginStatus.TOO_MANY_ATTEMPTS),
            Triple("TC-28", AuthSignInResult.Unreachable, LoginStatus.SERVER_UNREACHABLE),
            Triple("TC-29", AuthSignInResult.ServerError, LoginStatus.SERVER_ERROR),
        ).forEach { (id, result, expected) ->
            it("$id: becomes $expected when sign-in returns $result, and allows a retry") {
                authRepository.signInResult = result
                val viewModel = createViewModel()

                viewModel.attemptLogin(USERNAME, PASSWORD)
                viewModel.uiState.value.loginStatus shouldBe expected

                authRepository.signInResult = AuthSignInResult.Success(authToken = "token")
                viewModel.attemptLogin(USERNAME, PASSWORD)
                viewModel.uiState.value.loginStatus shouldBe LoginStatus.LOGGED_IN
            }
        }

        it("TC-30: ignores attempts while pending even after an earlier failure, then settles on the result") {
            authRepository.signInResult = AuthSignInResult.InvalidCredentials
            val viewModel = createViewModel()
            viewModel.attemptLogin(USERNAME, PASSWORD)
            viewModel.uiState.value.loginStatus shouldBe LoginStatus.INVALID_CREDENTIALS

            val gate = CompletableDeferred<Unit>()
            authRepository.signInGate = gate
            authRepository.signInResult = AuthSignInResult.Unreachable
            viewModel.attemptLogin(USERNAME, PASSWORD)
            viewModel.attemptLogin(USERNAME, PASSWORD)
            viewModel.uiState.value.loginStatus shouldBe LoginStatus.AUTH_PENDING
            authRepository.signInAttempts.size shouldBe 2

            gate.complete(Unit)

            viewModel.uiState.value.loginStatus shouldBe LoginStatus.SERVER_UNREACHABLE
        }

        it("TC-28: keeps the saved server address through a failure") {
            authRepository.signInResult = AuthSignInResult.Unreachable
            val viewModel = createViewModel()

            viewModel.attemptLogin(USERNAME, PASSWORD)

            viewModel.uiState.value.serverAddress shouldBe SERVER
        }
    }

    describe("attemptRegister") {

        it("TC-53: registers with the entered credentials and does not sign in") {
            val viewModel = createViewModel()

            viewModel.attemptRegister(USERNAME, PASSWORD)

            authRepository.registerAttempts shouldBe listOf(USERNAME to PASSWORD)
            authRepository.signInAttempts shouldBe emptyList()
        }

        it("TC-54: is AUTH_PENDING while registering") {
            authRepository.registerGate = CompletableDeferred()
            val viewModel = createViewModel()

            viewModel.attemptRegister(USERNAME, PASSWORD)

            viewModel.uiState.value.loginStatus shouldBe LoginStatus.AUTH_PENDING
        }

        it("TC-54: ignores further attempts while one is pending") {
            val gate = CompletableDeferred<Unit>()
            authRepository.registerGate = gate
            val viewModel = createViewModel()

            viewModel.attemptRegister(USERNAME, PASSWORD)
            viewModel.attemptRegister(USERNAME, "again")
            gate.complete(Unit)

            authRepository.registerAttempts shouldBe listOf(USERNAME to PASSWORD)
            viewModel.uiState.value.loginStatus shouldBe LoginStatus.LOGGED_IN
        }

        it("TC-54: ignores attempts while pending even after an earlier failure, then settles on the result") {
            authRepository.registerResult = AuthRegisterResult.UsernameTaken
            val viewModel = createViewModel()
            viewModel.attemptRegister(USERNAME, PASSWORD)
            viewModel.uiState.value.loginStatus shouldBe LoginStatus.USERNAME_TAKEN

            val gate = CompletableDeferred<Unit>()
            authRepository.registerGate = gate
            authRepository.registerResult = AuthRegisterResult.Unreachable
            viewModel.attemptRegister(USERNAME, PASSWORD)
            viewModel.attemptRegister(USERNAME, PASSWORD)
            viewModel.uiState.value.loginStatus shouldBe LoginStatus.AUTH_PENDING
            authRepository.registerAttempts.size shouldBe 2

            gate.complete(Unit)

            viewModel.uiState.value.loginStatus shouldBe LoginStatus.SERVER_UNREACHABLE
        }

        it("TC-55: becomes LOGGED_IN when registration succeeds") {
            val viewModel = createViewModel()

            viewModel.attemptRegister(USERNAME, PASSWORD)

            viewModel.uiState.value.loginStatus shouldBe LoginStatus.LOGGED_IN
        }

        it("TC-56: becomes MISSING_SERVER_CONFIG when there is no valid server to register on") {
            authRepository.registerResult = AuthRegisterResult.NoServer
            val viewModel = createViewModel()

            viewModel.attemptRegister(USERNAME, PASSWORD)

            viewModel.uiState.value.loginStatus shouldBe LoginStatus.MISSING_SERVER_CONFIG
        }

        it("TC-56: reports MISSING_SERVER_CONFIG when no server is saved, even if register would succeed") {
            serverConfigRepository.savedAddress.value = ""
            val viewModel = createViewModel()

            viewModel.attemptRegister(USERNAME, PASSWORD)

            authRepository.registerAttempts shouldBe listOf(USERNAME to PASSWORD)
            viewModel.uiState.value.loginStatus shouldBe LoginStatus.MISSING_SERVER_CONFIG
        }

        listOf(
            Triple("TC-57", AuthRegisterResult.UsernameTaken, LoginStatus.USERNAME_TAKEN),
            Triple("TC-58", AuthRegisterResult.RegistrationDisabled, LoginStatus.REGISTRATION_DISABLED),
            Triple("TC-59", AuthRegisterResult.InvalidDetails, LoginStatus.INVALID_DETAILS),
            Triple("TC-60", AuthRegisterResult.TooManyAttempts, LoginStatus.TOO_MANY_ATTEMPTS),
            Triple("TC-61", AuthRegisterResult.Unreachable, LoginStatus.SERVER_UNREACHABLE),
            Triple("TC-62", AuthRegisterResult.ServerError, LoginStatus.SERVER_ERROR),
        ).forEach { (id, result, expected) ->
            it("$id: becomes $expected when registration returns $result, and allows a retry") {
                authRepository.registerResult = result
                val viewModel = createViewModel()

                viewModel.attemptRegister(USERNAME, PASSWORD)
                viewModel.uiState.value.loginStatus shouldBe expected

                authRepository.registerResult = AuthRegisterResult.Success(authToken = "token")
                viewModel.attemptRegister(USERNAME, PASSWORD)
                viewModel.uiState.value.loginStatus shouldBe LoginStatus.LOGGED_IN
            }
        }

        it("TC-61: keeps the saved server address when registration can't reach the server") {
            authRepository.registerResult = AuthRegisterResult.Unreachable
            val viewModel = createViewModel()

            viewModel.attemptRegister(USERNAME, PASSWORD)

            viewModel.uiState.value shouldBe LoginUIState(
                loginStatus = LoginStatus.SERVER_UNREACHABLE,
                serverAddress = SERVER,
            )
        }

        it("TC-63: a pending login ignores register, then a later register is recorded") {
            val gate = CompletableDeferred<Unit>()
            authRepository.signInGate = gate
            val viewModel = createViewModel()

            viewModel.attemptLogin(USERNAME, PASSWORD)
            viewModel.attemptRegister("ignored", "ignored")
            viewModel.uiState.value.loginStatus shouldBe LoginStatus.AUTH_PENDING

            gate.complete(Unit)
            viewModel.uiState.value.loginStatus shouldBe LoginStatus.LOGGED_IN

            viewModel.attemptRegister(USERNAME, PASSWORD)

            authRepository.signInAttempts shouldBe listOf(USERNAME to PASSWORD)
            authRepository.registerAttempts shouldBe listOf(USERNAME to PASSWORD)
            viewModel.uiState.value.loginStatus shouldBe LoginStatus.LOGGED_IN
        }

        it("TC-63: a pending register ignores login, and only the register is recorded") {
            val gate = CompletableDeferred<Unit>()
            authRepository.registerGate = gate
            val viewModel = createViewModel()

            viewModel.attemptRegister(USERNAME, PASSWORD)
            viewModel.uiState.value.loginStatus shouldBe LoginStatus.AUTH_PENDING
            viewModel.attemptLogin(USERNAME, "other")
            gate.complete(Unit)

            authRepository.registerAttempts shouldBe listOf(USERNAME to PASSWORD)
            authRepository.signInAttempts shouldBe emptyList()
            viewModel.uiState.value.loginStatus shouldBe LoginStatus.LOGGED_IN
        }
    }

    describe("LoginStatus.isFailure") {

        val signInFailures = setOf(
            LoginStatus.INVALID_CREDENTIALS,
            LoginStatus.TOO_MANY_ATTEMPTS,
            LoginStatus.SERVER_UNREACHABLE,
            LoginStatus.SERVER_ERROR,
        )

        val registerFailures = setOf(
            LoginStatus.USERNAME_TAKEN,
            LoginStatus.REGISTRATION_DISABLED,
            LoginStatus.INVALID_DETAILS,
        )

        signInFailures.forEach { status ->
            it("TC-31: is true for $status") {
                status.isFailure shouldBe true
            }
        }

        registerFailures.forEach { status ->
            it("TC-64: is true for $status") {
                status.isFailure shouldBe true
            }
        }

        (LoginStatus.entries.toSet() - signInFailures - registerFailures).forEach { status ->
            it("TC-31: is false for $status") {
                status.isFailure shouldBe false
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
