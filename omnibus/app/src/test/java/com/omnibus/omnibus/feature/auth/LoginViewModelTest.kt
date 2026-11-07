package com.omnibus.omnibus.feature.auth

import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.auth.AuthRegisterResult
import com.omnibus.omnibus.data.auth.AuthSignInResult
import com.omnibus.omnibus.data.auth.TestAuthRepository
import com.omnibus.omnibus.data.configuration.TestServerConfigurationRepository
import com.omnibus.omnibus.data.configuration.randomServerAddress
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

private fun savedServer() = TestServerConfigurationRepository(randomServerAddress())

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun sut(
        authRepository: TestAuthRepository = TestAuthRepository(),
        serverConfigRepository: TestServerConfigurationRepository = TestServerConfigurationRepository(),
    ) = LoginViewModel(authRepository, serverConfigRepository).also { vm ->
        vm.viewModelScope.launch { vm.uiState.collect {} }
    }

    describe("the state") {
        context("before an attempt") {
            context("when a server is saved") {
                it("is NOT_LOGGED_IN, showing the saved server") {
                    val server = randomServerAddress()

                    sut(serverConfigRepository = TestServerConfigurationRepository(server)).uiState.value shouldBe
                        LoginUIState(loginStatus = LoginStatus.NOT_LOGGED_IN, serverAddress = server)
                }
            }

            it("is MISSING_SERVER_CONFIG for a null, empty or blank saved server") {
                checkAll(Exhaustive.of(null, "", "   ")) { saved ->
                    val vm = sut(serverConfigRepository = TestServerConfigurationRepository(saved))

                    vm.uiState.value.loginStatus shouldBe LoginStatus.MISSING_SERVER_CONFIG
                }
            }
        }
    }

    describe("signing in") {
        context("when a server is saved") {
            it("sends the entered credentials") {
                val username = randomString()
                val password = randomString()
                val auth = TestAuthRepository().apply { signInGate = CompletableDeferred() }

                sut(auth, savedServer()).attemptLogin(username, password)

                auth.signInAttempts shouldBe listOf(username to password)
            }

            it("TC-26 to TC-29: maps each sign-in result to its status") {
                checkAll(
                    Exhaustive.of(
                        AuthSignInResult.Success(authToken = randomString()) to LoginStatus.LOGGED_IN,
                        AuthSignInResult.NoServer to LoginStatus.MISSING_SERVER_CONFIG,
                        AuthSignInResult.InvalidCredentials to LoginStatus.INVALID_CREDENTIALS,
                        AuthSignInResult.TooManyAttempts to LoginStatus.TOO_MANY_ATTEMPTS,
                        AuthSignInResult.Unreachable to LoginStatus.SERVER_UNREACHABLE,
                        AuthSignInResult.ServerError to LoginStatus.SERVER_ERROR,
                    ),
                ) { (result, expected) ->
                    val auth = TestAuthRepository()
                    auth.signInResult = result
                    val vm = sut(auth, savedServer())

                    vm.attemptLogin(randomString(), randomString())

                    vm.uiState.value.loginStatus shouldBe expected
                }
            }

            context("when an attempt failed") {
                it("TC-26 to TC-29: allows a retry") {
                    val auth = TestAuthRepository()
                    auth.signInResult = AuthSignInResult.Unreachable
                    val vm = sut(auth, savedServer())
                    vm.attemptLogin(randomString(), randomString())

                    auth.signInResult = AuthSignInResult.Success(authToken = randomString())
                    vm.attemptLogin(randomString(), randomString())

                    vm.uiState.value.loginStatus shouldBe LoginStatus.LOGGED_IN
                }
            }

            context("when an attempt is pending") {
                it("TC-30: is AUTH_PENDING") {
                    val auth = TestAuthRepository()
                    auth.signInGate = CompletableDeferred()
                    val vm = sut(auth, savedServer())

                    vm.attemptLogin(randomString(), randomString())

                    vm.uiState.value.loginStatus shouldBe LoginStatus.AUTH_PENDING
                }

                it("TC-30: ignores a further attempt") {
                    val username = randomString()
                    val password = randomString()
                    val auth = TestAuthRepository()
                    auth.signInGate = CompletableDeferred()
                    val vm = sut(auth, savedServer())

                    vm.attemptLogin(username, password)
                    vm.attemptLogin(randomString(), randomString())

                    auth.signInAttempts shouldBe listOf(username to password)
                }

                it("TC-63: ignores a register") {
                    val auth = TestAuthRepository()
                    auth.signInGate = CompletableDeferred()
                    val vm = sut(auth, savedServer())

                    vm.attemptLogin(randomString(), randomString())
                    vm.attemptRegister(randomString(), randomString())

                    auth.registerAttempts.shouldBeEmpty()
                }
            }
        }
    }

    describe("registering") {
        context("when a server is saved") {
            it("TC-53: sends the entered credentials") {
                val username = randomString()
                val password = randomString()
                val auth = TestAuthRepository().apply { registerGate = CompletableDeferred() }

                sut(auth, savedServer()).attemptRegister(username, password)

                auth.registerAttempts shouldBe listOf(username to password)
            }

            it("TC-55 to TC-62: maps each registration result to its status") {
                checkAll(
                    Exhaustive.of(
                        AuthRegisterResult.Success(authToken = randomString()) to LoginStatus.LOGGED_IN,
                        AuthRegisterResult.NoServer to LoginStatus.MISSING_SERVER_CONFIG,
                        AuthRegisterResult.UsernameTaken to LoginStatus.USERNAME_TAKEN,
                        AuthRegisterResult.RegistrationDisabled to LoginStatus.REGISTRATION_DISABLED,
                        AuthRegisterResult.InvalidDetails to LoginStatus.INVALID_DETAILS,
                        AuthRegisterResult.TooManyAttempts to LoginStatus.TOO_MANY_ATTEMPTS,
                        AuthRegisterResult.Unreachable to LoginStatus.SERVER_UNREACHABLE,
                        AuthRegisterResult.ServerError to LoginStatus.SERVER_ERROR,
                    ),
                ) { (result, expected) ->
                    val auth = TestAuthRepository()
                    auth.registerResult = result
                    val vm = sut(auth, savedServer())

                    vm.attemptRegister(randomString(), randomString())

                    vm.uiState.value.loginStatus shouldBe expected
                }
            }

            context("when an attempt failed") {
                it("TC-57 to TC-62: allows a retry") {
                    val auth = TestAuthRepository()
                    auth.registerResult = AuthRegisterResult.UsernameTaken
                    val vm = sut(auth, savedServer())
                    vm.attemptRegister(randomString(), randomString())

                    auth.registerResult = AuthRegisterResult.Success(authToken = randomString())
                    vm.attemptRegister(randomString(), randomString())

                    vm.uiState.value.loginStatus shouldBe LoginStatus.LOGGED_IN
                }
            }

            context("when an attempt is pending") {
                it("TC-54: is AUTH_PENDING") {
                    val auth = TestAuthRepository()
                    auth.registerGate = CompletableDeferred()
                    val vm = sut(auth, savedServer())

                    vm.attemptRegister(randomString(), randomString())

                    vm.uiState.value.loginStatus shouldBe LoginStatus.AUTH_PENDING
                }

                it("TC-54: ignores a further attempt") {
                    val username = randomString()
                    val password = randomString()
                    val auth = TestAuthRepository()
                    auth.registerGate = CompletableDeferred()
                    val vm = sut(auth, savedServer())

                    vm.attemptRegister(username, password)
                    vm.attemptRegister(randomString(), randomString())

                    auth.registerAttempts shouldBe listOf(username to password)
                }
            }
        }
    }
})
