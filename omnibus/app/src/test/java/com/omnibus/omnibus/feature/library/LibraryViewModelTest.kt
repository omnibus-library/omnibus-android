package com.omnibus.omnibus.feature.library

import com.omnibus.omnibus.data.library.BookAvailability
import com.omnibus.omnibus.data.library.BooksPage
import com.omnibus.omnibus.data.library.BooksPageQuery
import com.omnibus.omnibus.data.library.FakeBookAvailability
import com.omnibus.omnibus.data.library.FakeLibraryRepository
import com.omnibus.omnibus.data.library.LibraryCategory
import com.omnibus.omnibus.data.library.LibraryError
import com.omnibus.omnibus.data.library.LibraryPageResult
import com.omnibus.omnibus.data.library.LibrarySort
import com.omnibus.omnibus.data.library.LibrarySortPreference
import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.data.library.RecentProgressResult
import com.omnibus.omnibus.data.library.ResumePoint
import com.omnibus.omnibus.data.library.SortDirection
import com.omnibus.omnibus.data.library.local.FakeLibrarySortPreferenceStore
import com.omnibus.omnibus.data.library.page
import com.omnibus.omnibus.data.library.testBook
import com.omnibus.omnibus.data.library.testResumePoint
import com.omnibus.omnibus.data.network.ConnectivityMonitor
import com.omnibus.omnibus.data.network.FakeConnectivityMonitor
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.testing.CollectingScope
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** The fixed "now" the Continue cards' relative times are measured from, in milliseconds. */
private const val NOW = 1_780_000_000_000L

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest : DescribeSpec({

    lateinit var repository: FakeLibraryRepository
    lateinit var sortStore: FakeLibrarySortPreferenceStore
    lateinit var collecting: CollectingScope

    /** Builds a VM against [repo] and [store] and keeps its state collected, as a screen would. */
    fun viewModelFor(
        repo: FakeLibraryRepository,
        store: FakeLibrarySortPreferenceStore = FakeLibrarySortPreferenceStore(),
        monitor: ConnectivityMonitor = FakeConnectivityMonitor(),
        availability: BookAvailability = FakeBookAvailability(),
        time: TimeSource = TimeSource { NOW },
    ) = LibraryViewModel(repo, store, monitor, availability, time).also {
        collecting.collect(it.uiState)
    }

    fun createViewModel() = viewModelFor(repository, sortStore)

    fun pageOf(range: LongRange, cursor: String? = null) = page(*range.toList().toLongArray(), cursor = cursor)

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    beforeEach {
        repository = FakeLibraryRepository()
        sortStore = FakeLibrarySortPreferenceStore()
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

    describe("filtered predicate") {

        it("TC-103: each non-default category, sort or direction on its own is filtered") {
            val filtered = listOf(
                LibraryUIState(category = LibraryCategory.Ebooks),
                LibraryUIState(category = LibraryCategory.Audiobooks),
                LibraryUIState(direction = SortDirection.Asc),
            ) + LibrarySort.entries.filter { it != LibrarySort.NewestAdded }.map { LibraryUIState(sort = it) }

            filtered.forEach { it.isFiltered shouldBe true }
        }
    }

    describe("sort restore") {

        it("TC-121: a stored sort and direction are on the first page request") {
            sortStore = FakeLibrarySortPreferenceStore(LibrarySortPreference(LibrarySort.Title, SortDirection.Asc))
            repository.enqueue(page(1L))

            createViewModel()

            repository.pageRequests.single() shouldBe
                FakeLibraryRepository.PageRequest(BooksPageQuery(sort = LibrarySort.Title, dir = SortDirection.Asc), null)
        }

        it("TC-122: no page request is sent until the stored preference arrives") {
            sortStore = FakeLibrarySortPreferenceStore(LibrarySortPreference(LibrarySort.Author, SortDirection.Asc))
            val release = CompletableDeferred<Unit>()
            sortStore.holdFirstEmission = release
            repository.enqueue(page(1L))

            createViewModel()
            repository.pageRequests.size shouldBe 0

            release.complete(Unit)

            repository.pageRequests.size shouldBe 1
            repository.pageRequests.single() shouldBe
                FakeLibraryRepository.PageRequest(BooksPageQuery(sort = LibrarySort.Author, dir = SortDirection.Asc), null)
        }

        it("TC-123: the defaults still load the first page when the preference read gives defaults") {
            sortStore = FakeLibrarySortPreferenceStore(LibrarySortPreference())
            repository.enqueue(pageOf(1L..2L))

            val viewModel = createViewModel()

            repository.pageRequests.single() shouldBe FakeLibraryRepository.PageRequest(BooksPageQuery(), null)
            viewModel.uiState.value.screen shouldBe LibraryScreenState.Content
        }

        it("TC-124: the state is Loading while the stored preference is pending") {
            val release = CompletableDeferred<Unit>()
            sortStore.holdFirstEmission = release
            repository.enqueue(page(1L))

            val viewModel = createViewModel()

            viewModel.uiState.value.isInitialLoading shouldBe true
            viewModel.uiState.value.screen shouldBe LibraryScreenState.Loading

            release.complete(Unit)

            viewModel.uiState.value.screen shouldBe LibraryScreenState.Content
        }

        it("TC-125: a new VM starts on All with an empty formats query, whatever was stored") {
            sortStore = FakeLibrarySortPreferenceStore(LibrarySortPreference(LibrarySort.Title, SortDirection.Asc))
            repository.enqueue(page(1L))

            val viewModel = createViewModel()

            viewModel.uiState.value.category shouldBe LibraryCategory.All
            repository.pageRequests.single().query.formats.shouldBeEmpty()
        }
    }

    describe("query changes") {

        it("TC-126: selecting a category never saves the preference") {
            repository.enqueue(page(1L))
            val viewModel = createViewModel()

            viewModel.selectCategory(LibraryCategory.Ebooks)

            sortStore.saves.shouldBeEmpty()
        }

        it("TC-127: a category is not kept across VMs on the same store") {
            val first = createViewModel()
            first.selectCategory(LibraryCategory.Audiobooks)

            val second = createViewModel()

            first.uiState.value.category shouldBe LibraryCategory.Audiobooks
            second.uiState.value.category shouldBe LibraryCategory.All
        }

        it("TC-128: selecting a category reloads from the first page with its formats") {
            repository.enqueue(pageOf(1L..60L, cursor = "c1"))
            val viewModel = createViewModel()

            viewModel.selectCategory(LibraryCategory.Ebooks)

            repository.pageRequests.last() shouldBe FakeLibraryRepository.PageRequest(
                BooksPageQuery(formats = LibraryCategory.Ebooks.formats),
                null,
            )
            viewModel.uiState.value.category shouldBe LibraryCategory.Ebooks
        }

        it("TC-129: selecting a sort keeps the current direction and category formats") {
            val viewModel = createViewModel()
            viewModel.selectCategory(LibraryCategory.Audiobooks)

            viewModel.selectSort(LibrarySort.Title)

            repository.pageRequests.last() shouldBe FakeLibraryRepository.PageRequest(
                BooksPageQuery(sort = LibrarySort.Title, dir = SortDirection.Desc, formats = LibraryCategory.Audiobooks.formats),
                null,
            )
        }

        it("TC-130: toggling the direction reloads with the flipped direction and flips back") {
            val viewModel = createViewModel()

            viewModel.toggleDirection()
            repository.pageRequests.last() shouldBe FakeLibraryRepository.PageRequest(BooksPageQuery(dir = SortDirection.Asc), null)

            viewModel.toggleDirection()
            repository.pageRequests.last() shouldBe FakeLibraryRepository.PageRequest(BooksPageQuery(dir = SortDirection.Desc), null)
        }

        it("TC-131: choosing the value already active does nothing") {
            repository.enqueue(page(1L))
            val viewModel = createViewModel()
            val requestsBefore = repository.pageRequests.size
            val stateBefore = viewModel.uiState.value

            viewModel.selectCategory(LibraryCategory.All)
            viewModel.selectSort(LibrarySort.NewestAdded)

            repository.pageRequests.size shouldBe requestsBefore
            sortStore.saves.shouldBeEmpty()
            viewModel.uiState.value shouldBe stateBefore
        }

        it("TC-132: a sort or direction change saves the sort and direction") {
            val viewModel = createViewModel()

            viewModel.selectSort(LibrarySort.Series)
            viewModel.toggleDirection()

            sortStore.saves shouldBe listOf(
                LibrarySortPreference(LibrarySort.Series, SortDirection.Desc),
                LibrarySortPreference(LibrarySort.Series, SortDirection.Asc),
            )
        }

        it("TC-133: load-more after a sort and category change uses the current query") {
            repository.enqueue(pageOf(1L..60L, cursor = "c1"))
            val viewModel = createViewModel()
            repository.enqueue(pageOf(1L..60L, cursor = "c2"))
            viewModel.selectSort(LibrarySort.Author)
            repository.enqueue(pageOf(1L..60L, cursor = "c3"))
            viewModel.selectCategory(LibraryCategory.Audiobooks)
            repository.enqueue(pageOf(61L..90L, cursor = "c4"))

            viewModel.loadMoreIfNeeded(59)

            repository.pageRequests.last() shouldBe FakeLibraryRepository.PageRequest(
                BooksPageQuery(sort = LibrarySort.Author, dir = SortDirection.Desc, formats = LibraryCategory.Audiobooks.formats),
                "c3",
            )
        }

        it("TC-134: load-more on the default state requests the default query with the cursor") {
            repository.enqueue(pageOf(1L..60L, cursor = "c1"))
            val viewModel = createViewModel()
            repository.enqueue(pageOf(61L..62L))

            viewModel.loadMoreIfNeeded(59)

            repository.pageRequests.last() shouldBe FakeLibraryRepository.PageRequest(BooksPageQuery(), "c1")
        }

        it("TC-135: a late first result from a superseded reload is dropped") {
            repository.enqueue(page(1L))
            val viewModel = createViewModel()
            val first = CompletableDeferred<LibraryPageResult>()
            repository.enqueue(first)
            viewModel.selectSort(LibrarySort.Title)
            val second = CompletableDeferred<LibraryPageResult>()
            repository.enqueue(second)
            viewModel.selectCategory(LibraryCategory.Ebooks)

            second.complete(page(7L))
            first.complete(page(5L))

            viewModel.uiState.value.books.map { it.id } shouldBe listOf(7L)
            viewModel.uiState.value.nextCursor shouldBe null
        }

        it("TC-136: a late load-more result from before a query change is discarded and no load-more runs during the reload") {
            repository.enqueue(pageOf(1L..60L, cursor = "c1"))
            val viewModel = createViewModel()
            val loadMore = CompletableDeferred<LibraryPageResult>()
            repository.enqueue(loadMore)
            viewModel.loadMoreIfNeeded(59)
            val reload = CompletableDeferred<LibraryPageResult>()
            repository.enqueue(reload)
            viewModel.selectSort(LibrarySort.Title)

            viewModel.loadMoreIfNeeded(59)
            repository.pageRequests.size shouldBe 3

            loadMore.complete(page(61L, cursor = "c2"))
            viewModel.uiState.value.books.map { it.id } shouldBe (1L..60L).toList()

            reload.complete(page(100L))
            viewModel.uiState.value.books.map { it.id } shouldBe listOf(100L)
            viewModel.uiState.value.isLoadingMore shouldBe false
        }

        it("TC-137: a query change never sends the old cursor and clears it when the reload starts") {
            repository.enqueue(pageOf(1L..60L, cursor = "c1"))
            val viewModel = createViewModel()
            repository.enqueue(CompletableDeferred())

            viewModel.selectSort(LibrarySort.Title)

            viewModel.uiState.value.nextCursor shouldBe null
            repository.pageRequests.last().cursor shouldBe null
            val requestsBefore = repository.pageRequests.size
            viewModel.loadMoreIfNeeded(59)
            repository.pageRequests.size shouldBe requestsBefore
        }
    }

    describe("scroll to top") {

        it("TC-138: a query change keeps the old books on screen while the reload is pending") {
            repository.enqueue(pageOf(1L..2L))
            val viewModel = createViewModel()
            repository.enqueue(CompletableDeferred())

            viewModel.selectSort(LibrarySort.Title)

            viewModel.uiState.value.books.map { it.id } shouldBe listOf(1L, 2L)
            viewModel.uiState.value.screen shouldNotBe LibraryScreenState.Loading
        }

        it("TC-139: the first page of a query change replaces the books and raises scrollToTop") {
            repository.enqueue(page(1L, 2L))
            val viewModel = createViewModel()
            val pending = CompletableDeferred<LibraryPageResult>()
            repository.enqueue(pending)

            viewModel.selectCategory(LibraryCategory.Ebooks)
            pending.complete(page(7L))

            viewModel.uiState.value.books.map { it.id } shouldBe listOf(7L)
            viewModel.uiState.value.scrollToTop shouldBe true
        }

        it("TC-140: scrolledToTop clears the flag and later pages of the same reload do not set it again") {
            repository.enqueue(page(1L, 2L))
            val viewModel = createViewModel()
            val later = CompletableDeferred<LibraryPageResult>()
            repository.enqueueFlow {
                emit(page(7L))
                emit(later.await())
            }

            viewModel.selectCategory(LibraryCategory.Ebooks)
            viewModel.uiState.value.scrollToTop shouldBe true
            viewModel.scrolledToTop()
            viewModel.uiState.value.scrollToTop shouldBe false

            later.complete(page(8L))

            viewModel.uiState.value.books.map { it.id } shouldBe listOf(8L)
            viewModel.uiState.value.scrollToTop shouldBe false
        }

        it("TC-141: initial load, pull to refresh and load-more never raise scrollToTop") {
            repository.enqueue(pageOf(1L..60L, cursor = "c1"))
            val viewModel = createViewModel()
            viewModel.uiState.value.scrollToTop shouldBe false

            repository.enqueue(pageOf(1L..60L, cursor = "c2"))
            viewModel.refresh()
            viewModel.uiState.value.scrollToTop shouldBe false

            repository.enqueue(pageOf(61L..62L, cursor = "c3"))
            viewModel.loadMoreIfNeeded(59)
            viewModel.uiState.value.scrollToTop shouldBe false
        }

        it("TC-142: a failed query-change reload keeps the books, sets the transient error and does not scroll") {
            repository.enqueue(page(1L, 2L))
            val viewModel = createViewModel()
            repository.enqueue(LibraryPageResult.Failure(LibraryError.Unreachable))

            viewModel.selectSort(LibrarySort.Title)

            val state = viewModel.uiState.value
            state.books.map { it.id } shouldBe listOf(1L, 2L)
            state.scrollToTop shouldBe false
            state.transientError shouldBe LibraryUIError.Failed(LibraryError.Unreachable)
        }

        it("TC-143: when a query change supersedes another, only the surviving reload raises the flag, once") {
            repository.enqueue(page(1L, 2L))
            val viewModel = createViewModel()
            val superseded = CompletableDeferred<LibraryPageResult>()
            repository.enqueue(superseded)
            viewModel.selectSort(LibrarySort.Title)
            val surviving = CompletableDeferred<LibraryPageResult>()
            repository.enqueue(surviving)
            viewModel.selectCategory(LibraryCategory.Ebooks)

            superseded.complete(page(5L))
            viewModel.uiState.value.scrollToTop shouldBe false

            surviving.complete(page(7L))
            viewModel.uiState.value.scrollToTop shouldBe true

            viewModel.scrolledToTop()
            viewModel.uiState.value.scrollToTop shouldBe false
        }
    }

    describe("filtered empty") {

        it("TC-160: a filtered result with no books is Content, and only the unfiltered empty result is Empty") {
            LibraryUIState(hasLoaded = true, category = LibraryCategory.Ebooks).screen shouldBe LibraryScreenState.Content
            LibraryUIState(hasLoaded = true, sort = LibrarySort.Title).screen shouldBe LibraryScreenState.Content
            LibraryUIState(hasLoaded = true).screen shouldBe LibraryScreenState.Empty
            LibraryUIState(isInitialLoading = true, category = LibraryCategory.Ebooks).screen shouldBe
                LibraryScreenState.Loading
            LibraryUIState(
                error = LibraryUIError.Failed(LibraryError.Unreachable),
                category = LibraryCategory.Ebooks,
                hasLoaded = true,
            ).screen shouldBe LibraryScreenState.Error(LibraryUIError.Failed(LibraryError.Unreachable))
        }

        it("TC-160: changing to a filter that matches nothing keeps the grid state as Content") {
            val viewModel = createViewModel()
            viewModel.uiState.value.screen shouldBe LibraryScreenState.Empty

            viewModel.selectCategory(LibraryCategory.Audiobooks)

            viewModel.uiState.value.category shouldBe LibraryCategory.Audiobooks
            viewModel.uiState.value.screen shouldBe LibraryScreenState.Content
        }
    }

    describe("#21 connectivity") {

        it("#21 TC-3: isOffline follows the monitor from the first state") {
            val monitor = FakeConnectivityMonitor(online = false)
            val viewModel = viewModelFor(repository, sortStore, monitor)

            viewModel.uiState.value.isOffline shouldBe true

            monitor.isOnline.value = true
            viewModel.uiState.value.isOffline shouldBe false

            monitor.isOnline.value = false
            viewModel.uiState.value.isOffline shouldBe true
        }
    }
    describe("#22 continue cards") {

        /** A reading record for book [id], uuid `b<id>`. */
        fun reading(id: Long, formats: List<String> = listOf("epub")) =
            testResumePoint(book = testBook(id, uniqueIdentifier = "b$id", formats = formats), progressPercent = 10.0)

        /** A listening record for book [id], uuid `b<id>`. */
        fun listening(id: Long, formats: List<String>, fileId: Long? = null) = testResumePoint(
            book = testBook(id, uniqueIdentifier = "b$id", formats = formats),
            format = ProgressFormat.Listening,
            audioPositionSeconds = 600.0,
            totalDurationSeconds = 3600.0,
            bookFileId = fileId,
        )

        fun success(vararg points: ResumePoint) = RecentProgressResult.Success(points.toList())

        /** A VM whose first load of the Continue cards has completed with [points]. */
        fun loadedWith(vararg points: ResumePoint): LibraryViewModel {
            repository.enqueueRecent(success(*points))
            return createViewModel().also { it.libraryShown() }
        }

        val both = listOf("epub", "m4b")

        it("#22 TC-18: keeps the first five cards in server order, with distinct ids for one book's two media") {
            repository.enqueueRecent(
                success(
                    reading(1, both),
                    listening(1, both),
                    reading(2),
                    reading(3),
                    reading(4),
                    reading(5),
                    reading(6),
                ),
            )
            val viewModel = createViewModel()

            viewModel.libraryShown()

            val state = viewModel.uiState.value
            state.continueCards.map { it.id } shouldBe listOf("b1:epub", "b1:audio", "b2:epub", "b3:epub", "b4:epub")
            state.continueSelectedId shouldBe "b1:epub"
        }

        it("#22 TC-19: an empty list clears the cards and the selection") {
            val viewModel = loadedWith(reading(1), reading(2))
            viewModel.uiState.value.continueSelectedId shouldBe "b1:epub"

            repository.enqueueRecent(success())
            viewModel.libraryShown()

            viewModel.uiState.value.continueCards shouldBe emptyList()
            viewModel.uiState.value.continueSelectedId shouldBe null
        }

        val failures = listOf(RecentProgressResult.Failure(LibraryError.Unreachable), RecentProgressResult.NoServer)
        failures.forEach { failure ->
            it("#22 TC-20: a failed load ($failure) keeps the cards it had and raises no error") {
                val viewModel = createViewModel()
                repository.enqueueRecent(failure)
                viewModel.libraryShown()

                viewModel.uiState.value.continueCards shouldBe emptyList()
                viewModel.uiState.value.error shouldBe null
                viewModel.uiState.value.transientError shouldBe null

                repository.enqueueRecent(success(reading(1), reading(2)))
                viewModel.libraryShown()
                val before = viewModel.uiState.value

                repository.enqueueRecent(failure)
                viewModel.libraryShown()

                viewModel.uiState.value.continueCards shouldBe before.continueCards
                viewModel.uiState.value.continueSelectedId shouldBe before.continueSelectedId
                viewModel.uiState.value.error shouldBe null
                viewModel.uiState.value.transientError shouldBe null
            }
        }

        it("#22 TC-21: showing the Library again reloads the cards and applies the second result") {
            val viewModel = loadedWith(reading(1))

            repository.enqueueRecent(success(reading(2)))
            viewModel.libraryShown()

            repository.recentProgressRequests shouldBe 2
            viewModel.uiState.value.continueCards.map { it.id } shouldBe listOf("b2:epub")
        }

        it("#22 TC-21: refresh reloads the books and the cards, and each load is independent of the other") {
            repository.enqueue(page(1L, 2L))
            val viewModel = createViewModel()
            viewModel.libraryShown()
            val booksBefore = repository.pageRequests.size
            val cardsBefore = repository.recentProgressRequests

            repository.enqueue(page(1L, 2L))
            repository.enqueueRecent(success(reading(3)))
            viewModel.refresh()

            repository.pageRequests.size shouldBe booksBefore + 1
            repository.recentProgressRequests shouldBe cardsBefore + 1
            viewModel.uiState.value.continueCards.map { it.id } shouldBe listOf("b3:epub")

            repository.enqueue(LibraryPageResult.Failure(LibraryError.Unreachable))
            repository.enqueueRecent(success(reading(3)))
            viewModel.refresh()
            viewModel.uiState.value.continueCards.map { it.id } shouldBe listOf("b3:epub")

            repository.enqueueRecent(RecentProgressResult.Failure(LibraryError.Unreachable))
            val booksShown = viewModel.uiState.value.books
            val errorShown = viewModel.uiState.value.error
            viewModel.libraryShown()
            viewModel.uiState.value.books shouldBe booksShown
            viewModel.uiState.value.error shouldBe errorShown
        }

        it("#22 TC-22: a stale load cannot overwrite a newer one") {
            val viewModel = createViewModel()
            val first = CompletableDeferred<RecentProgressResult>()
            repository.enqueueRecent(first)
            repository.enqueueRecent(success(reading(2)))

            viewModel.libraryShown()
            viewModel.libraryShown()
            first.complete(success(reading(1)))

            viewModel.uiState.value.continueCards.map { it.id } shouldBe listOf("b2:epub")
        }

        it("#22 TC-23: a refresh that moves the visible card keeps it selected") {
            val viewModel = loadedWith(reading(0), reading(1), reading(2))
            viewModel.continueCardSettled("b2:epub")

            repository.enqueueRecent(success(reading(2), reading(0)))
            viewModel.libraryShown()

            viewModel.uiState.value.continueSelectedId shouldBe "b2:epub"
        }

        it("#22 TC-23: a refresh that drops the visible card selects the new first card") {
            val viewModel = loadedWith(reading(0), reading(1), reading(2))
            viewModel.continueCardSettled("b2:epub")

            repository.enqueueRecent(success(reading(0), reading(1)))
            viewModel.libraryShown()

            viewModel.uiState.value.continueSelectedId shouldBe "b0:epub"
        }

        it("#22 TC-23: the last of three selected, then a list of one, selects that one") {
            val viewModel = loadedWith(reading(0), reading(1), reading(2))
            viewModel.continueCardSettled("b2:epub")

            repository.enqueueRecent(success(reading(5)))
            viewModel.libraryShown()

            viewModel.uiState.value.continueSelectedId shouldBe "b5:epub"
        }

        it("#22 TC-24: settling selects the card, and an unknown id is ignored") {
            val viewModel = loadedWith(reading(1), reading(2))

            viewModel.continueCardSettled("b2:epub")
            viewModel.uiState.value.continueSelectedId shouldBe "b2:epub"

            viewModel.continueCardSettled("missing:epub")
            viewModel.uiState.value.continueSelectedId shouldBe "b2:epub"
        }

        val resumeRows = listOf(
            ResumeRow("reading card, can open", reading(1), answer = true, target = ResumeTarget.Reader("b1"), unavailable = null, medium = ProgressFormat.Reading),
            ResumeRow("reading card, cannot open", reading(1), answer = false, target = null, unavailable = UnavailableBook("Book 1", ProgressFormat.Reading), medium = ProgressFormat.Reading),
            ResumeRow("listening card, can open", listening(3, listOf("m4b"), fileId = 20), answer = true, target = ResumeTarget.Player("b3", 20), unavailable = null, medium = ProgressFormat.Listening),
            ResumeRow("listening card, cannot open", listening(3, listOf("m4b"), fileId = 20), answer = false, target = null, unavailable = UnavailableBook("Book 3", ProgressFormat.Listening), medium = ProgressFormat.Listening),
            ResumeRow("listening record on an ebook-only book", listening(4, listOf("epub")), answer = true, target = ResumeTarget.Reader("b4"), unavailable = null, medium = ProgressFormat.Reading),
        )
        resumeRows.forEach { row ->
            it("#22 TC-25: ${row.name} routes to ${row.target ?: row.unavailable}") {
                val availability = FakeBookAvailability(row.answer)
                val viewModel = viewModelFor(repository, sortStore, availability = availability)
                repository.enqueueRecent(success(row.point))
                viewModel.libraryShown()

                viewModel.resume(viewModel.uiState.value.continueCards.single().id)

                val state = viewModel.uiState.value
                state.pendingResume shouldBe row.target
                state.unavailableBook shouldBe row.unavailable
                availability.calls shouldBe listOf(row.point.book.uuid to row.medium)
            }
        }

        it("#22 TC-25: an unknown card id changes nothing and does not ask the guard") {
            val availability = FakeBookAvailability()
            val viewModel = viewModelFor(repository, sortStore, availability = availability)
            repository.enqueueRecent(success(reading(1)))
            viewModel.libraryShown()
            val before = viewModel.uiState.value

            viewModel.resume("missing:epub")

            viewModel.uiState.value shouldBe before
            availability.calls shouldBe emptyList()
        }

        it("#22 TC-26: resumeHandled clears the pending resume") {
            val viewModel = viewModelFor(repository, sortStore, availability = FakeBookAvailability(true))
            repository.enqueueRecent(success(reading(1)))
            viewModel.libraryShown()
            viewModel.resume("b1:epub")
            viewModel.uiState.value.pendingResume shouldBe ResumeTarget.Reader("b1")

            viewModel.resumeHandled()

            viewModel.uiState.value.pendingResume shouldBe null
        }

        it("#22 TC-26: unavailableDismissed clears the alert") {
            val viewModel = viewModelFor(repository, sortStore, availability = FakeBookAvailability(false))
            repository.enqueueRecent(success(reading(1)))
            viewModel.libraryShown()
            viewModel.resume("b1:epub")
            viewModel.uiState.value.unavailableBook shouldBe UnavailableBook("Book 1", ProgressFormat.Reading)

            viewModel.unavailableDismissed()

            viewModel.uiState.value.unavailableBook shouldBe null
        }
    }
})

/** One TC-25 row: the card to resume and the result it should produce. */
private data class ResumeRow(
    val name: String,
    val point: ResumePoint,
    val answer: Boolean,
    val target: ResumeTarget?,
    val unavailable: UnavailableBook?,
    val medium: ProgressFormat,
)
