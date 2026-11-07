package com.omnibus.omnibus.feature.bookdetail.more.delete

import com.omnibus.omnibus.data.auth.TestAuthRepository
import com.omnibus.omnibus.data.auth.randomCurrentUser
import com.omnibus.omnibus.data.deletion.DeleteItemsResult
import com.omnibus.omnibus.data.deletion.DeletionError
import com.omnibus.omnibus.data.deletion.DeletionImpact
import com.omnibus.omnibus.data.deletion.DeletionManifest
import com.omnibus.omnibus.data.deletion.DeletionManifestResult
import com.omnibus.omnibus.data.deletion.TestDeletionRepository
import com.omnibus.omnibus.data.deletion.randomDeletableCopy
import com.omnibus.omnibus.data.deletion.randomDeletableFile
import com.omnibus.omnibus.data.deletion.randomDeletionImpact
import com.omnibus.omnibus.data.network.TestConnectivityMonitor
import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.boolean
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** Two files and one physical copy. */
private fun itemsManifest() = DeletionManifest(
    files = listOf(randomDeletableFile(), randomDeletableFile()),
    copies = listOf(randomDeletableCopy()),
    impact = randomDeletionImpact(),
)

private fun admin() = TestAuthRepository().apply { currentUserState.value = randomCurrentUser().copy(isAdmin = true) }

