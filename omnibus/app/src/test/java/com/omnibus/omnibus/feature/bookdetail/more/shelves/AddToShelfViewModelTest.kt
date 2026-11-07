package com.omnibus.omnibus.feature.bookdetail.more.shelves

import com.omnibus.omnibus.data.auth.TestAuthRepository
import com.omnibus.omnibus.data.auth.randomCurrentUser
import com.omnibus.omnibus.data.library.ShelfKind
import com.omnibus.omnibus.data.network.TestConnectivityMonitor
import com.omnibus.omnibus.data.shelves.BookShelves
import com.omnibus.omnibus.data.shelves.BookShelvesResult
import com.omnibus.omnibus.data.shelves.ShelfMatchMode
import com.omnibus.omnibus.data.shelves.ShelfRules
import com.omnibus.omnibus.data.shelves.ShelfRulesResult
import com.omnibus.omnibus.data.shelves.ShelfSummary
import com.omnibus.omnibus.data.shelves.ShelfWriteResult
import com.omnibus.omnibus.data.shelves.ShelvesError
import com.omnibus.omnibus.data.shelves.randomShelfSummary
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.boolean
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** A signed-in user with two manual shelves and one matching smart shelf, and the fakes behind their ViewModel. */
private class Scene(onFirst: Boolean = false, onSecond: Boolean = false) {
    private val user = randomCurrentUser()
    val first: ShelfSummary = manualShelf()
    val second: ShelfSummary = manualShelf()
    val smart: ShelfSummary = randomShelfSummary().copy(ownerUserId = user.id, kind = ShelfKind.Smart)
    val shelves = BookShelves(
        shelves = listOf(first, second, smart),
        memberShelfIds = setOfNotNull(first.id.takeIf { onFirst }, second.id.takeIf { onSecond }),
        matchingSmartShelfIds = setOf(smart.id),
    )
    val repo = ScriptedShelvesRepository().apply { bookShelves = BookShelvesResult.Success(shelves) }
    val auth = TestAuthRepository().also { it.currentUserState.value = user }
    val bookUuid = randomString()
    val vm = AddToShelfViewModel(bookUuid, repo, auth, TestConnectivityMonitor())

    private fun manualShelf(): ShelfSummary = randomShelfSummary().copy(
        ownerUserId = user.id,
        kind = ShelfKind.Manual,
        bookCount = randomLong(1L..1_000L),
    )
}

private fun AddToShelfViewModel.loaded(): ShelfSheetContent.Loaded =
    uiState.value.content as ShelfSheetContent.Loaded

private fun AddToShelfViewModel.row(shelf: ShelfSummary): ManualShelfRow = loaded().manual.single { it.id == shelf.id }

@OptIn(ExperimentalCoroutinesApi::class)
class AddToShelfViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    describe("opening the sheet") {
        context("when a newer read overtakes an older one") {
            it("#116 TC-14: keeps the newer read") {
                val scene = Scene()
                val stale = scene.repo.holdBookShelves()
                scene.vm.openSheet()

                scene.vm.openSheet()
                stale.complete(BookShelvesResult.Success(scene.shelves.copy(memberShelfIds = setOf(scene.first.id))))

                scene.vm.row(scene.first).isOn shouldBe false
            }
        }

        context("when no user is signed in") {
            it("#116 TC-34: shows the failed state") {
                val scene = Scene()
                scene.auth.currentUserState.value = null

                scene.vm.openSheet()

                scene.vm.uiState.value.content shouldBe ShelfSheetContent.Failed
            }
        }

        context("when the read fails") {
            it("#116 TC-34: shows the failed state, for no server or a server error") {
                checkAll(
                    Exhaustive.of(BookShelvesResult.NoServer, BookShelvesResult.Failure(ShelvesError.Unreachable)),
                ) { result ->
                    val scene = Scene()
                    scene.repo.bookShelves = result

                    scene.vm.openSheet()

                    scene.vm.uiState.value.content shouldBe ShelfSheetContent.Failed
                }
            }

            it("#116 TC-34: reaches the shelves on retry") {
                val scene = Scene()
                val shelves = scene.repo.bookShelves
                scene.repo.bookShelves = BookShelvesResult.NoServer
                scene.vm.openSheet()

                scene.repo.bookShelves = shelves
                scene.vm.retry()

                scene.vm.loaded().manual.size shouldBe 2
            }
        }

        context("when it is opened again") {
            it("#116 TC-34: shows the server's new data") {
                val scene = Scene()
                scene.vm.openSheet()
                scene.vm.closeSheet()
                val count = randomLong(1L..1_000L)
                scene.repo.bookShelves = BookShelvesResult.Success(
                    scene.shelves.copy(
                        shelves = listOf(scene.first.copy(bookCount = count), scene.second, scene.smart),
                        memberShelfIds = setOf(scene.first.id),
                    ),
                )

                scene.vm.openSheet()

                scene.vm.row(scene.first).isOn shouldBe true
                scene.vm.row(scene.first).bookCount shouldBe count
            }
        }

