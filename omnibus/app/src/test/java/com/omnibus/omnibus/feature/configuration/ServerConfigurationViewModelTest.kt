package com.omnibus.omnibus.feature.configuration

import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.configuration.ServerValidationResult
import com.omnibus.omnibus.data.configuration.TestServerConfigurationRepository
import com.omnibus.omnibus.data.configuration.randomServerAddress
import com.omnibus.omnibus.data.libraries.randomLibrary
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
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

@OptIn(ExperimentalCoroutinesApi::class)
class ServerConfigurationViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun sut(repository: TestServerConfigurationRepository = TestServerConfigurationRepository()) =
        ServerConfigurationViewModel(repository).also { vm ->
            vm.viewModelScope.launch { vm.uiState.collect {} }
        }

    describe("the state") {
        context("before a submission") {
            context("when a server is saved") {
                it("exposes the saved address to prefill the field") {
                    val server = randomServerAddress()

                    sut(TestServerConfigurationRepository(server)).uiState.value.savedConfigurationValue shouldBe server
                }
            }
        }
    }

    describe("submitting an address") {
        it("validates the address as entered") {
            val address = randomString()
            val repository = TestServerConfigurationRepository().apply { validationGate = CompletableDeferred() }

            sut(repository).submitServerUrl(address)

            repository.validatedAddresses shouldBe listOf(address)
        }

        context("while the address is being checked") {
            it("is PENDING") {
                val repository = TestServerConfigurationRepository()
                repository.validationGate = CompletableDeferred()
                val vm = sut(repository)

                vm.submitServerUrl(randomString())

                vm.uiState.value.submissionStatus shouldBe ServerConfigSubmissionStatus.PENDING
            }
        }

        context("when the server is found") {
            it("saves the normalized address, not the raw input") {
                val normalized = randomServerAddress()
                val repository = TestServerConfigurationRepository()
                repository.validationResult = { ServerValidationResult.Success(normalized) }

                sut(repository).submitServerUrl(randomString())

                repository.savedAddress.value shouldBe normalized
            }
        }

        context("when the address is rejected") {
            it("saves nothing, for a missing server or a malformed address") {
                checkAll(
                    Exhaustive.of(ServerValidationResult.ServerNotFound, ServerValidationResult.InvalidAddressFormat),
                ) { result ->
                    val repository = TestServerConfigurationRepository()
                    repository.validationResult = { result }

                    sut(repository).submitServerUrl(randomString())

                    repository.savedAddress.value.shouldBeNull()
                }
            }
        }
    }

    describe("the submission's status") {
        it("maps a found server to ACCEPTED, a missing one to NOT_ACCEPTED, a malformed address to ERROR") {
            checkAll(
                Exhaustive.of(
                    ServerValidationResult.Success(randomServerAddress()) to ServerConfigSubmissionStatus.ACCEPTED,
                    ServerValidationResult.ServerNotFound to ServerConfigSubmissionStatus.NOT_ACCEPTED,
                    ServerValidationResult.InvalidAddressFormat to ServerConfigSubmissionStatus.ERROR,
                ),
            ) { (result, expected) ->
                val repository = TestServerConfigurationRepository()
                repository.validationResult = { result }
                val vm = sut(repository)

                vm.submitServerUrl(randomString())

                vm.uiState.value.submissionStatus shouldBe expected
            }
        }
    }

    describe("the submit button") {
        it("is enabled only for a status that can be submitted again") {
            checkAll(
                Exhaustive.of(
                    ServerConfigSubmissionStatus.UNSUBMITTED to true,
                    ServerConfigSubmissionStatus.PENDING to false,
                    ServerConfigSubmissionStatus.ACCEPTED to false,
                    ServerConfigSubmissionStatus.NOT_ACCEPTED to true,
                    ServerConfigSubmissionStatus.ERROR to true,
                ),
            ) { (status, enabled) ->
                ServerConfigUIState(submissionStatus = status).submitButtonEnabled shouldBe enabled
            }
        }
    }
    // #371
    describe("submitting a new library's address") {
        context("when the address is accepted") {
            it("TC-1: accepts it without saving it, so the saved address stays as it was") {
                val saved = randomServerAddress()
                val accepted = "https://${randomString(8..16).lowercase()}.lan"
                val repository = TestServerConfigurationRepository(saved).apply {
                    newLibraryValidationResult = { ServerValidationResult.Success(accepted) }
                }
                val vm = sut(repository)

                vm.submitNewLibraryUrl(randomString())

                vm.uiState.value.submissionStatus shouldBe ServerConfigSubmissionStatus.ACCEPTED
                vm.uiState.value.acceptedAddress shouldBe accepted
                repository.savedAddress.value shouldBe saved
            }

            it("TC-1: returns to unsubmitted once the connection has been shown") {
                val accepted = "https://${randomString(8..16).lowercase()}.lan"
                val repository = TestServerConfigurationRepository().apply {
                    newLibraryValidationResult = { ServerValidationResult.Success(accepted) }
                }
                val vm = sut(repository)
                vm.submitNewLibraryUrl(randomString())

                vm.connectionShown()

                vm.uiState.value.submissionStatus shouldBe ServerConfigSubmissionStatus.UNSUBMITTED
                vm.uiState.value.acceptedAddress.shouldBeNull()
            }

            it("TC-1: accepts the address again when the reader connects again") {
                val accepted = "https://${randomString(8..16).lowercase()}.lan"
                val repository = TestServerConfigurationRepository().apply {
                    newLibraryValidationResult = { ServerValidationResult.Success(accepted) }
                }
                val vm = sut(repository)
                vm.submitNewLibraryUrl(randomString())
                vm.connectionShown()

                vm.submitNewLibraryUrl(randomString())

                vm.uiState.value.submissionStatus shouldBe ServerConfigSubmissionStatus.ACCEPTED
                vm.uiState.value.acceptedAddress shouldBe accepted
            }
        }

        context("when the address matches a saved library") {
            it("TC-2: shows ALREADY_SAVED with that library, and disables the submit button") {
                val library = randomLibrary()
                val repository = TestServerConfigurationRepository().apply {
                    newLibraryValidationResult = { ServerValidationResult.AlreadySaved(library) }
                }
                val vm = sut(repository)

                vm.submitNewLibraryUrl(randomString())

                vm.uiState.value.submissionStatus shouldBe ServerConfigSubmissionStatus.ALREADY_SAVED
                vm.uiState.value.alreadySaved shouldBe library
                vm.uiState.value.submitButtonEnabled shouldBe false
            }

            it("TC-2: returns to unsubmitted once the address is edited") {
                val library = randomLibrary()
                val repository = TestServerConfigurationRepository().apply {
                    newLibraryValidationResult = { ServerValidationResult.AlreadySaved(library) }
                }
                val vm = sut(repository)
                vm.submitNewLibraryUrl(randomString())

                vm.editAddress()

                vm.uiState.value.submissionStatus shouldBe ServerConfigSubmissionStatus.UNSUBMITTED
                vm.uiState.value.alreadySaved.shouldBeNull()
            }
        }
    }
})
