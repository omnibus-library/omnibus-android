package com.omnibus.omnibus.feature.libraries.manage

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.omnibus.omnibus.data.libraries.LibrariesRepository
import com.omnibus.omnibus.data.libraries.LibraryRemoval
import com.omnibus.omnibus.data.libraries.LibrarySyncResult
import com.omnibus.omnibus.data.libraries.TestLibrariesRepository
import com.omnibus.omnibus.data.libraries.TestLibraryRemoval
import com.omnibus.omnibus.data.libraries.randomLibrary
import com.omnibus.omnibus.feature.libraries.remove.RemoveLibraryState
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import kotlinx.coroutines.CompletableDeferred
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
        removal: LibraryRemoval = TestLibraryRemoval(),
    ): LibraryDetailViewModel = LibraryDetailViewModel(libraryId, repository, persistScope, removal)

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
                            LibraryDetailViewModel(
                                library.id,
                                repository,
                                CoroutineScope(UnconfinedTestDispatcher()),
                                TestLibraryRemoval(repository),
                            )
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

    describe("offering to remove the library") {
        context("when the library is inactive") {
            it("TC-13: offers Remove library") {
                val library = randomLibrary().copy(isActive = false)
                val vm = sut(libraryId = library.id, repository = TestLibrariesRepository(listOf(library)))

                vm.uiState.value.canRemove shouldBe true
            }
        }

        context("when the library is active") {
            it("TC-13: doesn't offer Remove library") {
                val library = randomLibrary().copy(isActive = true)
                val vm = sut(libraryId = library.id, repository = TestLibrariesRepository(listOf(library)))

                vm.uiState.value.canRemove shouldBe false
            }

            it("TC-13: ignores a tap on Remove library") {
                val library = randomLibrary().copy(isActive = true)
                val vm = sut(libraryId = library.id, repository = TestLibrariesRepository(listOf(library)))

                vm.removeClicked()

                vm.uiState.value.removeState shouldBe RemoveLibraryState.Hidden
            }
        }

        context("before the library is read") {
            it("TC-13: doesn't offer Remove library") {
                val vm = sut(repository = TestLibrariesRepository())

                vm.uiState.value.canRemove shouldBe false
            }

            it("TC-13: ignores a tap on Remove library") {
                val vm = sut(repository = TestLibrariesRepository())

                vm.removeClicked()

                vm.uiState.value.removeState shouldBe RemoveLibraryState.Hidden
            }
        }
    }

    describe("cancelling the removal") {
        context("when the confirm dialog is open") {
            it("TC-14: closes it") {
                val library = randomLibrary().copy(isActive = false)
                val vm = sut(libraryId = library.id, repository = TestLibrariesRepository(listOf(library)))

                vm.removeClicked()
                vm.cancelRemove()

                vm.uiState.value.removeState shouldBe RemoveLibraryState.Hidden
            }

            it("TC-14: syncs and removes nothing") {
                val library = randomLibrary().copy(isActive = false)
                val removal = TestLibraryRemoval()
                val vm = sut(
                    libraryId = library.id,
                    repository = TestLibrariesRepository(listOf(library)),
                    removal = removal,
                )

                vm.removeClicked()
                vm.cancelRemove()

                removal.syncCalls.shouldBeEmpty()
                removal.removeCalls.shouldBeEmpty()
            }

            it("TC-14: keeps the name the reader typed") {
                val library = randomLibrary().copy(isActive = false, name = "Home")
                val vm = sut(libraryId = library.id, repository = TestLibrariesRepository(listOf(library)))

                vm.nameChanged("Den")
                vm.removeClicked()
                vm.cancelRemove()

                vm.uiState.value.name shouldBe "Den"
            }
        }

        context("when the sync failed") {
            it("TC-14: closes the failure dialog") {
                val library = randomLibrary().copy(isActive = false)
                val removal = TestLibraryRemoval().also { it.syncResult = LibrarySyncResult.Failed }
                val vm = sut(
                    libraryId = library.id,
                    repository = TestLibrariesRepository(listOf(library)),
                    removal = removal,
                )
                vm.removeClicked()
                vm.confirmRemove()

                vm.cancelRemove()

                vm.uiState.value.removeState shouldBe RemoveLibraryState.Hidden
            }
        }
    }

    describe("removing the library") {
        context("when the sync is still running") {
            it("TC-15: shows Syncing") {
                val library = randomLibrary().copy(isActive = false)
                val gate = CompletableDeferred<LibrarySyncResult>()
                val removal = TestLibraryRemoval().also { it.syncGate = gate }
                val vm = sut(
                    libraryId = library.id,
                    repository = TestLibrariesRepository(listOf(library)),
                    removal = removal,
                )

                vm.removeClicked()
                vm.confirmRemove()

                vm.uiState.value.removeState shouldBe RemoveLibraryState.Syncing
            }

            it("TC-15: removes nothing yet") {
                val library = randomLibrary().copy(isActive = false)
                val gate = CompletableDeferred<LibrarySyncResult>()
                val removal = TestLibraryRemoval().also { it.syncGate = gate }
                val vm = sut(
                    libraryId = library.id,
                    repository = TestLibrariesRepository(listOf(library)),
                    removal = removal,
                )

                vm.removeClicked()
                vm.confirmRemove()

                removal.removeCalls.shouldBeEmpty()
            }
        }

        context("when the sync is accepted") {
            it("TC-15: removes the library once") {
                val library = randomLibrary().copy(isActive = false)
                val gate = CompletableDeferred<LibrarySyncResult>()
                val repository = TestLibrariesRepository(listOf(library))
                val removal = TestLibraryRemoval(repository).also { it.syncGate = gate }
                val vm = sut(libraryId = library.id, repository = repository, removal = removal)
                vm.removeClicked()
                vm.confirmRemove()

                gate.complete(LibrarySyncResult.Synced)

                removal.removeCalls shouldBe listOf(library.id)
            }

            it("TC-15: reports the library as missing") {
                val library = randomLibrary().copy(isActive = false)
                val gate = CompletableDeferred<LibrarySyncResult>()
                val repository = TestLibrariesRepository(listOf(library))
                val removal = TestLibraryRemoval(repository).also { it.syncGate = gate }
                val vm = sut(libraryId = library.id, repository = repository, removal = removal)
                vm.removeClicked()
                vm.confirmRemove()

                gate.complete(LibrarySyncResult.Synced)

                vm.uiState.value.isMissing shouldBe true
            }
        }

        context("while the sync is running or the removal is running") {
            it("TC-16: ignores every other action while syncing") {
                val library = randomLibrary().copy(isActive = false)
                val gate = CompletableDeferred<LibrarySyncResult>()
                val removal = TestLibraryRemoval().also { it.syncGate = gate }
                val vm = sut(
                    libraryId = library.id,
                    repository = TestLibrariesRepository(listOf(library)),
                    removal = removal,
                )
                vm.removeClicked()
                vm.confirmRemove()

                vm.confirmRemove()
                vm.cancelRemove()
                vm.removeAnyway()
                vm.removeClicked()

                vm.uiState.value.removeState shouldBe RemoveLibraryState.Syncing
                removal.syncCalls shouldBe listOf(library.id)
                removal.removeCalls.shouldBeEmpty()
            }

            it("TC-16: ignores every other action while removing anyway") {
                val library = randomLibrary().copy(isActive = false)
                val repository = TestLibrariesRepository(listOf(library))
                val gate = CompletableDeferred<Unit>()
                val removal = TestLibraryRemoval(repository).also {
                    it.syncResult = LibrarySyncResult.Failed
                    it.removeGate = gate
                }
                val vm = sut(libraryId = library.id, repository = repository, removal = removal)
                vm.removeClicked()
                vm.confirmRemove()
                vm.removeAnyway()

                vm.confirmRemove()
                vm.cancelRemove()
                vm.removeAnyway()
                vm.removeClicked()

                vm.uiState.value.removeState shouldBe RemoveLibraryState.RemovingAnyway
                removal.syncCalls shouldBe listOf(library.id)
                removal.removeCalls shouldBe listOf(library.id)
            }
        }

        context("when the sync fails") {
            it("TC-17: shows the failure") {
                val library = randomLibrary().copy(isActive = false)
                val removal = TestLibraryRemoval().also { it.syncResult = LibrarySyncResult.Failed }
                val vm = sut(
                    libraryId = library.id,
                    repository = TestLibrariesRepository(listOf(library)),
                    removal = removal,
                )

                vm.removeClicked()
                vm.confirmRemove()

                vm.uiState.value.removeState shouldBe RemoveLibraryState.SyncFailed
            }

            it("TC-17: removes nothing and keeps the library in the list") {
                val library = randomLibrary().copy(isActive = false)
                val repository = TestLibrariesRepository(listOf(library))
                val removal = TestLibraryRemoval(repository).also { it.syncResult = LibrarySyncResult.Failed }
                val vm = sut(libraryId = library.id, repository = repository, removal = removal)

                vm.removeClicked()
                vm.confirmRemove()

                removal.removeCalls.shouldBeEmpty()
                repository.libraries.value shouldBe listOf(library)
            }
        }

        context("when the reader removes the library anyway") {
            it("TC-18: removes the library once and reports it missing") {
                val library = randomLibrary().copy(isActive = false)
                val repository = TestLibrariesRepository(listOf(library))
                val removal = TestLibraryRemoval(repository).also { it.syncResult = LibrarySyncResult.Failed }
                val vm = sut(libraryId = library.id, repository = repository, removal = removal)
                vm.removeClicked()
                vm.confirmRemove()

                vm.removeAnyway()

                removal.removeCalls shouldBe listOf(library.id)
                vm.uiState.value.isMissing shouldBe true
            }

            it("TC-18: is ignored from the confirm dialog") {
                val library = randomLibrary().copy(isActive = false)
                val removal = TestLibraryRemoval()
                val vm = sut(
                    libraryId = library.id,
                    repository = TestLibrariesRepository(listOf(library)),
                    removal = removal,
                )
                vm.removeClicked()

                vm.removeAnyway()

                vm.uiState.value.removeState shouldBe RemoveLibraryState.Confirming
                removal.removeCalls.shouldBeEmpty()
            }

            it("TC-18: is ignored from the start") {
                val library = randomLibrary().copy(isActive = false)
                val removal = TestLibraryRemoval()
                val vm = sut(
                    libraryId = library.id,
                    repository = TestLibrariesRepository(listOf(library)),
                    removal = removal,
                )

                vm.removeAnyway()

                vm.uiState.value.removeState shouldBe RemoveLibraryState.Hidden
                removal.removeCalls.shouldBeEmpty()
            }
        }

        context("when the view model is cleared while the sync runs") {
            it("TC-19: still removes the library once the sync is accepted") {
                val library = randomLibrary().copy(isActive = false)
                val gate = CompletableDeferred<LibrarySyncResult>()
                val repository = TestLibrariesRepository(listOf(library))
                val removal = TestLibraryRemoval(repository).also { it.syncGate = gate }
                val store = ViewModelStore()
                val vm = ViewModelProvider.create(
                    store,
                    viewModelFactory {
                        initializer {
                            LibraryDetailViewModel(
                                library.id,
                                repository,
                                CoroutineScope(UnconfinedTestDispatcher()),
                                removal,
                            )
                        }
                    },
                )[LibraryDetailViewModel::class]
                vm.removeClicked()
                vm.confirmRemove()

                store.clear()
                gate.complete(LibrarySyncResult.Synced)

                removal.removeCalls shouldBe listOf(library.id)
            }
        }
    }
})