        it("#116 TC-35: fills in the rules of each matching smart shelf") {
            val scene = Scene()
            val rules = ShelfRules(ShelfMatchMode.All, emptyList())
            scene.repo.rulesFor[scene.smart.id] = ShelfRulesResult.Success(rules)

            scene.vm.openSheet()

            scene.vm.loaded().smart.single().rules shouldBe rules
        }
    }

    describe("the page reloading") {
        context("when the sheet is open") {
            it("#116 TC-36: reads again only for a new key") {
                val scene = Scene()
                scene.vm.pageReloaded(0)
                scene.vm.openSheet()

                scene.vm.pageReloaded(0)
                scene.repo.bookShelvesRequests.size shouldBe 1

                scene.vm.pageReloaded(1)
                scene.repo.bookShelvesRequests.size shouldBe 2
            }

            context("when a toggle is pending") {
                it("#116 TC-36: keeps its optimistic state") {
                    val scene = Scene()
                    scene.repo.holdWrite(scene.first.id)
                    scene.vm.pageReloaded(0)
                    scene.vm.openSheet()
                    scene.vm.toggle(scene.first.id)

                    scene.vm.pageReloaded(1)

                    scene.vm.row(scene.first).isOn shouldBe true
                    scene.vm.row(scene.first).isPending shouldBe true
                }
            }
        }
    }

    describe("connectivity") {
        it("#116 TC-16: follows the monitor") {
            val monitor = TestConnectivityMonitor()
            val vm = AddToShelfViewModel(randomString(), ScriptedShelvesRepository(), TestAuthRepository(), monitor)

            monitor.isOnline.value = false

            vm.uiState.value.isOnline shouldBe false
        }
    }

    describe("toggling a shelf") {
        context("when the book is off the shelf") {
            it("#116 TC-27: adds it optimistically, counting it at once") {
                val scene = Scene()
                val held = scene.repo.holdWrite(scene.first.id)
                scene.vm.openSheet()

                scene.vm.toggle(scene.first.id)

                scene.repo.writes shouldBe listOf(ShelfWrite(add = true, shelfId = scene.first.id, bookUuid = scene.bookUuid))
                scene.vm.row(scene.first).isOn shouldBe true
                scene.vm.row(scene.first).bookCount shouldBe scene.first.bookCount + 1
                scene.vm.row(scene.first).isPending shouldBe true

                held.complete(ShelfWriteResult.Success)
                scene.vm.row(scene.first).isOn shouldBe true
                scene.vm.row(scene.first).isPending shouldBe false
            }
        }

        context("when the book is on the shelf") {
            it("#116 TC-28: removes it optimistically, counting it at once") {
                val scene = Scene(onFirst = true)
                scene.repo.holdWrite(scene.first.id)
                scene.vm.openSheet()

                scene.vm.toggle(scene.first.id)

                scene.repo.writes shouldBe listOf(ShelfWrite(add = false, shelfId = scene.first.id, bookUuid = scene.bookUuid))
                scene.vm.row(scene.first).isOn shouldBe false
                scene.vm.row(scene.first).bookCount shouldBe scene.first.bookCount - 1
            }
        }

        context("when the shelf has a write in flight") {
            it("#116 TC-29: ignores more taps on it, leaving other shelves usable") {
                val scene = Scene()
                scene.repo.holdWrite(scene.first.id)
                scene.vm.openSheet()

                scene.vm.toggle(scene.first.id)
                scene.vm.toggle(scene.first.id)
                scene.vm.toggle(scene.second.id)

                scene.repo.writes.map { it.shelfId } shouldBe listOf(scene.first.id, scene.second.id)
            }
        }

        context("when the write fails") {
            it("#116 TC-30: reverts the row with a report, regardless of direction or failure kind") {
                checkAll(
                    Exhaustive.boolean(),
                    Exhaustive.of(
                        ShelfWriteResult.NoServer,
                        ShelfWriteResult.Failure(ShelvesError.Unreachable),
                        ShelfWriteResult.Failure(ShelvesError.Http(500, null)),
                    ),
                ) { wasOn, result ->
                    val scene = Scene(onFirst = wasOn)
                    scene.repo.writeResult = result
                    scene.vm.openSheet()
                    val before = scene.vm.row(scene.first)

                    scene.vm.toggle(scene.first.id)

                    scene.vm.row(scene.first) shouldBe before
                    val failure = scene.vm.uiState.value.failure.shouldNotBeNull()
                    failure.shelfName shouldBe scene.first.name
                    failure.wasAdding shouldBe !wasOn
                }
            }

            it("#116 TC-31: clears the report only for its own event") {
                val scene = Scene()
                scene.repo.writeResult = ShelfWriteResult.NoServer
                scene.vm.openSheet()
                scene.vm.toggle(scene.first.id)
                val first = scene.vm.uiState.value.failure.shouldNotBeNull().eventId
                scene.vm.toggle(scene.second.id)
                val second = scene.vm.uiState.value.failure.shouldNotBeNull().eventId

                scene.vm.failureShown(first)
                scene.vm.uiState.value.failure?.eventId shouldBe second

                scene.vm.failureShown(second)
                scene.vm.uiState.value.failure.shouldBeNull()
            }

            context("when the sheet has closed") {
                it("#116 TC-32: still reverts the row with a report") {
                    val scene = Scene()
                    val held = scene.repo.holdWrite(scene.first.id)
                    scene.vm.openSheet()
                    scene.vm.toggle(scene.first.id)

                    scene.vm.closeSheet()
                    held.complete(ShelfWriteResult.NoServer)

                    scene.vm.row(scene.first).isOn shouldBe false
                    scene.vm.uiState.value.failure.shouldNotBeNull()
                }
            }
        }
    }
})
