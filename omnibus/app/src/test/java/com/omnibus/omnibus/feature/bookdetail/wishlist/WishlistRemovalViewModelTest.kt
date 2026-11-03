package com.omnibus.omnibus.feature.bookdetail.wishlist

import com.omnibus.omnibus.data.network.FakeConnectivityMonitor
import com.omnibus.omnibus.data.wishlist.FakeWishlistRepository
import com.omnibus.omnibus.data.wishlist.WishlistEntry
import com.omnibus.omnibus.data.wishlist.WishlistError
import com.omnibus.omnibus.data.wishlist.WishlistRemovalResult
import com.omnibus.omnibus.data.wishlist.WishlistSource
import com.omnibus.omnibus.data.wishlist.WishlistState
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class WishlistRemovalViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    val bookUuid = "book-1"
    val present = WishlistState.Present(WishlistEntry(id = 7, addedAtMillis = null, source = WishlistSource.Scan))

    /** The one place a test builds the ViewModel, so later changes only touch this helper. */
    fun viewModel(repo: FakeWishlistRepository, online: Boolean = true): Pair<WishlistRemovalViewModel, FakeConnectivityMonitor> {
        val monitor = FakeConnectivityMonitor(online)
        return WishlistRemovalViewModel(bookUuid, repo, monitor) to monitor
    }

    describe("state from the repository and connectivity") {

        it("#119 TC-26: the VM mirrors the wishlist state and connectivity, and follows later changes") {
            val repo = FakeWishlistRepository().apply { seed(bookUuid, present) }
            val (vm, monitor) = viewModel(repo, online = true)

            vm.uiState.value.state shouldBe present
            vm.uiState.value.isOnline shouldBe true

            repo.seed(bookUuid, WishlistState.Absent)
            vm.uiState.value.state shouldBe WishlistState.Absent

            repo.seed(bookUuid, present)
            monitor.isOnline.value = false
            vm.uiState.value.state shouldBe present
            vm.uiState.value.isOnline shouldBe false
        }

        it("#119 TC-26: a VM that starts offline reports offline") {
            val repo = FakeWishlistRepository().apply { seed(bookUuid, present) }
            viewModel(repo, online = false).first.uiState.value.isOnline shouldBe false
        }
    }

    describe("opening the confirmation") {

        it("#119 TC-27: openConfirm opens only when the row is enabled") {
            val repo = FakeWishlistRepository()
            for (state in listOf(WishlistState.Unknown, WishlistState.Absent, WishlistState.Failed)) {
                repo.seed(bookUuid, state)
                val (vm, _) = viewModel(repo)
                vm.openConfirm()
                vm.uiState.value.confirmOpen shouldBe false
            }

            repo.seed(bookUuid, present)
            val (offline, _) = viewModel(repo, online = false)
            offline.openConfirm()
            offline.uiState.value.confirmOpen shouldBe false

            val (online, _) = viewModel(repo, online = true)
            online.openConfirm()
            online.uiState.value.confirmOpen shouldBe true
        }

        it("#119 TC-27: openConfirm is ignored while a removal is in flight") {
            val repo = FakeWishlistRepository().apply { seed(bookUuid, present) }
            val gate = CompletableDeferred<Unit>()
            repo.removalGates.addLast(gate)
            val (vm, _) = viewModel(repo)

            vm.openConfirm()
            vm.confirmRemoval()
            vm.openConfirm()
            vm.uiState.value.confirmOpen shouldBe false
            gate.complete(Unit)
        }

        it("#119 TC-28: openConfirm then dismissConfirm closes it with no repository call") {
            val repo = FakeWishlistRepository().apply { seed(bookUuid, present) }
            val (vm, _) = viewModel(repo)

            vm.openConfirm()
            vm.uiState.value.confirmOpen shouldBe true
            vm.dismissConfirm()
            vm.uiState.value.confirmOpen shouldBe false
            repo.removeCalls shouldBe emptyList()
            repo.refreshCalls shouldBe emptyList()
        }

        it("#119 TC-34: the confirmation lives in the VM, so it survives a new collector (a recreated Activity)") {
            val repo = FakeWishlistRepository().apply { seed(bookUuid, present) }
            val (vm, _) = viewModel(repo)
            vm.openConfirm()

            val scope = CoroutineScope(UnconfinedTestDispatcher())
            val first = scope.launch { vm.uiState.collect { } }
            first.cancel()
            val second = scope.launch { vm.uiState.collect { } }
            vm.uiState.value.confirmOpen shouldBe true
            second.cancel()
        }
    }

    describe("removing the book") {

        it("#119 TC-29: confirmRemoval closes the dialog at once and sends exactly one request") {
            val repo = FakeWishlistRepository().apply { seed(bookUuid, present) }
            val gate = CompletableDeferred<Unit>()
            repo.removalGates.addLast(gate)
            val (vm, _) = viewModel(repo)
            vm.openConfirm()

            vm.confirmRemoval()
            vm.uiState.value.confirmOpen shouldBe false
            vm.uiState.value.removing shouldBe true
            repo.removeCalls shouldBe listOf(bookUuid)

            gate.complete(Unit)
            repo.removeCalls shouldBe listOf(bookUuid)
        }

        it("#119 TC-30: further confirms and opens while the request is in flight send nothing") {
            val repo = FakeWishlistRepository().apply { seed(bookUuid, present) }
            val gate = CompletableDeferred<Unit>()
            repo.removalGates.addLast(gate)
            val (vm, _) = viewModel(repo)
            vm.openConfirm()
            vm.confirmRemoval()

            vm.confirmRemoval()
            vm.confirmRemoval()
            vm.openConfirm()
            vm.uiState.value.confirmOpen shouldBe false
            repo.removeCalls shouldBe listOf(bookUuid)

            gate.complete(Unit)
            repo.removeCalls shouldBe listOf(bookUuid)
        }

        it("#119 TC-31: each removal result is reported once with a rising id, and outcomeShown clears only its own") {
            val repo = FakeWishlistRepository().apply { seed(bookUuid, present) }
            repo.removalResults.addLast(WishlistRemovalResult.Removed(bookDeleted = true))
            repo.removalResults.addLast(WishlistRemovalResult.Removed(bookDeleted = false))
            val (vm, _) = viewModel(repo)

            vm.openConfirm()
            vm.confirmRemoval()
            val first = vm.uiState.value.outcome
            first shouldBe RemovalOutcome.Removed(eventId = 1, bookDeleted = true)
            vm.uiState.value.removing shouldBe false

            vm.outcomeShown(1)
            vm.uiState.value.outcome.shouldBeNull()

            // The fake sets Absent after a removal, so the row is re-armed by seeding Present again.
            repo.seed(bookUuid, present)
            vm.openConfirm()
            vm.confirmRemoval()
            val second = vm.uiState.value.outcome
            second shouldBe RemovalOutcome.Removed(eventId = 2, bookDeleted = false)

            // A stale id does not clear a newer outcome.
            vm.outcomeShown(1)
            vm.uiState.value.outcome shouldBe second
            vm.outcomeShown(2)
            vm.uiState.value.outcome.shouldBeNull()
        }

        it("#119 TC-32: each failure is reported once with a new id, and the state and retry are unchanged") {
            val repo = FakeWishlistRepository().apply { seed(bookUuid, present) }
            val errors = listOf(
                WishlistError.NoServer,
                WishlistError.Unreachable,
                WishlistError.SessionExpired,
                WishlistError.BookNotFound,
                WishlistError.Http(500),
                WishlistError.UnexpectedResponse,
            )
            val (vm, _) = viewModel(repo)
            for ((index, error) in errors.withIndex()) {
                repo.removalResults.addLast(WishlistRemovalResult.Failed(error))
                vm.openConfirm()
                vm.confirmRemoval()
                vm.uiState.value.outcome shouldBe RemovalOutcome.Failed(eventId = index + 1L)
                vm.uiState.value.state shouldBe present
                vm.uiState.value.removing shouldBe false
                vm.outcomeShown(index + 1L)
            }
            repo.removeCalls.size shouldBe errors.size
        }
    }

    describe("reloading the page") {

        it("#119 TC-33: the first key is only recorded, and each new key refreshes once") {
            val repo = FakeWishlistRepository().apply { seed(bookUuid, present) }
            val (vm, _) = viewModel(repo)

            vm.pageReloaded(1)
            vm.pageReloaded(1)
            vm.pageReloaded(2)
            vm.pageReloaded(2)
            vm.pageReloaded(3)

            repo.refreshCalls shouldBe listOf(bookUuid, bookUuid)
        }
    }
})
