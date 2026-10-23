package com.omnibus.omnibus.feature.configuration

import com.omnibus.omnibus.data.configuration.FakeServerConfigurationRepository
import com.omnibus.omnibus.data.configuration.ServerValidationResult
import com.omnibus.omnibus.testing.CollectingScope
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ServerConfigurationViewModelTest : DescribeSpec({

    lateinit var repository: FakeServerConfigurationRepository
    lateinit var collecting: CollectingScope

    fun createViewModel() = ServerConfigurationViewModel(repository).also {
        collecting.collect(it.uiState)
    }

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    beforeEach {
        repository = FakeServerConfigurationRepository()
        collecting = CollectingScope()
    }

    afterEach { collecting.cancel() }

    describe("initial state") {

        it("is unsubmitted with an empty address when no server is saved") {
            createViewModel().uiState.value shouldBe ServerConfigUIState()
        }

        it("exposes the saved address so the field can be prefilled") {
            repository.savedAddress.value = "http://10.0.0.2:3000"

            createViewModel().uiState.value.savedConfigurationValue shouldBe "http://10.0.0.2:3000"
        }
    }

    describe("submitServerUrl") {

        it("is PENDING while the address is being checked") {
            repository.validationGate = CompletableDeferred()
            val viewModel = createViewModel()

            viewModel.submitServerUrl("10.0.0.2:3000")

            viewModel.uiState.value.submissionStatus shouldBe ServerConfigSubmissionStatus.PENDING
            viewModel.uiState.value.submitButtonEnabled shouldBe false
        }

        it("validates the address as entered") {
            val viewModel = createViewModel()

            viewModel.submitServerUrl("10.0.0.2:3000")

            repository.validatedAddresses shouldBe listOf("10.0.0.2:3000")
        }

        describe("when the server is found") {

            it("saves the normalized address, not the raw input") {
                repository.validationResult = { ServerValidationResult.Success("http://10.0.0.2:3000") }
                val viewModel = createViewModel()

                viewModel.submitServerUrl("10.0.0.2:3000/")

                repository.savedAddress.value shouldBe "http://10.0.0.2:3000"
            }

            it("becomes ACCEPTED and disables the submit button") {
                val viewModel = createViewModel()

                viewModel.submitServerUrl("http://10.0.0.2:3000")

                viewModel.uiState.value.submissionStatus shouldBe ServerConfigSubmissionStatus.ACCEPTED
                viewModel.uiState.value.submitButtonEnabled shouldBe false
            }
        }

        describe("when the server isn't found") {

            it("becomes NOT_ACCEPTED without saving, and allows a retry") {
                repository.validationResult = { ServerValidationResult.ServerNotFound }
                val viewModel = createViewModel()

                viewModel.submitServerUrl("10.0.0.2:3000")

                viewModel.uiState.value.submissionStatus shouldBe ServerConfigSubmissionStatus.NOT_ACCEPTED
                viewModel.uiState.value.submitButtonEnabled shouldBe true
                repository.savedAddress.value.shouldBeNull()
            }
        }

        describe("when the address is malformed") {

            it("becomes ERROR without saving, and allows a retry") {
                repository.validationResult = { ServerValidationResult.InvalidAddressFormat }
                val viewModel = createViewModel()

                viewModel.submitServerUrl("exa mple")

                viewModel.uiState.value.submissionStatus shouldBe ServerConfigSubmissionStatus.ERROR
                viewModel.uiState.value.submitButtonEnabled shouldBe true
                repository.savedAddress.value.shouldBeNull()
            }
        }
    }
})
