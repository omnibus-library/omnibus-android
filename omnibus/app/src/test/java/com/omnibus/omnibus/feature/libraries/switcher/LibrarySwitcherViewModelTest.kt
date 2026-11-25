package com.omnibus.omnibus.feature.libraries.switcher

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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class LibrarySwitcherViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    describe("the switcher's libraries") {
        it("TC-1: lists every library in the order they were added, with the open one in its place") {
            val first = randomLibrary().copy(addedAt = 1, isActive = false)
            val open = randomLibrary().copy(addedAt = 2, isActive = true)
            val third = randomLibrary().copy(addedAt = 3, isActive = false)
            val vm = LibrarySwitcherViewModel(TestLibrariesRepository(listOf(third, open, first)))

            vm.uiState.test {
                awaitItem().libraries shouldBe listOf(first, open, third)
            }
        }

        it("TC-2: follows the saved libraries as they change") {
            val open = randomLibrary().copy(addedAt = 1, isActive = true)
            val repository = TestLibrariesRepository(listOf(open))
            val vm = LibrarySwitcherViewModel(repository)

            vm.uiState.test {
                awaitItem().libraries shouldBe listOf(open)

                val added = randomLibrary().copy(addedAt = 2, isActive = false)
                repository.libraries.update { it + added }

                awaitItem().libraries shouldBe listOf(open, added)
            }
        }

        it("TC-3: shows no rows when the libraries can't be read") {
            val repository = object : LibrariesRepository by TestLibrariesRepository() {
                override fun getLibrariesFlow(): Flow<List<Library>> = flow { throw IOException("disk") }
            }
            val vm = LibrarySwitcherViewModel(repository)

            vm.uiState.test {
                awaitItem().libraries shouldBe emptyList()
                expectNoEvents()
            }
        }
    }
})
