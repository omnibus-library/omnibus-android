package com.omnibus.omnibus.feature.shelves.detail

import com.omnibus.omnibus.data.auth.TestAuthRepository
import com.omnibus.omnibus.data.library.ShelfPreviewsResult
import com.omnibus.omnibus.data.library.TestLibraryRepository
import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.data.shelves.ShelfBooksResult
import com.omnibus.omnibus.data.shelves.ShelfDetail
import com.omnibus.omnibus.data.shelves.FilterClause
import com.omnibus.omnibus.data.shelves.FilterField
import com.omnibus.omnibus.data.shelves.FilterMode
import com.omnibus.omnibus.data.shelves.ShelfDetailResult
import com.omnibus.omnibus.data.shelves.ShelfWriteResult
import com.omnibus.omnibus.data.shelves.ShelvesError
import com.omnibus.omnibus.data.shelves.randomShelfDetail
import com.omnibus.omnibus.feature.bookdetail.more.shelves.ScriptedShelvesRepository
import com.omnibus.omnibus.feature.bookdetail.more.shelves.ShelfWrite
import com.omnibus.omnibus.testing.randomLong
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** [this] detail with its shelf's id set to [id], so a test can script reads of one shelf. */
private fun ShelfDetail.withId(id: Long): ShelfDetail = copy(shelf = shelf.copy(id = id))

