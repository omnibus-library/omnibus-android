package com.omnibus.omnibus.feature.bookdetail.wishlist

import com.omnibus.omnibus.data.network.TestConnectivityMonitor
import com.omnibus.omnibus.data.wishlist.TestWishlistRepository
import com.omnibus.omnibus.data.wishlist.WishlistError
import com.omnibus.omnibus.data.wishlist.WishlistRemovalResult
import com.omnibus.omnibus.data.wishlist.WishlistState
import com.omnibus.omnibus.data.wishlist.randomWishlistEntry
import com.omnibus.omnibus.testing.randomBoolean
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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

private fun present() = WishlistState.Present(randomWishlistEntry())

@OptIn(ExperimentalCoroutinesApi::class)
class WishlistRemovalViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun sut(
        bookUuid: String = randomString(),
        repo: TestWishlistRepository = TestWishlistRepository(),
        monitor: TestConnectivityMonitor = TestConnectivityMonitor(randomBoolean()),
    ) = WishlistRemovalViewModel(bookUuid, repo, monitor)

    /** A ViewModel, online, for a book on the wishlist. */
    fun sutWithEntry(repo: TestWishlistRepository = TestWishlistRepository()): WishlistRemovalViewModel {
        val uuid = randomString()
        repo.seed(uuid, present())
        return sut(uuid, repo, TestConnectivityMonitor(true))
    }

    describe("following the wishlist and the connection") {

        it("#119 TC-26: mirrors the connection") {
            val monitor = TestConnectivityMonitor(true)
            val vm = sut(monitor = monitor)

            monitor.isOnline.value = false

            vm.uiState.value.isOnline shouldBe false
        }
    }

    describe("opening the confirmation") {

        context("when the book is on the wishlist") {
            context("when the device is online") {
                it("#119 TC-27: opens") {
                    val vm = sutWithEntry()

                    vm.openConfirm()

                    vm.uiState.value.confirmOpen shouldBe true
                }
            }

            context("when the device is offline") {
                it("#119 TC-27: stays closed") {
                    val uuid = randomString()
                    val repo = TestWishlistRepository()
                    repo.seed(uuid, present())
                    val vm = sut(uuid, repo, TestConnectivityMonitor(false))

                    vm.openConfirm()

                    vm.uiState.value.confirmOpen shouldBe false
                }
            }
        }

        context("when it is dismissed") {
            it("#119 TC-28: closes with no removal") {
                val repo = TestWishlistRepository()
                val vm = sutWithEntry(repo)
                vm.openConfirm()

                vm.dismissConfirm()

                vm.uiState.value.confirmOpen shouldBe false
                repo.removeCalls shouldBe emptyList()
            }
        }
    }

    describe("removing the book") {

        context("when the removal is in flight") {
            it("#119 TC-29: closes the dialog at once, showing it is removing") {
                val repo = TestWishlistRepository()
                repo.removalGates.addLast(CompletableDeferred())
                val vm = sutWithEntry(repo)
                vm.openConfirm()

                vm.confirmRemoval()

                vm.uiState.value.confirmOpen shouldBe false
                vm.uiState.value.removing shouldBe true
            }

            it("#119 TC-30: sends no second request") {
                val repo = TestWishlistRepository()
                repo.removalGates.addLast(CompletableDeferred())
                val vm = sutWithEntry(repo)
                vm.confirmRemoval()

                vm.confirmRemoval()

                repo.removeCalls.size shouldBe 1
            }
        }

        context("when the removal succeeds") {
            it("#119 TC-31: reports it, saying whether the book was deleted") {
                checkAll(Exhaustive.of(true, false)) { bookDeleted ->
                    val repo = TestWishlistRepository()
                    repo.removalResults.addLast(WishlistRemovalResult.Removed(bookDeleted))
                    val vm = sutWithEntry(repo)

                    vm.confirmRemoval()

                    vm.uiState.value.outcome shouldBe RemovalOutcome.Removed(eventId = 1, bookDeleted = bookDeleted)
                }
            }
        }

        context("when the removal fails") {
            it("#119 TC-32: reports the failure") {
                val repo = TestWishlistRepository()
                repo.removalResults.addLast(WishlistRemovalResult.Failed(WishlistError.Unreachable))
                val vm = sutWithEntry(repo)

                vm.confirmRemoval()

                vm.uiState.value.outcome shouldBe RemovalOutcome.Failed(eventId = 1)
            }
        }

        context("when the outcome has been shown") {
            it("#119 TC-31: clears it") {
                val vm = sutWithEntry()
                vm.confirmRemoval()

                vm.outcomeShown(1)

                vm.uiState.value.outcome.shouldBeNull()
            }
        }

        context("when an older outcome is marked shown") {
            it("#119 TC-31: keeps the newer one") {
                val repo = TestWishlistRepository()
                repo.removalResults.addLast(WishlistRemovalResult.Failed(WishlistError.Unreachable))
                val vm = sutWithEntry(repo)
                vm.confirmRemoval()
                vm.confirmRemoval()

                vm.outcomeShown(1)

                vm.uiState.value.outcome shouldBe RemovalOutcome.Removed(eventId = 2, bookDeleted = false)
            }
        }
    }

    describe("reloading the page") {

        it("#119 TC-33: skips the first key, then refreshes once per new key") {
            val uuid = randomString()
            val repo = TestWishlistRepository()
            val vm = sut(uuid, repo)

            vm.pageReloaded(1)
            vm.pageReloaded(2)
            vm.pageReloaded(2)

            repo.refreshCalls shouldBe listOf(uuid)
        }
    }
})
