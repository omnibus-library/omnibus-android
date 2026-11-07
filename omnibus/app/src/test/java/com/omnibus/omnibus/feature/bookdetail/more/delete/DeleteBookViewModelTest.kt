package com.omnibus.omnibus.feature.bookdetail.more.delete

import com.omnibus.omnibus.data.auth.CurrentUser
import com.omnibus.omnibus.data.auth.TestAuthRepository
import com.omnibus.omnibus.data.deletion.DeleteItemsResult
import com.omnibus.omnibus.data.deletion.DeletableCopy
import com.omnibus.omnibus.data.deletion.DeletableFile
import com.omnibus.omnibus.data.deletion.DeletionError
import com.omnibus.omnibus.data.deletion.DeletionImpact
import com.omnibus.omnibus.data.deletion.DeletionManifest
import com.omnibus.omnibus.data.deletion.DeletionManifestResult
import com.omnibus.omnibus.data.deletion.TestDeletionRepository
import com.omnibus.omnibus.data.network.TestConnectivityMonitor
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

private const val BOOK_UUID = "book-1"

private fun user(isAdmin: Boolean) =
    CurrentUser(id = 1, canEdit = false, canUpload = false, hiddenFormats = emptySet(), isAdmin = isAdmin)

private fun file(id: Long) = DeletableFile(id = id, format = "epub", filename = "f$id.epub", label = null, sizeBytes = 10)

private fun copy(id: Long) = DeletableCopy(id = id, isbn = null, note = null)

private fun manifestOf(
    files: List<DeletableFile> = emptyList(),
    copies: List<DeletableCopy> = emptyList(),
    impact: DeletionImpact = DeletionImpact(),
) = DeletionManifest(files = files, copies = copies, impact = impact)

/** Two files and one physical copy. */
private val itemsManifest = manifestOf(files = listOf(file(1), file(2)), copies = listOf(copy(5)))

