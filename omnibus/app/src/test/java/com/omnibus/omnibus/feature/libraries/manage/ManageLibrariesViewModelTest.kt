package com.omnibus.omnibus.feature.libraries.manage

import app.cash.turbine.test
import com.omnibus.omnibus.data.libraries.Library
import com.omnibus.omnibus.data.libraries.LibrariesRepository
import com.omnibus.omnibus.data.libraries.TestLibrariesRepository
import com.omnibus.omnibus.data.libraries.randomLibrary
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ManageLibrariesViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    describe("the list of libraries") {
        it("TC-3: lists the libraries in the order they were added, with the open one in its place") {
            val first = randomLibrary().copy(addedAt = 1, isActive = false)
            val open = randomLibrary().copy(addedAt = 2, isActive = true)
            val third = randomLibrary().copy(addedAt = 3, isActive = false)
            val vm = ManageLibrariesViewModel(TestLibrariesRepository(listOf(third, open, first)))

            vm.uiState.test {
                awaitItem().libraries shouldBe listOf(first, open, third)
            }
        }

        it("TC-5: shows no rows when the libraries can't be read, and stays alive") {
            val repository = object : LibrariesRepository by TestLibrariesRepository() {
                override fun getLibrariesFlow(): Flow<List<Library>> = flow { throw IOException("disk") }
            }
            val vm = ManageLibrariesViewModel(repository)

            vm.uiState.test {
                awaitItem().libraries shouldBe emptyList()
                expectNoEvents()
            }
        }
    }
})
