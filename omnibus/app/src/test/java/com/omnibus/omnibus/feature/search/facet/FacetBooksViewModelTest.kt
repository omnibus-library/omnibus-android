package com.omnibus.omnibus.feature.search.facet

import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.library.BooksFacet
import com.omnibus.omnibus.data.library.BooksPage
import com.omnibus.omnibus.data.library.BooksPageQuery
import com.omnibus.omnibus.data.library.FacetKind
import com.omnibus.omnibus.data.library.LibraryError
import com.omnibus.omnibus.data.library.LibraryPageResult
import com.omnibus.omnibus.data.library.LibrarySort
import com.omnibus.omnibus.data.library.SortDirection
import com.omnibus.omnibus.data.library.TestLibraryRepository
import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.feature.search.SearchFailure
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Opens the facet list for [route] on virtual time, after the answers are scripted. The ViewModel's scope is cancelled
 * afterwards, so a page still waiting does not outlive the case.
 */
private fun runCase(
    repo: TestLibraryRepository,
    route: FacetBooksRoute = FacetBooksRoute(FacetKind.Tag, randomString()),
    block: suspend TestScope.(FacetBooksViewModel) -> Unit,
) = runTest {
    val vm = FacetBooksViewModel(route, repo)
    try {
        runCurrent()
        block(vm)
    } finally {
        vm.viewModelScope.cancel()
    }
}

/** A successful page of random books with the given [ids], and the paging headers [cursor] and [total]. */
private fun booksPage(vararg ids: Long, cursor: String? = null, total: Long? = null): LibraryPageResult.Success =
    LibraryPageResult.Success(BooksPage(ids.map { randomBook().copy(id = it) }, cursor, total, hiddenCount = null))

/** The query the list sends for the tag [name]: title order, ascending. */
private fun tagQuery(name: String) =
    BooksPageQuery(LibrarySort.Title, SortDirection.Asc, facet = BooksFacet(FacetKind.Tag, name))

class FacetBooksViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(StandardTestDispatcher()) }

    afterSpec { Dispatchers.resetMain() }

    describe("opening the list") {

        it("#352 TC-19: asks for the first page once, by title, filtered to the tag") {
            val name = randomString()
            val repo = TestLibraryRepository().apply { enqueue(booksPage(1)) }

            runCase(repo, FacetBooksRoute(FacetKind.Tag, name)) {
                repo.pageRequests shouldBe listOf(TestLibraryRepository.PageRequest(tagQuery(name), cursor = null))
            }
        }

        context("when the first page has books") {
            it("#352 TC-20: shows the books with the total and the cursor") {
                val page = booksPage(1, 2, cursor = "c", total = 14)
                val repo = TestLibraryRepository().apply { enqueue(page) }

                runCase(repo) { vm ->
                    vm.uiState.value.body shouldBe
                        FacetBody.Loaded(page.page.books, total = 14, nextCursor = "c", nextPage = NextPageState.Idle)
                }
            }

            it("#352 TC-21: has no total when the server sends no count") {
                val repo = TestLibraryRepository().apply { enqueue(booksPage(1, 2, total = null)) }

                runCase(repo) { vm ->
                    vm.uiState.value.body.shouldBeTypeOf<FacetBody.Loaded>().total shouldBe null
                }
            }

            it("#352 TC-21: keeps the total as the server sent it, even when it is below the books shown") {
                val repo = TestLibraryRepository().apply { enqueue(booksPage(1, 2, 3, total = 1)) }

                runCase(repo) { vm ->
                    vm.uiState.value.body.shouldBeTypeOf<FacetBody.Loaded>().total shouldBe 1L
                }
            }
        }

        context("when the first page has no books") {
            it("#352 TC-27: shows the empty facet, whatever the cursor") {
                checkAll(Exhaustive.of<String?>(null, "c")) { cursor ->
                    val repo = TestLibraryRepository().apply { enqueue(booksPage(cursor = cursor)) }

                    runCase(repo) { vm ->
                        vm.uiState.value.body shouldBe FacetBody.Empty
                    }
                }
            }
        }

        context("when the first page fails") {
            it("#352 TC-28: shows the unreachable state when the server cannot be reached") {
                val repo = TestLibraryRepository().apply {
                    enqueue(LibraryPageResult.Failure(LibraryError.Unreachable))
                }

                runCase(repo) { vm ->
                    vm.uiState.value.body shouldBe FacetBody.Failed(SearchFailure.Unreachable)
                }
            }

            it("#352 TC-28: shows the not-working state for a server error or an unreadable answer") {
                checkAll(
                    Exhaustive.of(
                        LibraryError.Http(500, serverMessage = null),
                        LibraryError.Http(429, serverMessage = "Slow down"),
                        LibraryError.UnexpectedResponse,
                    ),
                ) { error ->
                    val repo = TestLibraryRepository().apply { enqueue(LibraryPageResult.Failure(error)) }

                    runCase(repo) { vm ->
                        vm.uiState.value.body shouldBe FacetBody.Failed(SearchFailure.NotWorking)
                    }
                }
            }

            it("#352 TC-29: shows nothing when the session ended or no server is saved") {
                checkAll(
                    Exhaustive.of<LibraryPageResult>(
                        LibraryPageResult.Failure(LibraryError.SessionExpired),
                        LibraryPageResult.NoServer,
                    ),
                ) { ending ->
                    val repo = TestLibraryRepository().apply { enqueue(ending) }

                    runCase(repo) { vm ->
                        vm.uiState.value.body shouldBe FacetBody.Ended
                    }
                }
            }
        }

        context("when the first page failed and the reader tries again") {
            it("#352 TC-30: asks for the first page again, with no cursor") {
                val repo = TestLibraryRepository().apply {
                    enqueue(LibraryPageResult.Failure(LibraryError.Unreachable))
                    enqueue(booksPage(1))
                }
                val name = randomString()

                runCase(repo, FacetBooksRoute(FacetKind.Tag, name)) { vm ->
                    vm.retry()
                    runCurrent()

                    repo.pageRequests shouldBe listOf(
                        TestLibraryRepository.PageRequest(tagQuery(name), cursor = null),
                        TestLibraryRepository.PageRequest(tagQuery(name), cursor = null),
                    )
                }
            }

            it("#352 TC-30: shows the books of the page it asked for again") {
                val second = booksPage(1, 2)
                val repo = TestLibraryRepository().apply {
                    enqueue(LibraryPageResult.Failure(LibraryError.Unreachable))
                    enqueue(second)
                }

                runCase(repo) { vm ->
                    vm.retry()
                    runCurrent()

                    vm.uiState.value.body shouldBe
                        FacetBody.Loaded(second.page.books, total = null, nextCursor = null, nextPage = NextPageState.Idle)
                }
            }
        }
    }

    describe("paging through the list") {

        context("when the first page has a cursor") {
            it("#352 TC-22: asks for the next page with that cursor and the same query") {
                val name = randomString()
                val repo = TestLibraryRepository().apply {
                    enqueue(booksPage(1, cursor = "c"))
                    enqueue(booksPage(2))
                }

                runCase(repo, FacetBooksRoute(FacetKind.Tag, name)) { vm ->
                    vm.loadMoreIfNeeded(0)
                    runCurrent()

                    repo.pageRequests[1] shouldBe TestLibraryRepository.PageRequest(tagQuery(name), cursor = "c")
                }
            }

            it("#352 TC-22: appends the books of the next page") {
                val first = booksPage(1, cursor = "c")
                val second = booksPage(2)
                val repo = TestLibraryRepository().apply {
                    enqueue(first)
                    enqueue(second)
                }

                runCase(repo) { vm ->
                    vm.loadMoreIfNeeded(0)
                    runCurrent()

                    vm.uiState.value.body shouldBe FacetBody.Loaded(
                        first.page.books + second.page.books,
                        total = null,
                        nextCursor = null,
                        nextPage = NextPageState.Idle,
                    )
                }
            }

            it("#352 TC-23: shows a book that a later page repeats only once") {
                val repo = TestLibraryRepository().apply {
                    enqueue(booksPage(1, 2, cursor = "c"))
                    enqueue(booksPage(2, 3))
                }

                runCase(repo) { vm ->
                    vm.loadMoreIfNeeded(0)
                    runCurrent()

                    vm.uiState.value.body.shouldBeTypeOf<FacetBody.Loaded>().books.map { it.id } shouldBe listOf(1L, 2L, 3L)
                }
            }
        }

        context("when the last page has no cursor") {
            it("#352 TC-24: sends no request for more books") {
                val repo = TestLibraryRepository().apply { enqueue(booksPage(1)) }

                runCase(repo) { vm ->
                    vm.loadMoreIfNeeded(0)
                    runCurrent()

                    repo.pageRequests.size shouldBe 1
                }
            }
        }

        context("when a later page is empty") {
            it("#352 TC-24: sends no request after it") {
                val repo = TestLibraryRepository().apply {
                    enqueue(booksPage(1, cursor = "c"))
                    enqueue(booksPage(cursor = "d"))
                }

                runCase(repo) { vm ->
                    vm.loadMoreIfNeeded(0)
                    runCurrent()
                    vm.loadMoreIfNeeded(0)
                    runCurrent()

                    repo.pageRequests.size shouldBe 2
                }
            }
        }

        context("when a page is already on its way") {
            it("#352 TC-25: asks for one page however many times the reader is near the end") {
                val repo = TestLibraryRepository().apply {
                    enqueue(booksPage(1, cursor = "c"))
                    enqueue(CompletableDeferred<LibraryPageResult>())
                }

                runCase(repo) { vm ->
                    vm.loadMoreIfNeeded(0)
                    vm.loadMoreIfNeeded(0)
                    runCurrent()

                    repo.pageRequests.size shouldBe 2
                }
            }
        }

        context("when the reader is not near the end") {
            it("#352 TC-26: asks for no page when the last visible row is at the threshold") {
                val repo = TestLibraryRepository().apply { enqueue(booksPage(1, cursor = "c")) }

                runCase(repo) { vm ->
                    vm.loadMoreIfNeeded(FACET_LOAD_MORE_THRESHOLD)
                    runCurrent()

                    repo.pageRequests.size shouldBe 1
                }
            }

            it("#352 TC-26: asks for the next page when the last visible row is inside the threshold") {
                val repo = TestLibraryRepository().apply {
                    enqueue(booksPage(1, cursor = "c"))
                    enqueue(booksPage(2))
                }

                runCase(repo) { vm ->
                    vm.loadMoreIfNeeded(FACET_LOAD_MORE_THRESHOLD - 1)
                    runCurrent()

                    repo.pageRequests.size shouldBe 2
                }
            }
        }

        context("when the next page failed") {
            it("#352 TC-31: keeps the books and the cursor, and marks the next page as failed") {
                val first = booksPage(1, cursor = "c", total = 5)
                val repo = TestLibraryRepository().apply {
                    enqueue(first)
                    enqueue(LibraryPageResult.Failure(LibraryError.Unreachable))
                }

                runCase(repo) { vm ->
                    vm.loadMoreIfNeeded(0)
                    runCurrent()

                    vm.uiState.value.body shouldBe
                        FacetBody.Loaded(first.page.books, total = 5, nextCursor = "c", nextPage = NextPageState.Failed)
                }
            }

            it("#352 TC-32: asks for no page while the failed page waits for Try again") {
                val repo = TestLibraryRepository().apply {
                    enqueue(booksPage(1, cursor = "c"))
                    enqueue(LibraryPageResult.Failure(LibraryError.Unreachable))
                }

                runCase(repo) { vm ->
                    vm.loadMoreIfNeeded(0)
                    runCurrent()
                    vm.loadMoreIfNeeded(0)
                    runCurrent()

                    repo.pageRequests.size shouldBe 2
                }
            }

            it("#352 TC-33: asks for the same page again with its cursor") {
                val name = randomString()
                val repo = TestLibraryRepository().apply {
                    enqueue(booksPage(1, cursor = "c"))
                    enqueue(LibraryPageResult.Failure(LibraryError.Unreachable))
                    enqueue(booksPage(2))
                }

                runCase(repo, FacetBooksRoute(FacetKind.Tag, name)) { vm ->
                    vm.loadMoreIfNeeded(0)
                    runCurrent()
                    vm.retryNextPage()
                    runCurrent()

                    repo.pageRequests.last() shouldBe TestLibraryRepository.PageRequest(tagQuery(name), cursor = "c")
                }
            }

            it("#352 TC-33: appends the books of the page that succeeds on retry, and goes idle") {
                val first = booksPage(1, cursor = "c")
                val third = booksPage(2)
                val repo = TestLibraryRepository().apply {
                    enqueue(first)
                    enqueue(LibraryPageResult.Failure(LibraryError.Unreachable))
                    enqueue(third)
                }

                runCase(repo) { vm ->
                    vm.loadMoreIfNeeded(0)
                    runCurrent()
                    vm.retryNextPage()
                    runCurrent()

                    vm.uiState.value.body shouldBe FacetBody.Loaded(
                        first.page.books + third.page.books,
                        total = null,
                        nextCursor = null,
                        nextPage = NextPageState.Idle,
                    )
                }
            }

            it("#352 TC-34: keeps the books shown and stops paging when the session ended") {
                val first = booksPage(1, cursor = "c")
                val repo = TestLibraryRepository().apply {
                    enqueue(first)
                    enqueue(LibraryPageResult.Failure(LibraryError.SessionExpired))
                }

                runCase(repo) { vm ->
                    vm.loadMoreIfNeeded(0)
                    runCurrent()

                    vm.uiState.value.body shouldBe
                        FacetBody.Loaded(first.page.books, total = null, nextCursor = null, nextPage = NextPageState.Idle)
                }
            }

            it("#352 TC-34: sends no later page when the session ended") {
                val repo = TestLibraryRepository().apply {
                    enqueue(booksPage(1, cursor = "c"))
                    enqueue(LibraryPageResult.Failure(LibraryError.SessionExpired))
                }

                runCase(repo) { vm ->
                    vm.loadMoreIfNeeded(0)
                    runCurrent()
                    vm.loadMoreIfNeeded(0)
                    runCurrent()

                    repo.pageRequests.size shouldBe 2
                }
            }
        }
    }
})