@OptIn(ExperimentalCoroutinesApi::class)
class ShelfDetailViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun sut(
        shelves: ScriptedShelvesRepository = ScriptedShelvesRepository(),
        shelfId: Long = randomLong(),
        library: TestLibraryRepository = TestLibraryRepository().apply {
            defaultShelfPreviews = ShelfPreviewsResult.Success(emptyList())
        },
        auth: TestAuthRepository = TestAuthRepository(),
    ) = ShelfDetailViewModel(shelfId = shelfId, shelvesRepository = shelves, libraryRepository = library, authRepository = auth)

    describe("loading the shelf") {

        it("#232 TC-12: replaces the cached detail with the fresh one") {
            val shelves = ScriptedShelvesRepository()
            val shelfId = randomLong()
            val cached = randomShelfDetail().withId(shelfId)
            val fresh = randomShelfDetail().withId(shelfId)
            shelves.shelfEmissions[shelfId] = listOf(ShelfDetailResult.Success(cached), ShelfDetailResult.Success(fresh))
            val viewModel = sut(shelves, shelfId = shelfId)

            viewModel.screenShown()

            viewModel.uiState.value.detail shouldBe fresh
        }

        it("#232 TC-12: shows the cached books first, then the fresh ones") {
            val shelves = ScriptedShelvesRepository()
            val cached = List(2) { randomBook() }
            val fresh = List(3) { randomBook() }
            val gate = CompletableDeferred<ShelfBooksResult>()
            shelves.enqueueBooksFlow {
                emit(ShelfBooksResult.Success(cached))
                emit(gate.await())
            }
            val viewModel = sut(shelves)

            viewModel.screenShown()
            viewModel.uiState.value.books shouldBe cached

            gate.complete(ShelfBooksResult.Success(fresh))
            viewModel.uiState.value.books shouldBe fresh
        }

        it("#232 TC-12: loads again on each screenShown and on refresh") {
            val shelves = ScriptedShelvesRepository()
            val viewModel = sut(shelves)

            viewModel.screenShown()
            viewModel.screenShown()
            viewModel.refresh()

            shelves.booksRequests.size shouldBe 3
        }
    }

    describe("refreshing") {

        it("#232 TC-12: keeps the books on screen and shows a snackbar when a refresh fails") {
            val shelves = ScriptedShelvesRepository()
            val shelfId = randomLong()
            val books = List(2) { randomBook() }
            shelves.enqueueBooks(ShelfBooksResult.Success(books))
            shelves.enqueueBooks(ShelfBooksResult.Failure(ShelvesError.Unreachable))
            val viewModel = sut(shelves, shelfId = shelfId)

            viewModel.screenShown()
            viewModel.refresh()

            viewModel.uiState.value.books shouldBe books
            viewModel.uiState.value.transientError shouldBe ShelvesError.Unreachable
            viewModel.uiState.value.isRefreshing shouldBe false
        }
    }

    describe("overlapping loads") {

        it("#232 TC-12: a slow earlier read never overwrites a newer one") {
            val shelves = ScriptedShelvesRepository()
            val slow = CompletableDeferred<ShelfBooksResult>()
            val fresh = List(2) { randomBook() }
            shelves.enqueueBooks(slow)
            shelves.enqueueBooks(ShelfBooksResult.Success(fresh))
            val viewModel = sut(shelves)

            viewModel.screenShown()
            viewModel.refresh()
            slow.complete(ShelfBooksResult.Success(List(4) { randomBook() }))

            viewModel.uiState.value.books shouldBe fresh
        }
    }

    describe("a shelf that is gone") {

        it("#232 TC-8: shows the shelf as gone when its detail read answers 404") {
            val shelves = ScriptedShelvesRepository()
            val shelfId = randomLong()
            shelves.shelfEmissions[shelfId] = listOf(ShelfDetailResult.Gone)
            val viewModel = sut(shelves, shelfId = shelfId)

            viewModel.screenShown()

            viewModel.uiState.value.isGone shouldBe true
        }

        it("#232 TC-8: shows the shelf as gone when its page read answers 404") {
            val shelves = ScriptedShelvesRepository()
            shelves.enqueueBooks(ShelfBooksResult.Gone)
            val viewModel = sut(shelves)

            viewModel.screenShown()

            viewModel.uiState.value.isGone shouldBe true
        }
    }

    describe("filtering") {

        it("#232 TC-26: applying a facet reloads the page with the new clauses") {
            val shelves = ScriptedShelvesRepository()
            val shelfId = randomLong()
            val clause = FilterClause(FilterField.Genre, FilterMode.Include, listOf("Fantasy"))
            val viewModel = sut(shelves, shelfId = shelfId)
            viewModel.screenShown()

            viewModel.applyFacet(clause)

            shelves.booksRequests.last() shouldBe (shelfId to listOf(clause))
        }

        it("#232 TC-26: clearing the facets reloads the unfiltered page") {
            val shelves = ScriptedShelvesRepository()
            val shelfId = randomLong()
            val clause = FilterClause(FilterField.Tag, FilterMode.Exclude, listOf("Long"))
            val viewModel = sut(shelves, shelfId = shelfId)
            viewModel.applyFacet(clause)

            viewModel.clearFacets()

            shelves.booksRequests.last() shouldBe (shelfId to emptyList())
        }

        it("#232 TC-26: a facet change does not reload the detail") {
            val shelves = ScriptedShelvesRepository()
            val viewModel = sut(shelves)
            viewModel.screenShown()
            val detailReads = shelves.shelfRequests.size

            viewModel.applyFacet(FilterClause(FilterField.Genre, FilterMode.Include, listOf("Fantasy")))

            shelves.shelfRequests.size shouldBe detailReads
        }

        it("#232 TC-26: a facet change cancels the page read still in flight") {
            val shelves = ScriptedShelvesRepository()
            val slow = CompletableDeferred<ShelfBooksResult>()
            val fresh = List(2) { randomBook() }
            shelves.enqueueBooks(slow)
            shelves.enqueueBooks(ShelfBooksResult.Success(fresh))
            val viewModel = sut(shelves)
            viewModel.screenShown()

            viewModel.applyFacet(FilterClause(FilterField.Genre, FilterMode.Include, listOf("Fantasy")))
            slow.complete(ShelfBooksResult.Success(List(4) { randomBook() }))

            viewModel.uiState.value.books shouldBe fresh
        }

        it("#232 TC-26: keeps the clauses when the screen is shown again") {
            val shelves = ScriptedShelvesRepository()
            val shelfId = randomLong()
            val clause = FilterClause(FilterField.Author, FilterMode.Include, listOf("Le Guin"))
            val viewModel = sut(shelves, shelfId = shelfId)
            viewModel.applyFacet(clause)

            viewModel.screenShown()

            viewModel.uiState.value.clauses shouldBe listOf(clause)
            shelves.booksRequests.last() shouldBe (shelfId to listOf(clause))
        }
    }

    describe("deleting the shelf") {

        it("#232 TC-38: closes the screen once the server deletes the shelf") {
            val shelves = ScriptedShelvesRepository()
            val shelfId = randomLong()
            val viewModel = sut(shelves, shelfId = shelfId)

            viewModel.deleteShelf()

            shelves.deletes shouldBe listOf(shelfId)
            viewModel.uiState.value.closed shouldBe true
        }

        it("#232 TC-38: keeps the screen and shows the failure when the delete fails") {
            val shelves = ScriptedShelvesRepository().apply { deleteResult = ShelfWriteResult.Failure(ShelvesError.Unreachable) }
            val viewModel = sut(shelves)

            viewModel.deleteShelf()

            viewModel.uiState.value.closed shouldBe false
            viewModel.uiState.value.transientError shouldBe ShelvesError.Unreachable
        }
    }

    describe("removing a book") {

        it("#232 TC-38: takes the book off the grid at once, before the server answers") {
            val shelves = ScriptedShelvesRepository()
            val shelfId = randomLong()
            val first = randomBook().copy(uniqueIdentifier = "first")
            val second = randomBook().copy(uniqueIdentifier = "second")
            shelves.enqueueBooks(ShelfBooksResult.Success(listOf(first, second)))
            val hold = shelves.holdWrite(shelfId)
            val viewModel = sut(shelves, shelfId = shelfId)
            viewModel.screenShown()

            viewModel.removeBook("first")

            viewModel.uiState.value.books shouldBe listOf(second)
            hold.complete(ShelfWriteResult.Success)
        }

        it("#232 TC-38: keeps the book removed after the server accepts it") {
            val shelves = ScriptedShelvesRepository()
            val shelfId = randomLong()
            val first = randomBook().copy(uniqueIdentifier = "first")
            val second = randomBook().copy(uniqueIdentifier = "second")
            shelves.enqueueBooks(ShelfBooksResult.Success(listOf(first, second)))
            shelves.enqueueBooks(ShelfBooksResult.Success(listOf(second)))
            val viewModel = sut(shelves, shelfId = shelfId)
            viewModel.screenShown()

            viewModel.removeBook("first")

            viewModel.uiState.value.books shouldBe listOf(second)
            shelves.writes.last() shouldBe ShelfWrite(add = false, shelfId = shelfId, bookUuid = "first")
        }

        it("#232 TC-38: puts the book back and shows the failure when the server refuses") {
            val shelves = ScriptedShelvesRepository().apply { writeResult = ShelfWriteResult.Failure(ShelvesError.Unreachable) }
            val first = randomBook().copy(uniqueIdentifier = "first")
            val second = randomBook().copy(uniqueIdentifier = "second")
            shelves.enqueueBooks(ShelfBooksResult.Success(listOf(first, second)))
            val viewModel = sut(shelves)
            viewModel.screenShown()

            viewModel.removeBook("first")

            viewModel.uiState.value.books shouldBe listOf(first, second)
            viewModel.uiState.value.transientError shouldBe ShelvesError.Unreachable
        }
    }
})