private fun online() = TestConnectivityMonitor(online = true)

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteBookViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun sut(
        bookUuid: String = randomString(),
        repo: TestDeletionRepository = TestDeletionRepository(),
        auth: TestAuthRepository = TestAuthRepository(),
        monitor: TestConnectivityMonitor = TestConnectivityMonitor(),
    ) = DeleteBookViewModel(bookUuid, repo, auth, monitor)

    /** An admin's ViewModel, with the sheet open on the choose step for [manifest]. */
    fun choosing(
        manifest: DeletionManifest = itemsManifest(),
        repo: TestDeletionRepository = TestDeletionRepository(),
        bookUuid: String = randomString(),
    ): DeleteBookViewModel {
        repo.manifestResult = DeletionManifestResult.Success(manifest)
        return sut(bookUuid, repo, admin(), online()).apply { openSheet(hasFiles = randomBoolean()) }
    }

    /** An admin's ViewModel, on the confirm step for the first file of [manifest]. */
    fun confirming(
        manifest: DeletionManifest = itemsManifest(),
        repo: TestDeletionRepository = TestDeletionRepository(),
        bookUuid: String = randomString(),
    ): DeleteBookViewModel = choosing(manifest, repo, bookUuid).apply {
        toggleFile(manifest.files.first().id)
        continueToConfirm()
    }

    describe("opening the sheet") {
        context("when the delete row is not enabled") {
            it("#121 TC-27, TC-28: stays closed without reading the manifest, for a signed-out, non-admin or offline user") {
                checkAll(
                    Exhaustive.of(
                        null to true,
                        randomCurrentUser().copy(isAdmin = false) to true,
                        randomCurrentUser().copy(isAdmin = true) to false,
                    ),
                ) { (user, online) ->
                    val repo = TestDeletionRepository()
                    val auth = TestAuthRepository().apply { currentUserState.value = user }
                    val vm = sut(repo = repo, auth = auth, monitor = TestConnectivityMonitor(online))

                    vm.openSheet(hasFiles = randomBoolean())

                    vm.uiState.value.sheet shouldBe DeleteSheet.Closed
                    repo.manifestRequests shouldBe emptyList()
                }
            }
        }

        context("when the book has items") {
            it("#121 TC-29: shows the choose step with nothing picked") {
                val manifest = itemsManifest()
                val vm = choosing(manifest)

                vm.uiState.value.sheet shouldBe DeleteSheet.Choosing(manifest)
                vm.uiState.value.pickedFiles shouldBe emptySet()
            }
        }

        context("when it was opened before") {
            it("#121 TC-29: reads the manifest again with nothing picked") {
                val manifest = itemsManifest()
                val repo = TestDeletionRepository()
                val vm = choosing(manifest, repo)
                vm.toggleFile(manifest.files.first().id)
                vm.closeSheet()

                vm.openSheet(hasFiles = randomBoolean())

                repo.manifestRequests.size shouldBe 2
                vm.uiState.value.pickedFiles shouldBe emptySet()
            }
        }

        context("when the book has nothing to pick") {
            it("#121 TC-30: opens on the confirm step") {
                val empty = DeletionManifest(emptyList(), emptyList(), DeletionImpact())

                choosing(empty).uiState.value.sheet shouldBe DeleteSheet.Confirming(empty)
            }
        }

        context("when the read fails") {
            it("#121 TC-31: lets a retry reach the choose step") {
                val manifest = itemsManifest()
                val repo = TestDeletionRepository().apply {
                    manifestResult = DeletionManifestResult.Failed(DeletionError.Unreachable)
                }
                val vm = sut(repo = repo, auth = admin(), monitor = online())
                vm.openSheet(hasFiles = randomBoolean())
                vm.uiState.value.sheet shouldBe DeleteSheet.LoadFailed

                repo.manifestResult = DeletionManifestResult.Success(manifest)
                vm.retryLoad()

                vm.uiState.value.sheet shouldBe DeleteSheet.Choosing(manifest)
            }
        }
    }

    describe("picking items") {
        it("#121 TC-32: adds a pick, then removes it on the second toggle") {
            val manifest = itemsManifest()
            val file = manifest.files.first().id
            val copy = manifest.copies.first().id
            val vm = choosing(manifest)

            vm.toggleFile(file)
            vm.toggleCopy(copy)
            vm.uiState.value.pickedFiles shouldBe setOf(file)
            vm.uiState.value.pickedCopies shouldBe setOf(copy)

            vm.toggleFile(file)
            vm.toggleCopy(copy)
            vm.uiState.value.pickedFiles shouldBe emptySet()
            vm.uiState.value.pickedCopies shouldBe emptySet()
        }

        it("#121 TC-32: selects everything from a partial pick, then clears it") {
            val manifest = itemsManifest()
            val vm = choosing(manifest)
            vm.toggleFile(manifest.files.first().id)

            vm.toggleAll()
            vm.uiState.value.pickedFiles shouldBe manifest.files.map { it.id }.toSet()
            vm.uiState.value.pickedCopies shouldBe manifest.copies.map { it.id }.toSet()

            vm.toggleAll()
            vm.uiState.value.pickedFiles shouldBe emptySet()
            vm.uiState.value.pickedCopies shouldBe emptySet()
        }

        context("when nothing is picked") {
            it("#121 TC-33: stays on the choose step on continue") {
                val manifest = itemsManifest()
                val vm = choosing(manifest)

                vm.continueToConfirm()

                vm.uiState.value.sheet shouldBe DeleteSheet.Choosing(manifest)
            }
        }
    }

    describe("going back") {
        context("when the book has items") {
            it("#121 TC-34: returns to the choose step with the picks kept, clearing the last error") {
                val manifest = itemsManifest()
                val repo = TestDeletionRepository()
                val vm = confirming(manifest, repo)
                repo.deleteResult = DeleteItemsResult.Failed(DeletionError.Unreachable)
                vm.confirmDelete()

                vm.back()

                vm.uiState.value.sheet shouldBe DeleteSheet.Choosing(manifest)
                vm.uiState.value.pickedFiles shouldBe setOf(manifest.files.first().id)
                vm.uiState.value.deleteError.shouldBeNull()
            }
        }

        context("when the book has nothing to pick") {
            it("#121 TC-30: closes the sheet") {
                val vm = choosing(DeletionManifest(emptyList(), emptyList(), DeletionImpact()))

                vm.back()

                vm.uiState.value.sheet shouldBe DeleteSheet.Closed
            }
        }
    }

    describe("closing the sheet") {
        it("#121 TC-35: clears the picks") {
            val vm = confirming()

            vm.closeSheet()

            vm.uiState.value.sheet shouldBe DeleteSheet.Closed
            vm.uiState.value.pickedFiles shouldBe emptySet()
        }

        context("when a delete is in flight") {
            it("#121 TC-35: stays open") {
                val manifest = itemsManifest()
                val repo = TestDeletionRepository().apply { deleteGate = CompletableDeferred() }
                val vm = confirming(manifest, repo)
                vm.confirmDelete()

                vm.closeSheet()

                vm.uiState.value.sheet shouldBe DeleteSheet.Confirming(manifest)
            }
        }
    }

    describe("confirming the delete") {
        it("#121 TC-36: sends the picked ids for this book") {
            val uuid = randomString()
            val manifest = itemsManifest()
            val repo = TestDeletionRepository().apply { deleteGate = CompletableDeferred() }
            val vm = confirming(manifest, repo, uuid)

            vm.confirmDelete()

            repo.deleteRequests shouldBe listOf(Triple(uuid, setOf(manifest.files.first().id), emptySet<Long>()))
        }

        context("when a delete is in flight") {
            it("#121 TC-36: ignores a second tap") {
                val repo = TestDeletionRepository().apply { deleteGate = CompletableDeferred() }
                val vm = confirming(repo = repo)

                vm.confirmDelete()
                vm.confirmDelete()

                repo.deleteRequests.size shouldBe 1
            }
        }

        context("when the delete succeeds") {
            it("#121 TC-37: closes with an outcome counted from the response, regardless of whether the book went") {
                checkAll(Exhaustive.boolean()) { bookDeleted ->
                    val repo = TestDeletionRepository().apply {
                        deleteResult = DeleteItemsResult.Deleted(
                            deletedFileIds = listOf(randomLong(), randomLong()),
                            deletedCopyIds = listOf(randomLong()),
                            bookDeleted = bookDeleted,
                        )
                    }
                    val vm = confirming(repo = repo)

                    vm.confirmDelete()

                    vm.uiState.value.sheet shouldBe DeleteSheet.Closed
                    vm.uiState.value.outcome shouldBe DeleteOutcome(
                        eventId = 1,
                        bookDeleted = bookDeleted,
                        deletedFiles = 2,
                        deletedCopies = 1,
                    )
                }
            }

            it("#121 TC-38: clears the outcome only for its own id") {
                val repo = TestDeletionRepository().apply {
                    deleteResult = DeleteItemsResult.Deleted(emptyList(), emptyList(), randomBoolean())
                }
                val vm = confirming(repo = repo)
                vm.confirmDelete()
                val outcome = checkNotNull(vm.uiState.value.outcome)

                vm.outcomeShown(outcome.eventId + 1)
                vm.uiState.value.outcome shouldBe outcome

                vm.outcomeShown(outcome.eventId)
                vm.uiState.value.outcome.shouldBeNull()
            }
        }

        context("when the delete fails") {
            it("#121 TC-39: reports only an unreachable server differently") {
                checkAll(
                    Exhaustive.of(
                        DeletionError.Unreachable to DeleteFailure.Unreachable,
                        DeletionError.Forbidden to DeleteFailure.Other,
                        DeletionError.BookNotFound to DeleteFailure.Other,
                        DeletionError.ItemNotFound to DeleteFailure.Other,
                        DeletionError.Http(500) to DeleteFailure.Other,
                        DeletionError.SessionExpired to DeleteFailure.Other,
                        DeletionError.UnexpectedResponse to DeleteFailure.Other,
                        DeletionError.NoServer to DeleteFailure.Other,
                    ),
                ) { (error, expected) ->
                    val repo = TestDeletionRepository().apply { deleteResult = DeleteItemsResult.Failed(error) }
                    val vm = confirming(repo = repo)

                    vm.confirmDelete()

                    vm.uiState.value.deleteError shouldBe expected
                }
            }

            it("#121 TC-39: keeps the confirm step with its picks") {
                val manifest = itemsManifest()
                val repo = TestDeletionRepository().apply {
                    deleteResult = DeleteItemsResult.Failed(DeletionError.Unreachable)
                }
                val vm = confirming(manifest, repo)

                vm.confirmDelete()

                vm.uiState.value.sheet shouldBe DeleteSheet.Confirming(manifest)
                vm.uiState.value.pickedFiles shouldBe setOf(manifest.files.first().id)
            }
        }
    }
})
