package com.omnibus.omnibus.feature.library

import com.omnibus.omnibus.data.library.BooksPage
import com.omnibus.omnibus.data.library.BooksPageQuery
import com.omnibus.omnibus.data.library.FakeLibraryRepository
import com.omnibus.omnibus.data.library.LibraryError
import com.omnibus.omnibus.data.library.LibraryPageResult
import com.omnibus.omnibus.data.library.LibrarySort
import com.omnibus.omnibus.data.library.SortDirection
import com.omnibus.omnibus.data.library.page
import com.omnibus.omnibus.data.library.testBook
import com.omnibus.omnibus.testing.CollectingScope
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest : DescribeSpec({

    lateinit var repository: FakeLibraryRepository
    lateinit var collecting: CollectingScope

    /** Builds a VM against [repo] and keeps its state collected, as a screen would. */
    fun viewModelFor(repo: FakeLibraryRepository) = LibraryViewModel(repo).also {
        collecting.collect(it.uiState)
    }

    fun createViewModel() = viewModelFor(repository)

    fun pageOf(range: LongRange, cursor: String? = null) = page(*range.toList().toLongArray(), cursor = cursor)

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    beforeEach {
        repository = FakeLibraryRepository()
        collecting = CollectingScope()
    }

    afterEach { collecting.cancel() }

    describe("loading") {

        it("TC-56: loads the first page on creation with the newest-added sort") {
            repository.enqueue(pageOf(1L..2L, cursor = "c1"))

            val viewModel = createViewModel()

            repository.pageRequests.single() shouldBe FakeLibraryRepository.PageRequest(BooksPageQuery(), null)
            viewModel.uiState.value.books.map { it.id } shouldBe listOf(1L, 2L)
            viewModel.uiState.value.nextCursor shouldBe "c1"
        }

        it("TC-57: reports the initial load while pending and hasLoaded once the result arrives") {
            val first = CompletableDeferred<LibraryPageResult>()
            repository.enqueue(first)

            val viewModel = createViewModel()

            viewModel.uiState.value.isInitialLoading shouldBe true
            viewModel.uiState.value.hasLoaded shouldBe false

            first.complete(page(1L))

            viewModel.uiState.value.isInitialLoading shouldBe false
            viewModel.uiState.value.hasLoaded shouldBe true
        }
    }

    describe("screen") {

        it("TC-58: is Loading while the first page is in flight with no books") {
            repository.enqueue(CompletableDeferred())

            createViewModel().uiState.value.screen shouldBe LibraryScreenState.Loading
        }

        it("TC-59: is Error with the typed failure when the first page fails") {
            repository.enqueue(LibraryPageResult.Failure(LibraryError.Unreachable))

            createViewModel().uiState.value.screen shouldBe
                LibraryScreenState.Error(LibraryUIError.Failed(LibraryError.Unreachable))
        }

        it("TC-60: retry re-runs the first-page request and goes Error, Loading, Content") {
            repository.enqueue(LibraryPageResult.Failure(LibraryError.Unreachable))
            val viewModel = createViewModel()
            val second = CompletableDeferred<LibraryPageResult>()
            repository.enqueue(second)

            viewModel.retry()

            viewModel.uiState.value.screen shouldBe LibraryScreenState.Loading
            repository.pageRequests.last() shouldBe FakeLibraryRepository.PageRequest(BooksPageQuery(), null)

            second.complete(page(1L, 2L))

            viewModel.uiState.value.screen shouldBe LibraryScreenState.Content
        }

        it("TC-61: is Error with the no-server error when no server is saved") {
            repository.enqueue(LibraryPageResult.NoServer)

            createViewModel().uiState.value.screen shouldBe
                LibraryScreenState.Error(LibraryUIError.NoServer)
        }

        it("TC-62: is Empty for a loaded page with no books") {
            repository.enqueue(page())

            val viewModel = createViewModel()

            viewModel.uiState.value.isInitialLoading shouldBe false
            viewModel.uiState.value.screen shouldBe LibraryScreenState.Empty
        }

        it("TC-63: keeps Content when a refresh or load-more fails with books on screen") {
            repository.enqueue(pageOf(1L..60L, cursor = "c1"))
            val viewModel = createViewModel()
            repository.enqueue(LibraryPageResult.Failure(LibraryError.Unreachable))

            viewModel.loadMoreIfNeeded(59)

            viewModel.uiState.value.screen shouldBe LibraryScreenState.Content

            repository.enqueue(LibraryPageResult.Failure(LibraryError.Unreachable))
            viewModel.refresh()

            viewModel.uiState.value.screen shouldBe LibraryScreenState.Content
        }

        it("TC-63: applies Loading, then Error, then Empty, then Content by first match") {
            LibraryUIState(isInitialLoading = true, error = LibraryUIError.NoServer).screen shouldBe LibraryScreenState.Loading
            LibraryUIState(error = LibraryUIError.NoServer).screen shouldBe LibraryScreenState.Error(LibraryUIError.NoServer)
            LibraryUIState().screen shouldBe LibraryScreenState.Empty
            LibraryUIState(books = listOf(testBook(1)), error = LibraryUIError.NoServer).screen shouldBe LibraryScreenState.Content
            LibraryUIState(isInitialLoading = true, books = listOf(testBook(1))).screen shouldBe
                LibraryScreenState.Content
        }
    }

    describe("pagination") {

        it("TC-77: loads the next page with the cursor, appends it and advances the cursor") {
            repository.enqueue(pageOf(1L..60L, cursor = "c1"))
            val viewModel = createViewModel()
            repository.enqueue(pageOf(61L..90L, cursor = "c2"))

            viewModel.loadMoreIfNeeded(59)

            repository.pageRequests.size shouldBe 2
            repository.pageRequests[1] shouldBe FakeLibraryRepository.PageRequest(BooksPageQuery(), "c1")
            viewModel.uiState.value.books.map { it.id } shouldBe (1L..90L).toList()
            viewModel.uiState.value.nextCursor shouldBe "c2"
        }

        it("TC-78: requests the next page at index 48 of 60 books but not at 47") {
            repository.enqueue(pageOf(1L..60L, cursor = "c1"))
            val viewModel = createViewModel()

            viewModel.loadMoreIfNeeded(47)
            repository.pageRequests.size shouldBe 1

            viewModel.loadMoreIfNeeded(48)
            repository.pageRequests.size shouldBe 2

            val fresh = FakeLibraryRepository().apply { enqueue(pageOf(1L..60L, cursor = "c1")) }
            viewModelFor(fresh).loadMoreIfNeeded(59)
            fresh.pageRequests.size shouldBe 2
        }

        it("TC-79: makes no request when there is no next cursor") {
            repository.enqueue(pageOf(1L..2L))
            val viewModel = createViewModel()

            viewModel.loadMoreIfNeeded(1)

            repository.pageRequests.size shouldBe 1
        }

        it("TC-80: makes no further request while initial, refresh or load-more is in flight") {
            val initial = FakeLibraryRepository()
            initial.enqueue(CompletableDeferred())
            viewModelFor(initial).loadMoreIfNeeded(100)
            initial.pageRequests.size shouldBe 1

            val refreshing = FakeLibraryRepository()
            refreshing.enqueue(pageOf(1L..60L, cursor = "c1"))
            val refreshingViewModel = viewModelFor(refreshing)
            refreshing.enqueue(CompletableDeferred())
            refreshingViewModel.refresh()
            refreshingViewModel.loadMoreIfNeeded(59)
            refreshing.pageRequests.size shouldBe 2

            val loadingMore = FakeLibraryRepository()
            loadingMore.enqueue(pageOf(1L..60L, cursor = "c1"))
            val loadingMoreViewModel = viewModelFor(loadingMore)
            loadingMore.enqueue(CompletableDeferred())
            loadingMoreViewModel.loadMoreIfNeeded(59)
            loadingMoreViewModel.loadMoreIfNeeded(59)
            loadingMore.pageRequests.size shouldBe 2
        }

        it("TC-81: appends de-duplicated by id, keeping the first occurrence in order") {
            repository.enqueue(pageOf(1L..60L, cursor = "c1"))
            val viewModel = createViewModel()
            repository.enqueue(
                LibraryPageResult.Success(
                    BooksPage(
                        books = listOf(testBook(59, title = "Changed"), testBook(61)),
                        nextCursor = "c2",
                        total = null,
                        hiddenCount = null,
                    ),
                ),
            )

            viewModel.loadMoreIfNeeded(59)

            val books = viewModel.uiState.value.books
            books.map { it.id } shouldBe (1L..61L).toList()
            books.first { it.id == 59L }.title shouldBe "Book 59"
        }

        it("TC-82: ends pagination on a null cursor and on an empty page that carries a cursor") {
            repository.enqueue(pageOf(1L..60L, cursor = "c1"))
            val lastPage = createViewModel()
            repository.enqueue(page(61L, 62L))
            lastPage.loadMoreIfNeeded(59)
            lastPage.loadMoreIfNeeded(61)
            repository.pageRequests.size shouldBe 2

            val emptyPage = FakeLibraryRepository()
            emptyPage.enqueue(pageOf(1L..60L, cursor = "c1"))
            val emptyPageViewModel = viewModelFor(emptyPage)
            emptyPage.enqueue(LibraryPageResult.Success(BooksPage(emptyList(), "c2", null, null)))
            emptyPageViewModel.loadMoreIfNeeded(59)
            emptyPageViewModel.loadMoreIfNeeded(59)
            emptyPage.pageRequests.size shouldBe 2
        }
    }

    describe("refresh") {

        it("TC-85: replaces the list wholesale and resets the cursor") {
            repository.enqueue(pageOf(1L..60L, cursor = "c1"))
            val viewModel = createViewModel()
            repository.enqueue(pageOf(61L..62L, cursor = "c2"))
            viewModel.loadMoreIfNeeded(59)
            repository.enqueue(pageOf(100L..101L, cursor = "new"))

            viewModel.refresh()

            viewModel.uiState.value.books.map { it.id } shouldBe listOf(100L, 101L)
            viewModel.uiState.value.nextCursor shouldBe "new"
            repository.pageRequests.last().cursor shouldBe null
        }

        it("TC-86: stays refreshing through a cached emission and clears only when the flow completes") {
            repository.enqueue(page(1L, 2L))
            val viewModel = createViewModel()
            val network = CompletableDeferred<LibraryPageResult>()
            repository.enqueueFlow {
                emit(page(7L))
                emit(network.await())
            }

            viewModel.refresh()

            viewModel.uiState.value.books.map { it.id } shouldBe listOf(7L)
            viewModel.uiState.value.isRefreshing shouldBe true

            network.complete(page(8L, 9L))

            viewModel.uiState.value.books.map { it.id } shouldBe listOf(8L, 9L)
            viewModel.uiState.value.isRefreshing shouldBe false
        }

        it("TC-87: finishes the refresh after the state collector is cancelled") {
            repository.enqueue(page(1L))
            val viewModel = createViewModel()
            val network = CompletableDeferred<LibraryPageResult>()
            repository.enqueue(network)
            viewModel.refresh()

            collecting.cancel()
            network.complete(page(5L))

            viewModel.uiState.value.books.map { it.id } shouldBe listOf(5L)
            viewModel.uiState.value.isRefreshing shouldBe false
        }
    }

    describe("stale reloads") {

        it("TC-89: drops an older reload that completes after a newer one") {
            repository.enqueue(LibraryPageResult.Failure(LibraryError.Unreachable))
            val viewModel = createViewModel()
            val older = CompletableDeferred<LibraryPageResult>()
            repository.enqueue(older)
            viewModel.retry()
            val newer = CompletableDeferred<LibraryPageResult>()
            repository.enqueue(newer)
            viewModel.refresh()

            newer.complete(page(2L))
            older.complete(page(1L))

            viewModel.uiState.value.books.map { it.id } shouldBe listOf(2L)
            viewModel.uiState.value.isInitialLoading shouldBe false
            viewModel.uiState.value.isRefreshing shouldBe false
        }

        it("TC-90: a refresh cancels an in-flight load-more so its late result is not appended") {
            repository.enqueue(pageOf(1L..60L, cursor = "c1"))
            val viewModel = createViewModel()
            val loadMore = CompletableDeferred<LibraryPageResult>()
            repository.enqueue(loadMore)
            viewModel.loadMoreIfNeeded(59)
            repository.enqueue(page(100L, cursor = "n"))
            viewModel.refresh()

            loadMore.complete(page(61L, cursor = "c2"))

            viewModel.uiState.value.books.map { it.id } shouldBe listOf(100L)
            viewModel.uiState.value.nextCursor shouldBe "n"
            viewModel.uiState.value.isLoadingMore shouldBe false
        }

        it("TC-91: ignores a superseded reload's failure after a newer reload succeeded") {
            repository.enqueue(page(1L))
            val viewModel = createViewModel()
            val older = CompletableDeferred<LibraryPageResult>()
            repository.enqueue(older)
            viewModel.refresh()
            repository.enqueue(page(2L))
            viewModel.refresh()

            older.complete(LibraryPageResult.Failure(LibraryError.Http(500, "old failure")))

            viewModel.uiState.value.transientError shouldBe null
            viewModel.uiState.value.error shouldBe null
            viewModel.uiState.value.isRefreshing shouldBe false
            viewModel.uiState.value.books.map { it.id } shouldBe listOf(2L)
        }
    }

    describe("state survival") {

        it("TC-92: a new collector sees the same books and cursor without another request") {
            repository.enqueue(pageOf(1L..2L, cursor = "c1"))
            val viewModel = createViewModel()
            collecting.cancel()

            val second = CollectingScope()
            second.collect(viewModel.uiState)

            repository.pageRequests.size shouldBe 1
            viewModel.uiState.value.books.map { it.id } shouldBe listOf(1L, 2L)
            viewModel.uiState.value.nextCursor shouldBe "c1"
            second.cancel()
        }
    }

    describe("transient errors") {

        it("TC-95: a failed load-more keeps the books and cursor and sets the message") {
            repository.enqueue(pageOf(1L..60L, cursor = "c1"))
            val viewModel = createViewModel()
            repository.enqueue(LibraryPageResult.Failure(LibraryError.Unreachable))

            viewModel.loadMoreIfNeeded(59)

            val state = viewModel.uiState.value
            state.books.size shouldBe 60
            state.nextCursor shouldBe "c1"
            state.transientError shouldBe LibraryUIError.Failed(LibraryError.Unreachable)
            state.isLoadingMore shouldBe false
            state.error shouldBe null
            state.screen shouldBe LibraryScreenState.Content
        }

        it("TC-96: after a failed load-more, the next scroll retries with the same cursor") {
            repository.enqueue(pageOf(1L..60L, cursor = "c1"))
            val viewModel = createViewModel()
            repository.enqueue(LibraryPageResult.Failure(LibraryError.Unreachable))
            viewModel.loadMoreIfNeeded(59)
            repository.enqueue(pageOf(61L..62L, cursor = "c2"))

            viewModel.loadMoreIfNeeded(59)

            repository.pageRequests.size shouldBe 3
            repository.pageRequests.last().cursor shouldBe "c1"
            viewModel.uiState.value.books.size shouldBe 62
            viewModel.uiState.value.nextCursor shouldBe "c2"
        }

        it("TC-97: a failed refresh keeps the old books and cursor and sets the message") {
            repository.enqueue(pageOf(1L..60L, cursor = "c1"))
            val viewModel = createViewModel()
            repository.enqueue(LibraryPageResult.Failure(LibraryError.Unreachable))

            viewModel.refresh()

            val state = viewModel.uiState.value
            state.books.size shouldBe 60
            state.nextCursor shouldBe "c1"
            state.transientError shouldBe LibraryUIError.Failed(LibraryError.Unreachable)
            state.isRefreshing shouldBe false
            state.screen shouldBe LibraryScreenState.Content
        }

        it("TC-98: transientErrorShown clears the message, and a later failure sets it again") {
            repository.enqueue(pageOf(1L..60L, cursor = "c1"))
            val viewModel = createViewModel()
            repository.enqueue(LibraryPageResult.Failure(LibraryError.UnexpectedResponse))
            viewModel.loadMoreIfNeeded(59)
            viewModel.uiState.value.transientError shouldBe LibraryUIError.Failed(LibraryError.UnexpectedResponse)

            viewModel.transientErrorShown()
            viewModel.transientErrorShown()
            viewModel.uiState.value.transientError shouldBe null

            repository.enqueue(LibraryPageResult.Failure(LibraryError.SessionExpired))
            viewModel.loadMoreIfNeeded(59)
            viewModel.uiState.value.transientError shouldBe LibraryUIError.Failed(LibraryError.SessionExpired)
        }

        it("TC-99: a first-page failure with no books sets error but no transient message") {
            repository.enqueue(LibraryPageResult.Failure(LibraryError.Unreachable))

            val viewModel = createViewModel()

            viewModel.uiState.value.screen shouldBe
                LibraryScreenState.Error(LibraryUIError.Failed(LibraryError.Unreachable))
            viewModel.uiState.value.transientError shouldBe null
        }
    }

    describe("FakeLibraryRepository") {

        it("TC-101: records each request's sort, direction and cursor and replays scripted results in order") {
            val fake = FakeLibraryRepository()
            fake.enqueue(page(1L, cursor = "c1"))

            val first = fake.getBooksPageFlow(BooksPageQuery(sort = LibrarySort.Title, dir = SortDirection.Asc)).toList()
            val second = fake.getBooksPageFlow(cursor = "c1").toList()

            first shouldBe listOf<LibraryPageResult>(page(1L, cursor = "c1"))
            second shouldBe listOf<LibraryPageResult>(fake.defaultResult)
            fake.pageRequests shouldBe listOf(
                FakeLibraryRepository.PageRequest(BooksPageQuery(LibrarySort.Title, SortDirection.Asc), null),
                FakeLibraryRepository.PageRequest(BooksPageQuery(), "c1"),
            )
        }

        it("TC-101: replays a cached emission before a deferred network result") {
            val fake = FakeLibraryRepository()
            val network = CompletableDeferred<LibraryPageResult>()
            fake.enqueueFlow {
                emit(page(1L))
                emit(network.await())
            }
            val collected = mutableListOf<LibraryPageResult>()

            network.complete(page(2L))
            fake.getBooksPageFlow().toList(collected)

            collected shouldBe listOf<LibraryPageResult>(page(1L), page(2L))
        }
    }
})
