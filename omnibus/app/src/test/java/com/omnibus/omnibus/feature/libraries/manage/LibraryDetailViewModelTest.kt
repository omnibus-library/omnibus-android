package com.omnibus.omnibus.feature.libraries.manage

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.omnibus.omnibus.data.libraries.LibrariesRepository
import com.omnibus.omnibus.data.libraries.TestLibrariesRepository
import com.omnibus.omnibus.data.libraries.randomLibrary
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryDetailViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun sut(
        libraryId: String = randomLibrary().id,
        repository: LibrariesRepository = TestLibrariesRepository(),
        persistScope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher()),
    ): LibraryDetailViewModel = LibraryDetailViewModel(libraryId, repository, persistScope)

    describe("the title of a library's detail") {
        context("when the name field has a name") {
            it("TC-8: is the name, trimmed") {
                val state = LibraryDetailUIState(
                    library = randomLibrary().copy(address = "https://a.example:8443"),
                    name = "  Den  ",
                )

                state.title shouldBe "Den"
            }
        }

        context("when the name field is blank") {
            context("when the address states a port") {
                it("TC-8: is the host with that port") {
                    checkAll(Exhaustive.collection(listOf("", "   "))) { blank ->
                        val state = LibraryDetailUIState(
                            library = randomLibrary().copy(address = "https://a.example:8443"),
                            name = blank,
                        )

                        state.title shouldBe "a.example:8443"
                    }
                }
            }

            context("when the address states no port") {
                it("TC-8: is the host") {
                    checkAll(Exhaustive.collection(listOf("", "   "))) { blank ->
                        val state = LibraryDetailUIState(
                            library = randomLibrary().copy(address = "https://a.example"),
                            name = blank,
                        )

                        state.title shouldBe "a.example"
                    }
                }
            }
        }
    }

    describe("typing the name") {
        it("TC-10: keeps the first 40 characters of a 45-character name") {
            val typed = randomString(45..45)
            val vm = sut()

            vm.nameChanged(typed)

            vm.uiState.value.name shouldBe typed.take(40)
        }
    }

    describe("the library's name in the field") {
        context("when the library's username arrives after the reader has typed") {
            it("TC-7: keeps the typed name") {
                val library = randomLibrary().copy(name = "Home", username = null)
                val repository = TestLibrariesRepository(listOf(library))
                val vm = sut(libraryId = library.id, repository = repository)

                vm.nameChanged("Den")
                repository.libraries.update { list -> list.map { it.copy(username = "ana") } }

                vm.uiState.value.name shouldBe "Den"
            }

            it("TC-7: carries the new username on the library") {
                val library = randomLibrary().copy(name = "Home", username = null)
                val repository = TestLibrariesRepository(listOf(library))
                val vm = sut(libraryId = library.id, repository = repository)

                vm.nameChanged("Den")
                repository.libraries.update { list -> list.map { it.copy(username = "ana") } }

                vm.uiState.value.library?.username shouldBe "ana"
            }
        }
    }

    describe("saving the name") {
        context("when another library already has the typed name") {
            it("TC-11: renames this library to the trimmed name") {
                val mine = randomLibrary().copy(name = "Home")
                val other = randomLibrary().copy(name = "Work")
                val repository = TestLibrariesRepository(listOf(mine, other))
                val vm = sut(libraryId = mine.id, repository = repository)

                vm.nameChanged("  Work  ")
                vm.save()

                repository.renames shouldBe listOf(mine.id to "Work")
            }
        }

        context("when the name differs from the stored one only by its spaces") {
            it("TC-12: doesn't rename the library") {
                val library = randomLibrary().copy(name = "Home")
                val repository = TestLibrariesRepository(listOf(library))
                val vm = sut(libraryId = library.id, repository = repository)

                vm.nameChanged(" Home ")
                vm.save()

                repository.renames.shouldBeEmpty()
            }
        }

        context("when the name has changed") {
            it("TC-12: renames the library once, however many saves follow") {
                val library = randomLibrary().copy(name = "Home")
                val repository = TestLibrariesRepository(listOf(library))
                val vm = sut(libraryId = library.id, repository = repository)

                vm.nameChanged("Den")
                vm.save()
                vm.save()

                repository.renames shouldBe listOf(library.id to "Den")
            }
        }

        context("when the view model is cleared right after a change") {
            it("TC-14: still renames the library") {
                val library = randomLibrary().copy(name = "Home")
                val repository = TestLibrariesRepository(listOf(library))
                val store = ViewModelStore()
                val vm = ViewModelProvider.create(
                    store,
                    viewModelFactory {
                        initializer {
                            LibraryDetailViewModel(library.id, repository, CoroutineScope(UnconfinedTestDispatcher()))
                        }
                    },
                )[LibraryDetailViewModel::class]
                vm.nameChanged("Den")

                store.clear()

                repository.renames shouldBe listOf(library.id to "Den")
            }
        }

        context("when the library has been removed from the list") {
            it("TC-16: reports the library as missing") {
                val library = randomLibrary().copy(name = "Home")
                val repository = TestLibrariesRepository(listOf(library))
                val vm = sut(libraryId = library.id, repository = repository)
                repository.libraries.update { list -> list.filterNot { it.id == library.id } }

                vm.uiState.value.isMissing shouldBe true
            }

            it("TC-16: doesn't rename it") {
                val library = randomLibrary().copy(name = "Home")
                val repository = TestLibrariesRepository(listOf(library))
                val vm = sut(libraryId = library.id, repository = repository)
                repository.libraries.update { list -> list.filterNot { it.id == library.id } }

                vm.nameChanged("Den")
                vm.save()

                repository.renames.shouldBeEmpty()
            }
        }
    }
})