/** The ViewModel with its fakes, so a test can drive the repository and the signed-in user. */
private class Harness(
    val vm: DeleteBookViewModel,
    val repo: TestDeletionRepository,
    val auth: TestAuthRepository,
    val monitor: TestConnectivityMonitor,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteBookViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    /** The one place a test builds the ViewModel, so later changes only touch this helper. */
    fun harness(admin: Boolean = true, online: Boolean = true): Harness {
        val repo = TestDeletionRepository()
        val auth = TestAuthRepository().apply { currentUserState.value = user(admin) }
        val monitor = TestConnectivityMonitor(online)
        return Harness(DeleteBookViewModel(BOOK_UUID, repo, auth, monitor), repo, auth, monitor)
    }

    /** A harness whose manifest has two files and one copy, opened with the row enabled. */
    fun openedWithItems(): Harness {
        val h = harness()
        h.repo.manifestResult = DeletionManifestResult.Success(itemsManifest)
        h.vm.openSheet(hasFiles = true)
        return h
    }

    describe("the gating state") {

        it("#121 TC-27: an admin is admin, a non-admin or no user is not, the connectivity follows the monitor, and construction makes no call") {
            val admin = harness(admin = true)
            admin.vm.uiState.value.isAdmin shouldBe true
            admin.vm.uiState.value.isOnline shouldBe true

            val nonAdmin = harness(admin = false)
            nonAdmin.vm.uiState.value.isAdmin shouldBe false

            val signedOut = harness()
            signedOut.auth.currentUserState.value = null
            signedOut.vm.uiState.value.isAdmin shouldBe false

            admin.monitor.isOnline.value = false
            admin.vm.uiState.value.isOnline shouldBe false
            admin.monitor.isOnline.value = true
            admin.vm.uiState.value.isOnline shouldBe true

            admin.repo.manifestRequests shouldBe emptyList()
            admin.repo.deleteRequests shouldBe emptyList()
        }
    }

    describe("opening the sheet") {

        it("#121 TC-28: a non-admin, or an offline admin, cannot open the sheet, and no manifest is read") {
            val nonAdmin = harness(admin = false)
            nonAdmin.vm.openSheet(hasFiles = true)
            nonAdmin.vm.uiState.value.sheet shouldBe DeleteSheet.Closed
            nonAdmin.repo.manifestRequests shouldBe emptyList()

            val offline = harness(admin = true, online = false)
            offline.vm.openSheet(hasFiles = true)
            offline.vm.uiState.value.sheet shouldBe DeleteSheet.Closed
            offline.repo.manifestRequests shouldBe emptyList()
        }

        it("#121 TC-29: opening loads the manifest into Choosing with nothing picked, and each open reads it again") {
            val h = harness()
            val manifest = manifestOf(files = listOf(file(1), file(2)))
            h.repo.manifestResult = DeletionManifestResult.Success(manifest)
            val gate = CompletableDeferred<Unit>()
            h.repo.manifestGate = gate

            h.vm.openSheet(hasFiles = true)
            h.vm.uiState.value.sheet shouldBe DeleteSheet.Loading
            gate.complete(Unit)
            h.vm.uiState.value.sheet shouldBe DeleteSheet.Choosing(manifest)
            h.vm.uiState.value.pickedFiles shouldBe emptySet()
            h.repo.manifestRequests shouldBe listOf(BOOK_UUID)

            h.vm.toggleFile(1)
            h.vm.closeSheet()
            h.vm.openSheet(hasFiles = true)
            h.repo.manifestRequests shouldBe listOf(BOOK_UUID, BOOK_UUID)
            h.vm.uiState.value.sheet shouldBe DeleteSheet.Choosing(manifest)
            h.vm.uiState.value.pickedFiles shouldBe emptySet()
        }

        it("#121 TC-30: a book with nothing to pick opens on confirm, and back closes the sheet") {
            val h = harness()
            val empty = manifestOf()
            h.repo.manifestResult = DeletionManifestResult.Success(empty)

            h.vm.openSheet(hasFiles = false)
            h.vm.uiState.value.sheet shouldBe DeleteSheet.Confirming(empty)

            h.vm.back()
            h.vm.uiState.value.sheet shouldBe DeleteSheet.Closed
        }

        it("#121 TC-31: a failed read shows LoadFailed, and each retry is one more read until one succeeds") {
            val h = harness()
            h.repo.manifestResult = DeletionManifestResult.Failed(DeletionError.Unreachable)
            h.vm.openSheet(hasFiles = true)
            h.vm.uiState.value.sheet shouldBe DeleteSheet.LoadFailed

            h.vm.retryLoad()
            h.vm.uiState.value.sheet shouldBe DeleteSheet.LoadFailed
            h.repo.manifestRequests.size shouldBe 2

            val manifest = manifestOf(files = listOf(file(1)))
            h.repo.manifestResult = DeletionManifestResult.Success(manifest)
            val gate = CompletableDeferred<Unit>()
            h.repo.manifestGate = gate
            h.vm.retryLoad()
            h.vm.uiState.value.sheet shouldBe DeleteSheet.Loading
            gate.complete(Unit)
            h.vm.uiState.value.sheet shouldBe DeleteSheet.Choosing(manifest)
            h.repo.manifestRequests.size shouldBe 3
        }
    }

    describe("picking files and copies") {

        it("#121 TC-32: toggles add and remove a pick, Select all picks everything and then clears, and unknown ids are ignored") {
            val h = openedWithItems()

            h.vm.toggleFile(1)
            h.vm.uiState.value.pickedFiles shouldBe setOf(1L)
            h.vm.toggleFile(1)
            h.vm.uiState.value.pickedFiles shouldBe emptySet()

            h.vm.toggleCopy(5)
            h.vm.uiState.value.pickedCopies shouldBe setOf(5L)
            h.vm.toggleCopy(5)
            h.vm.uiState.value.pickedCopies shouldBe emptySet()

            h.vm.toggleFile(1)
            h.vm.toggleAll()
            h.vm.uiState.value.pickedFiles shouldBe setOf(1L, 2L)
            h.vm.uiState.value.pickedCopies shouldBe setOf(5L)
            h.vm.toggleAll()
            h.vm.uiState.value.pickedFiles shouldBe emptySet()
            h.vm.uiState.value.pickedCopies shouldBe emptySet()

            h.vm.toggleFile(99)
            h.vm.toggleCopy(99)
            h.vm.uiState.value.pickedFiles shouldBe emptySet()
            h.vm.uiState.value.pickedCopies shouldBe emptySet()
        }

        it("#121 TC-33: Continue does nothing with no picks, and with picks moves to confirm and keeps them") {
            val h = openedWithItems()
            h.vm.continueToConfirm()
            h.vm.uiState.value.sheet shouldBe DeleteSheet.Choosing(itemsManifest)

            h.vm.toggleFile(2)
            h.vm.continueToConfirm()
            h.vm.uiState.value.sheet shouldBe DeleteSheet.Confirming(itemsManifest)
            h.vm.uiState.value.pickedFiles shouldBe setOf(2L)
        }
    }

    describe("going back and closing") {

        it("#121 TC-34: back from confirm returns to choose with the picks kept and the error cleared") {
            val h = openedWithItems()
            h.vm.toggleFile(1)
            h.vm.continueToConfirm()
            h.repo.deleteResult = DeleteItemsResult.Failed(DeletionError.Unreachable)
            h.vm.confirmDelete()
            h.vm.uiState.value.deleteError shouldBe DeleteFailure.Unreachable

            h.vm.back()
            h.vm.uiState.value.sheet shouldBe DeleteSheet.Choosing(itemsManifest)
            h.vm.uiState.value.pickedFiles shouldBe setOf(1L)
            h.vm.uiState.value.deleteError.shouldBeNull()
        }

        it("#121 TC-35: close clears the picks when idle, is ignored while deleting, and works again after a failure") {
            val idle = openedWithItems()
            idle.vm.toggleFile(1)
            idle.vm.closeSheet()
            idle.vm.uiState.value.sheet shouldBe DeleteSheet.Closed
            idle.vm.uiState.value.pickedFiles shouldBe emptySet()

            val busy = openedWithItems()
            busy.vm.toggleFile(1)
            busy.vm.continueToConfirm()
            val gate = CompletableDeferred<Unit>()
            busy.repo.deleteGate = gate
            busy.vm.confirmDelete()
            busy.vm.uiState.value.deleting shouldBe true
            busy.vm.closeSheet()
            busy.vm.uiState.value.sheet shouldBe DeleteSheet.Confirming(itemsManifest)
            busy.vm.uiState.value.pickedFiles shouldBe setOf(1L)
            gate.complete(Unit)

            val failed = openedWithItems()
            failed.vm.toggleFile(1)
            failed.vm.continueToConfirm()
            failed.repo.deleteResult = DeleteItemsResult.Failed(DeletionError.Http(500))
            failed.vm.confirmDelete()
            failed.vm.closeSheet()
            failed.vm.uiState.value.sheet shouldBe DeleteSheet.Closed
        }
    }

    describe("confirming the delete") {

        it("#121 TC-36: confirm sends the picked ids once, ignores a second tap, and does nothing from the other steps") {
            val h = openedWithItems()
            h.vm.confirmDelete()
            h.repo.deleteRequests shouldBe emptyList()
            h.vm.uiState.value.sheet shouldBe DeleteSheet.Choosing(itemsManifest)

            h.vm.toggleFile(1)
            h.vm.continueToConfirm()
            val gate = CompletableDeferred<Unit>()
            h.repo.deleteGate = gate
            h.vm.confirmDelete()
            h.vm.uiState.value.deleting shouldBe true
            h.vm.confirmDelete()
            h.repo.deleteRequests shouldBe listOf(Triple(BOOK_UUID, setOf(1L), emptySet<Long>()))
            gate.complete(Unit)

            val record = harness()
            record.repo.manifestResult = DeletionManifestResult.Success(manifestOf())
            record.vm.openSheet(hasFiles = false)
            record.vm.confirmDelete()
            record.repo.deleteRequests shouldBe listOf(Triple(BOOK_UUID, emptySet<Long>(), emptySet<Long>()))
        }

        it("#121 TC-36: confirm from Closed or Loading sends nothing") {
            val closed = harness()
            closed.vm.confirmDelete()
            closed.repo.deleteRequests shouldBe emptyList()

            val loading = harness()
            val gate = CompletableDeferred<Unit>()
            loading.repo.manifestGate = gate
            loading.vm.openSheet(hasFiles = true)
            loading.vm.confirmDelete()
            loading.repo.deleteRequests shouldBe emptyList()
            gate.complete(Unit)
        }

        it("#121 TC-37: a total delete that the server reports as book_deleted closes the sheet with a total outcome") {
            val h = openedWithItems()
            h.vm.toggleAll()
            h.vm.continueToConfirm()
            h.repo.deleteResult = DeleteItemsResult.Deleted(listOf(1L, 2L), listOf(5L), bookDeleted = true)

            h.vm.confirmDelete()

            h.vm.uiState.value.deleting shouldBe false
            h.vm.uiState.value.sheet shouldBe DeleteSheet.Closed
            h.vm.uiState.value.outcome shouldBe DeleteOutcome(
                eventId = 1,
                bookDeleted = true,
                deletedFiles = 2,
                deletedCopies = 1,
            )
        }

        it("#121 TC-38: a partial outcome counts the response ids, is cleared only by its own id, and a second delete gets a larger id") {
            val h = openedWithItems()
            h.vm.toggleFile(1)
            h.vm.continueToConfirm()
            h.repo.deleteResult = DeleteItemsResult.Deleted(listOf(1L), emptyList(), bookDeleted = false)
            h.vm.confirmDelete()

            val first = h.vm.uiState.value.outcome
            first shouldBe DeleteOutcome(eventId = 1, bookDeleted = false, deletedFiles = 1, deletedCopies = 0)

            h.vm.outcomeShown(999)
            h.vm.uiState.value.outcome shouldBe first
            h.vm.outcomeShown(1)
            h.vm.uiState.value.outcome.shouldBeNull()

            h.vm.openSheet(hasFiles = true)
            h.vm.toggleFile(2)
            h.vm.continueToConfirm()
            h.repo.deleteResult = DeleteItemsResult.Deleted(listOf(2L), emptyList(), bookDeleted = false)
            h.vm.confirmDelete()
            h.vm.uiState.value.outcome shouldBe DeleteOutcome(eventId = 2, bookDeleted = false, deletedFiles = 1, deletedCopies = 0)
        }

        it("#121 TC-39: each failed delete maps to the inline failure, keeps the sheet and picks, and a retry clears the error first") {
            val unreachable = openedWithItems()
            unreachable.vm.toggleFile(1)
            unreachable.vm.continueToConfirm()
            unreachable.repo.deleteResult = DeleteItemsResult.Failed(DeletionError.Unreachable)
            unreachable.vm.confirmDelete()
            unreachable.vm.uiState.value.deleteError shouldBe DeleteFailure.Unreachable

            val others = listOf(
                DeletionError.Forbidden,
                DeletionError.BookNotFound,
                DeletionError.ItemNotFound,
                DeletionError.Http(500),
                DeletionError.SessionExpired,
                DeletionError.UnexpectedResponse,
                DeletionError.NoServer,
            )
            for (error in others) {
                val h = openedWithItems()
                h.vm.toggleFile(1)
                h.vm.continueToConfirm()
                h.repo.deleteResult = DeleteItemsResult.Failed(error)
                h.vm.confirmDelete()

                val state = h.vm.uiState.value
                state.deleteError shouldBe DeleteFailure.Other
                state.sheet shouldBe DeleteSheet.Confirming(itemsManifest)
                state.deleting shouldBe false
                state.pickedFiles shouldBe setOf(1L)
                state.outcome.shouldBeNull()
            }

            val retry = unreachable
            val gate = CompletableDeferred<Unit>()
            retry.repo.deleteGate = gate
            retry.repo.deleteResult = DeleteItemsResult.Deleted(listOf(1L), emptyList(), bookDeleted = false)
            retry.vm.confirmDelete()
            retry.vm.uiState.value.deleteError.shouldBeNull()
            retry.vm.uiState.value.deleting shouldBe true
            gate.complete(Unit)
            retry.vm.uiState.value.outcome shouldBe DeleteOutcome(eventId = 1, bookDeleted = false, deletedFiles = 1, deletedCopies = 0)
        }
    }
})
