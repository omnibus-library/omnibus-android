package com.omnibus.omnibus

import com.omnibus.omnibus.data.configuration.FakeServerConfigurationRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    describe("startDestination") {
        it("opens server configuration when no server is saved") {
            val viewModel = MainViewModel(FakeServerConfigurationRepository(initialAddress = null))

            viewModel.startDestination.value shouldBe StartDestination.ServerConfiguration
        }

        it("opens server configuration when the saved server is blank") {
            val viewModel = MainViewModel(FakeServerConfigurationRepository(initialAddress = "  "))

            viewModel.startDestination.value shouldBe StartDestination.ServerConfiguration
        }

        it("opens login when a server is saved") {
            val viewModel =
                MainViewModel(FakeServerConfigurationRepository(initialAddress = "http://10.0.0.2:3000"))

            viewModel.startDestination.value shouldBe StartDestination.Login
        }

        it("opens server configuration when the saved config can't be read") {
            val repository = FakeServerConfigurationRepository(initialAddress = "http://10.0.0.2:3000")
                .apply { readFailure = IOException("corrupt") }

            MainViewModel(repository).startDestination.value shouldBe StartDestination.ServerConfiguration
        }

        it("does not change after the config is edited post-launch") {
            val repository = FakeServerConfigurationRepository(initialAddress = null)
            val viewModel = MainViewModel(repository)

            repository.saveServerConfig("http://10.0.0.2:3000")

            viewModel.startDestination.value shouldBe StartDestination.ServerConfiguration
        }
    }
})
