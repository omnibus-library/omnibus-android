package com.omnibus.omnibus.feature.bookdetail.more.shelves

import com.omnibus.omnibus.data.auth.CurrentUser
import com.omnibus.omnibus.data.auth.FakeAuthRepository
import com.omnibus.omnibus.data.library.ShelfKind
import com.omnibus.omnibus.data.network.FakeConnectivityMonitor
import com.omnibus.omnibus.data.shelves.BookShelves
import com.omnibus.omnibus.data.shelves.BookShelvesResult
import com.omnibus.omnibus.data.shelves.ShelfMatchMode
import com.omnibus.omnibus.data.shelves.ShelfRule
import com.omnibus.omnibus.data.shelves.ShelfRuleField
import com.omnibus.omnibus.data.shelves.ShelfRuleOp
import com.omnibus.omnibus.data.shelves.ShelfRules
import com.omnibus.omnibus.data.shelves.ShelfRulesResult
import com.omnibus.omnibus.data.shelves.ShelfSummary
import com.omnibus.omnibus.data.shelves.ShelfWriteResult
import com.omnibus.omnibus.data.shelves.ShelvesError
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class AddToShelfViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    val bookUuid = "book-1"
    val user = CurrentUser(id = 1, canEdit = true, canUpload = false, hiddenFormats = emptySet())

    /** Favourites (not on the book), Book club (on the book on the server), a wishlist and a matching smart shelf. */
    fun standardShelves(memberShelfIds: Set<Long> = emptySet()) = BookShelves(
        shelves = listOf(
            ShelfSummary(1, 1, ShelfKind.Manual, "Favourites", 3, listOf("a")),
            ShelfSummary(2, 1, ShelfKind.Manual, "Book club", 5, listOf("b", "c")),
            ShelfSummary(3, 1, ShelfKind.Wishlist, "Wishlist", 9, emptyList()),
            ShelfSummary(4, 1, ShelfKind.Smart, "Classics", 14, listOf("d")),
        ),
        memberShelfIds = memberShelfIds,
        matchingSmartShelfIds = setOf(4),
    )

    /** The one place a test builds the ViewModel, so later changes only touch this helper. */
    fun viewModel(
        repo: ScriptedShelvesRepository,
        auth: FakeAuthRepository = FakeAuthRepository().also { it.currentUserState.value = user },
        online: Boolean = true,
    ) = AddToShelfViewModel(bookUuid, repo, auth, FakeConnectivityMonitor(online))

    fun AddToShelfViewModel.loaded(): ShelfSheetContent.Loaded =
        uiState.value.content as ShelfSheetContent.Loaded

    fun AddToShelfViewModel.row(id: Long): ManualShelfRow = loaded().manual.single { it.id == id }

    val failureKinds: List<Pair<String, ShelfWriteResult>> = listOf(
        "no server" to ShelfWriteResult.NoServer,
        "unreachable" to ShelfWriteResult.Failure(ShelvesError.Unreachable),
        "session expired" to ShelfWriteResult.Failure(ShelvesError.SessionExpired),
        "forbidden" to ShelfWriteResult.Failure(ShelvesError.Forbidden),
        "not found" to ShelfWriteResult.Failure(ShelvesError.NotFound),
        "422" to ShelfWriteResult.Failure(ShelvesError.Http(422, "invalid")),
        "500" to ShelfWriteResult.Failure(ShelvesError.Http(500, null)),
        "unexpected response" to ShelfWriteResult.Failure(ShelvesError.UnexpectedResponse),
    )

    describe("opening and loading") {

        it("#116 TC-14: openSheet opens in Loading and a newer load wins") {
            val repo = ScriptedShelvesRepository()
            val stale = repo.holdBookShelves()
            val vm = viewModel(repo)

            vm.openSheet()
            vm.uiState.value.sheetOpen shouldBe true
            vm.uiState.value.content shouldBe ShelfSheetContent.Loading

            repo.bookShelves = BookShelvesResult.Success(standardShelves())
            vm.openSheet()
            vm.loaded().manual.map { it.name } shouldBe listOf("Favourites", "Book club")

            stale.complete(BookShelvesResult.Success(standardShelves(setOf(1))))
            vm.row(1).isOn shouldBe false
            repo.bookShelvesRequests.size shouldBe 2
        }

        it("#116 TC-34: a missing user, no server or a failed read shows Failed") {
            val noUser = ScriptedShelvesRepository()
            val auth = FakeAuthRepository()
            viewModel(noUser, auth = auth).apply { openSheet() }.uiState.value.content shouldBe ShelfSheetContent.Failed

            val failures = listOf(
                BookShelvesResult.NoServer,
                BookShelvesResult.Failure(ShelvesError.Unreachable),
                BookShelvesResult.Failure(ShelvesError.Http(500, null)),
            )
            for (result in failures) {
                val repo = ScriptedShelvesRepository().apply { bookShelves = result }
                viewModel(repo).apply { openSheet() }.uiState.value.content shouldBe ShelfSheetContent.Failed
            }
        }

        it("#116 TC-34: retry goes back to Loading, then Loaded") {
            val repo = ScriptedShelvesRepository().apply {
                bookShelves = BookShelvesResult.Failure(ShelvesError.Unreachable)
            }
            val vm = viewModel(repo)
            vm.openSheet()
            vm.uiState.value.content shouldBe ShelfSheetContent.Failed

            repo.bookShelves = BookShelvesResult.Success(standardShelves())
            val held = repo.holdBookShelves()
            vm.retry()
            vm.uiState.value.content shouldBe ShelfSheetContent.Loading

            held.complete(BookShelvesResult.Success(standardShelves()))
            vm.loaded().manual.size shouldBe 2
        }

        it("#116 TC-34: every openSheet re-reads, so reopening shows the server's data") {
            val repo = ScriptedShelvesRepository().apply { bookShelves = BookShelvesResult.Success(standardShelves()) }
            val vm = viewModel(repo)
            vm.openSheet()
            vm.toggle(1)
            vm.row(1).isOn shouldBe true
            vm.closeSheet()

            // The server now counts the book on Favourites: 3 before the add, 4 after.
            val afterAdd = standardShelves(setOf(1)).let { base ->
                base.copy(shelves = base.shelves.map { if (it.id == 1L) it.copy(bookCount = 4) else it })
            }
            repo.bookShelves = BookShelvesResult.Success(afterAdd)
            vm.openSheet()

            repo.bookShelvesRequests.size shouldBe 2
            vm.row(1).isOn shouldBe true
            vm.row(1).bookCount shouldBe 4
        }

        it("#116 TC-35: rules fill smart rows once each, and a failing read leaves rules null") {
            val repo = ScriptedShelvesRepository().apply {
                val base = standardShelves()
                bookShelves = BookShelvesResult.Success(
                    base.copy(
                        shelves = base.shelves + ShelfSummary(6, 1, ShelfKind.Smart, "Short", 2, emptyList()),
                        matchingSmartShelfIds = setOf(4, 6),
                    ),
                )
                rulesFor[4] = ShelfRulesResult.Success(
                    ShelfRules(ShelfMatchMode.All, listOf(ShelfRule(ShelfRuleField.Year, ShelfRuleOp.Gte, "1901"))),
                )
                rules = ShelfRulesResult.NoServer
            }
            val vm = viewModel(repo)

            vm.openSheet()

            repo.rulesRequests shouldBe listOf(4L, 6L)
            val smart = vm.loaded().smart.associateBy { it.id }
            smart.getValue(4L).rules.shouldNotBeNull()
            smart.getValue(6L).rules.shouldBeNull()
        }

        it("#116 TC-35: rules arriving after the sheet closes are discarded") {
            val repo = ScriptedShelvesRepository().apply { bookShelves = BookShelvesResult.Success(standardShelves()) }
            val held = repo.holdRules(4)
            val vm = viewModel(repo)

            vm.openSheet()
            vm.closeSheet()
            held.complete(ShelfRulesResult.Success(ShelfRules(ShelfMatchMode.All, emptyList())))

            vm.loaded().smart.single().rules.shouldBeNull()
        }

        it("#116 TC-36: pageReloaded records the first key, reloads only while open, and ignores a repeat") {
            val repo = ScriptedShelvesRepository().apply { bookShelves = BookShelvesResult.Success(standardShelves()) }
            val vm = viewModel(repo)

            vm.pageReloaded(0)
            repo.bookShelvesRequests.size shouldBe 0
            vm.pageReloaded(0)
            vm.pageReloaded(1)
            repo.bookShelvesRequests.size shouldBe 0

            vm.openSheet()
            repo.bookShelvesRequests.size shouldBe 1
            vm.pageReloaded(1)
            repo.bookShelvesRequests.size shouldBe 1
            vm.pageReloaded(2)
            repo.bookShelvesRequests.size shouldBe 2
        }

        it("#116 TC-36: a reload keeps a pending toggle's optimistic state") {
            val repo = ScriptedShelvesRepository().apply { bookShelves = BookShelvesResult.Success(standardShelves()) }
            val held = repo.holdWrite(1)
            val vm = viewModel(repo)
            vm.openSheet()
            vm.toggle(1)

            repo.bookShelves = BookShelvesResult.Success(standardShelves())
            vm.pageReloaded(0)
            vm.pageReloaded(1)

            vm.row(1).isOn shouldBe true
            vm.row(1).isPending shouldBe true
            held.complete(ShelfWriteResult.Success)
            vm.row(1).isPending shouldBe false
        }

        it("#116 TC-69: an admin sees only their own manual shelves through a full load") {
            val admin = user.copy(isAdmin = true)
            val repo = ScriptedShelvesRepository().apply {
                bookShelves = BookShelvesResult.Success(
                    BookShelves(
                        shelves = listOf(
                            ShelfSummary(1, 1, ShelfKind.Manual, "Mine", 1, emptyList()),
                            ShelfSummary(2, 7, ShelfKind.Manual, "Someone else", 1, emptyList()),
                            ShelfSummary(3, 7, ShelfKind.Smart, "Their smart", 1, emptyList()),
                        ),
                        memberShelfIds = emptySet(),
                        matchingSmartShelfIds = setOf(3),
                    ),
                )
            }
            val auth = FakeAuthRepository().also { it.currentUserState.value = admin }

            viewModel(repo, auth = auth).apply { openSheet() }.loaded().manual.map { it.id } shouldBe listOf(1L)
        }
    }

    describe("connectivity") {

        it("#116 TC-16: isOnline follows the connectivity monitor and defaults to true") {
            val monitor = FakeConnectivityMonitor(online = true)
            val vm = AddToShelfViewModel(bookUuid, ScriptedShelvesRepository(), FakeAuthRepository(), monitor)
            vm.uiState.value.isOnline shouldBe true

            monitor.isOnline.value = false
            vm.uiState.value.isOnline shouldBe false

            monitor.isOnline.value = true
            vm.uiState.value.isOnline shouldBe true
        }
    }

    describe("toggling") {

        it("#116 TC-27: toggle adds optimistically, count plus one, and keeps it on success") {
            val repo = ScriptedShelvesRepository().apply { bookShelves = BookShelvesResult.Success(standardShelves()) }
            val held = repo.holdWrite(1)
            val vm = viewModel(repo)
            vm.openSheet()

            vm.toggle(1)

            vm.row(1).let {
                it.isOn shouldBe true
                it.bookCount shouldBe 4
                it.isPending shouldBe true
            }
            repo.writes shouldBe listOf(ShelfWrite(add = true, shelfId = 1, bookUuid = bookUuid))

            held.complete(ShelfWriteResult.Success)
            vm.row(1).let {
                it.isOn shouldBe true
                it.bookCount shouldBe 4
                it.isPending shouldBe false
            }
            vm.uiState.value.failure.shouldBeNull()
        }

        it("#116 TC-28: toggle removes optimistically, count minus one, and keeps it on success") {
            val repo = ScriptedShelvesRepository().apply {
                bookShelves = BookShelvesResult.Success(standardShelves(setOf(2)))
            }
            val held = repo.holdWrite(2)
            val vm = viewModel(repo)
            vm.openSheet()

            vm.toggle(2)

            vm.row(2).let {
                it.isOn shouldBe false
                it.bookCount shouldBe 4
            }
            repo.writes shouldBe listOf(ShelfWrite(add = false, shelfId = 2, bookUuid = bookUuid))

            held.complete(ShelfWriteResult.Success)
            vm.row(2).isOn shouldBe false
            vm.row(2).bookCount shouldBe 4
        }

        it("#116 TC-29: taps on a pending shelf are ignored while other shelves stay usable") {
            val repo = ScriptedShelvesRepository().apply { bookShelves = BookShelvesResult.Success(standardShelves()) }
            val held = repo.holdWrite(1)
            val vm = viewModel(repo)
            vm.openSheet()

            vm.toggle(1)
            vm.toggle(1)
            vm.toggle(1)
            repo.writes.size shouldBe 1

            vm.toggle(2)
            repo.writes.map { it.shelfId } shouldBe listOf(1L, 2L)
            vm.row(2).isOn shouldBe true
            vm.row(2).isPending shouldBe false

            held.complete(ShelfWriteResult.Success)
            vm.toggle(1)
            repo.writes.size shouldBe 3
            vm.row(1).isOn shouldBe false
        }

        it("#116 TC-29: toggle is ignored while loading or failed") {
            val repo = ScriptedShelvesRepository()
            val held = repo.holdBookShelves()
            val vm = viewModel(repo)
            vm.openSheet()

            vm.toggle(1)
            repo.writes.size shouldBe 0

            held.complete(BookShelvesResult.Failure(ShelvesError.Unreachable))
            vm.uiState.value.content shouldBe ShelfSheetContent.Failed
            vm.toggle(1)
            repo.writes.size shouldBe 0
        }

        it("#116 TC-70: add then remove returns to the server count") {
            val repo = ScriptedShelvesRepository().apply { bookShelves = BookShelvesResult.Success(standardShelves()) }
            val vm = viewModel(repo)
            vm.openSheet()

            vm.toggle(1)
            vm.row(1).bookCount shouldBe 4
            vm.toggle(1)
            vm.row(1).let {
                it.bookCount shouldBe 3
                it.isOn shouldBe false
            }
            repo.writes.map { it.add } shouldBe listOf(true, false)
        }

        for ((label, result) in failureKinds) {
            it("#116 TC-30: failed write reverts and reports (add, $label)") {
                val repo = ScriptedShelvesRepository().apply {
                    bookShelves = BookShelvesResult.Success(standardShelves())
                    writeResult = result
                }
                val vm = viewModel(repo)
                vm.openSheet()
                val before = vm.row(1)

                vm.toggle(1)

                vm.row(1) shouldBe before
                val failure = vm.uiState.value.failure.shouldNotBeNull()
                failure.shelfName shouldBe "Favourites"
                failure.wasAdding shouldBe true
            }

            it("#116 TC-30: failed write reverts and reports (remove, $label)") {
                val repo = ScriptedShelvesRepository().apply {
                    bookShelves = BookShelvesResult.Success(standardShelves(setOf(2)))
                    writeResult = result
                }
                val vm = viewModel(repo)
                vm.openSheet()
                val before = vm.row(2)

                vm.toggle(2)

                vm.row(2) shouldBe before
                val failure = vm.uiState.value.failure.shouldNotBeNull()
                failure.shelfName shouldBe "Book club"
                failure.wasAdding shouldBe false
            }
        }

        it("#116 TC-31: failureShown clears only the matching event and a newer failure stays") {
            val repo = ScriptedShelvesRepository().apply {
                bookShelves = BookShelvesResult.Success(standardShelves())
                writeResult = ShelfWriteResult.Failure(ShelvesError.Unreachable)
            }
            val vm = viewModel(repo)
            vm.openSheet()

            vm.toggle(1)
            val first = vm.uiState.value.failure.shouldNotBeNull().eventId
            vm.toggle(2)
            val second = vm.uiState.value.failure.shouldNotBeNull().eventId
            (second > first) shouldBe true

            vm.failureShown(first)
            vm.uiState.value.failure?.eventId shouldBe second

            vm.failureShown(second)
            vm.uiState.value.failure.shouldBeNull()
        }

        it("#116 TC-32: a failure after close still reverts and is reported for the page") {
            val repo = ScriptedShelvesRepository().apply { bookShelves = BookShelvesResult.Success(standardShelves()) }
            val held = repo.holdWrite(1)
            val vm = viewModel(repo)
            vm.openSheet()
            vm.toggle(1)

            vm.closeSheet()
            held.complete(ShelfWriteResult.Failure(ShelvesError.Unreachable))

            vm.uiState.value.sheetOpen shouldBe false
            vm.uiState.value.failure.shouldNotBeNull().wasAdding shouldBe true
            vm.row(1).isOn shouldBe false
            vm.row(1).isPending shouldBe false
        }

        it("#116 TC-32: a failure while open leaves the sheet open with the failure set") {
            val repo = ScriptedShelvesRepository().apply {
                bookShelves = BookShelvesResult.Success(standardShelves())
                writeResult = ShelfWriteResult.Failure(ShelvesError.Unreachable)
            }
            val vm = viewModel(repo)
            vm.openSheet()

            vm.toggle(1)

            vm.uiState.value.sheetOpen shouldBe true
            vm.uiState.value.failure.shouldNotBeNull()
        }
    }
})
