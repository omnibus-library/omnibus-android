package com.omnibus.omnibus.feature.library

import app.cash.turbine.test
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.BookAvailability
import com.omnibus.omnibus.data.library.BooksPage
import com.omnibus.omnibus.data.library.BooksPageQuery
import com.omnibus.omnibus.data.library.LibraryCategory
import com.omnibus.omnibus.data.library.LibraryError
import com.omnibus.omnibus.data.library.LibraryPageResult
import com.omnibus.omnibus.data.library.LibrarySort
import com.omnibus.omnibus.data.library.LibrarySortPreference
import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.data.library.RecentProgressResult
import com.omnibus.omnibus.data.library.ResumePoint
import com.omnibus.omnibus.data.library.SeriesBooksResult
import com.omnibus.omnibus.data.library.SortDirection
import com.omnibus.omnibus.data.library.TestBookAvailability
import com.omnibus.omnibus.data.library.TestLibraryRepository
import com.omnibus.omnibus.data.library.local.TestLibrarySortPreferenceStore
import com.omnibus.omnibus.data.library.page
import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.data.library.randomLibrarySortPreference
import com.omnibus.omnibus.data.library.about
import com.omnibus.omnibus.data.library.randomResumePoint
import com.omnibus.omnibus.data.library.randomSeriesBooks
import com.omnibus.omnibus.data.library.withRecord
import com.omnibus.omnibus.data.network.ConnectivityMonitor
import com.omnibus.omnibus.data.network.TestConnectivityMonitor
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

private val unreachable = LibraryPageResult.Failure(LibraryError.Unreachable)
private val unreachableError = LibraryUIError.Failed(LibraryError.Unreachable)

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun sut(
        repository: TestLibraryRepository = TestLibraryRepository(),
        sortStore: TestLibrarySortPreferenceStore = TestLibrarySortPreferenceStore(),
        monitor: ConnectivityMonitor = TestConnectivityMonitor(),
        availability: BookAvailability = TestBookAvailability(),
        time: TimeSource = randomLong().let { now -> TimeSource { now } },
    ) = LibraryViewModel(repository, sortStore, monitor, availability, time)

    fun pageOf(range: LongRange, cursor: String? = null) = page(*range.toList().toLongArray(), cursor = cursor)

    /** A ViewModel showing books 1 to 60, with more to load after cursor `c1`. */
    fun sutWithSixtyBooks(repository: TestLibraryRepository = TestLibraryRepository()): LibraryViewModel {
        repository.enqueue(pageOf(1L..60L, cursor = "c1"))
        return sut(repository)
    }

    fun LibraryViewModel.bookIds() = uiState.value.books.map { it.id }

    /** A random book with [id], in the series [seriesId] or in none. */
    fun loadedBook(id: Long, seriesId: Long?): Book = randomBook().copy(id = id, seriesId = seriesId)

    fun pageOfBooks(books: List<Book>, cursor: String? = null) = LibraryPageResult.Success(BooksPage(books, cursor, null, null))

    /** A ViewModel with grouping on, reading its series from [repository]. */
    fun groupedSut(
        repository: TestLibraryRepository = TestLibraryRepository(),
        monitor: ConnectivityMonitor = TestConnectivityMonitor(),
    ) = sut(repository, TestLibrarySortPreferenceStore(initialGroupBySeries = true), monitor)

    describe("loading the first page") {
        it("TC-56: requests the default query") {
            val repository = TestLibraryRepository().apply { enqueue(pageOf(1L..2L, cursor = "c1")) }

            val viewModel = sut(repository)

            repository.pageRequests.single() shouldBe TestLibraryRepository.PageRequest(BooksPageQuery(), null)
            viewModel.bookIds() shouldBe listOf(1L, 2L)
            viewModel.uiState.value.nextCursor shouldBe "c1"
        }

        context("with a stored sort") {
            it("TC-121: requests the stored sort and direction") {
                val saved = randomLibrarySortPreference()
                val repository = TestLibraryRepository()

                sut(repository, TestLibrarySortPreferenceStore(saved))

                repository.pageRequests.single().query shouldBe BooksPageQuery(sort = saved.sort, dir = saved.direction)
            }

            it("TC-122: waits for the stored sort before requesting") {
                val release = CompletableDeferred<Unit>()
                val repository = TestLibraryRepository()

                sut(repository, TestLibrarySortPreferenceStore().apply { holdFirstEmission = release })
                repository.pageRequests.shouldBeEmpty()

                release.complete(Unit)
                repository.pageRequests.size shouldBe 1
            }
        }
    }

    describe("the screen") {
        context("while the first page is in flight") {
            it("TC-58: is Loading") {
                val repository = TestLibraryRepository().apply { enqueue(CompletableDeferred()) }

                sut(repository).uiState.value.screen shouldBe LibraryScreenState.Loading
            }
        }

        context("when the first page fails") {
            it("TC-59: is Error with the typed failure, for a failure or no server") {
                checkAll(
                    Exhaustive.of(unreachable to unreachableError, LibraryPageResult.NoServer to LibraryUIError.NoServer),
                ) { (result, error) ->
                    val repository = TestLibraryRepository().apply { enqueue(result) }

                    sut(repository).uiState.value.screen shouldBeEqual LibraryScreenState.Error(error)
                }
            }

            context("when retried") {
                it("TC-60: goes through Loading to Content") {
                    val repository = TestLibraryRepository().apply { enqueue(unreachable) }
                    val viewModel = sut(repository)
                    val second = CompletableDeferred<LibraryPageResult>()
                    repository.enqueue(second)

                    viewModel.retry()
                    viewModel.uiState.value.screen shouldBe LibraryScreenState.Loading

                    second.complete(page(1L, 2L))
                    viewModel.uiState.value.screen shouldBe LibraryScreenState.Content
                }
            }
        }

        it("TC-63: picks Loading, then Error, then Empty, then Content, by first match") {
            val book = randomBook()

            checkAll(
                Exhaustive.of(
                    LibraryUIState(isInitialLoading = true, error = LibraryUIError.NoServer) to LibraryScreenState.Loading,
                    LibraryUIState(error = LibraryUIError.NoServer) to LibraryScreenState.Error(LibraryUIError.NoServer),
                    LibraryUIState() to LibraryScreenState.Empty,
                    LibraryUIState(books = listOf(book), error = LibraryUIError.NoServer) to LibraryScreenState.Content,
                    LibraryUIState(isInitialLoading = true, books = listOf(book)) to LibraryScreenState.Content,
                ),
            ) { (state, screen) ->
                state.screen shouldBeEqual screen
            }
        }

        context("with a filtered result with no books") {
            it("TC-160: is Content") {
                LibraryUIState(hasLoaded = true, category = LibraryCategory.Ebooks).screen shouldBe LibraryScreenState.Content
            }
        }
    }

    describe("whether the books are filtered") {
        it("TC-103: is true for each non-default category, sort or direction on its own") {
            val filtered = listOf(
                LibraryUIState(category = LibraryCategory.Ebooks),
                LibraryUIState(category = LibraryCategory.Audiobooks),
                LibraryUIState(direction = SortDirection.Asc),
            ) + LibrarySort.entries.filter { it != LibrarySort.NewestAdded }.map { LibraryUIState(sort = it) }

            checkAll(Exhaustive.collection(filtered)) { state ->
                state.isFiltered shouldBe true
            }
        }
    }

    describe("loading more") {
        it("TC-77: moves on to the next page") {
            val repository = TestLibraryRepository()
            val viewModel = sutWithSixtyBooks(repository)
            repository.enqueue(pageOf(61L..90L, cursor = "c2"))

            viewModel.loadMoreIfNeeded(0)

            repository.pageRequests.last() shouldBe TestLibraryRepository.PageRequest(BooksPageQuery(), "c1")
            viewModel.bookIds() shouldBe (1L..90L).toList()
            viewModel.uiState.value.nextCursor shouldBe "c2"
        }

        it("TC-78, #235 TC-18: requests with 11 items after the last visible one, not 12") {
            val repository = TestLibraryRepository()
            val viewModel = sutWithSixtyBooks(repository)

            viewModel.loadMoreIfNeeded(12)
            repository.pageRequests.size shouldBe 1

            viewModel.loadMoreIfNeeded(11)
            repository.pageRequests.size shouldBe 2
        }

        context("without a next cursor") {
            it("TC-79: makes no request") {
                val repository = TestLibraryRepository().apply { enqueue(pageOf(1L..2L)) }
                val viewModel = sut(repository)

                viewModel.loadMoreIfNeeded(0)

                repository.pageRequests.size shouldBe 1
            }
        }

        context("while a refresh is in flight") {
            it("TC-80: makes no request") {
                val repository = TestLibraryRepository()
                val viewModel = sutWithSixtyBooks(repository)
                repository.enqueue(CompletableDeferred())
                viewModel.refresh()

                viewModel.loadMoreIfNeeded(0)

                repository.pageRequests.size shouldBe 2
            }
        }

        context("while another load-more is in flight") {
            it("TC-80: makes no request") {
                val repository = TestLibraryRepository()
                val viewModel = sutWithSixtyBooks(repository)
                repository.enqueue(CompletableDeferred())
                viewModel.loadMoreIfNeeded(0)

                viewModel.loadMoreIfNeeded(0)

                repository.pageRequests.size shouldBe 2
            }
        }

        context("with a page that repeats a book") {
            it("TC-81: keeps the first occurrence") {
                val repository = TestLibraryRepository()
                val viewModel = sutWithSixtyBooks(repository)
                val first = viewModel.uiState.value.books.first { it.id == 59L }
                repository.enqueue(
                    LibraryPageResult.Success(BooksPage(listOf(randomBook().copy(id = 59), randomBook().copy(id = 61)), "c2", null, null)),
                )

                viewModel.loadMoreIfNeeded(0)

                viewModel.bookIds() shouldBe (1L..61L).toList()
                viewModel.uiState.value.books.first { it.id == 59L } shouldBe first
            }
        }

        context("with an empty page that carries a cursor") {
            it("TC-82: ends paging") {
                val repository = TestLibraryRepository()
                val viewModel = sutWithSixtyBooks(repository)
                repository.enqueue(LibraryPageResult.Success(BooksPage(emptyList(), "c2", null, null)))
                viewModel.loadMoreIfNeeded(0)

                viewModel.loadMoreIfNeeded(0)

                repository.pageRequests.size shouldBe 2
            }
        }

        context("when it fails") {
            it("TC-95: keeps the books, showing a transient error") {
                val repository = TestLibraryRepository()
                val viewModel = sutWithSixtyBooks(repository)
                repository.enqueue(unreachable)

                viewModel.loadMoreIfNeeded(0)

                val state = viewModel.uiState.value
                state.books.size shouldBe 60
                state.transientError shouldBe unreachableError
                state.isLoadingMore shouldBe false
                state.screen shouldBe LibraryScreenState.Content
            }

            context("when the next scroll arrives") {
                it("TC-96: retries with the same cursor") {
                    val repository = TestLibraryRepository()
                    val viewModel = sutWithSixtyBooks(repository)
                    repository.enqueue(unreachable)
                    viewModel.loadMoreIfNeeded(0)

                    viewModel.loadMoreIfNeeded(0)

                    repository.pageRequests.last().cursor shouldBe "c1"
                }
            }
        }

        context("after a query change") {
            it("TC-133: uses the current query") {
                val repository = TestLibraryRepository()
                val viewModel = sutWithSixtyBooks(repository)
                repository.enqueue(pageOf(1L..60L, cursor = "c2"))
                viewModel.selectSort(LibrarySort.Author)
                repository.enqueue(pageOf(1L..60L, cursor = "c3"))
                viewModel.selectCategory(LibraryCategory.Audiobooks)

                viewModel.loadMoreIfNeeded(0)

                repository.pageRequests.last() shouldBe TestLibraryRepository.PageRequest(
                    BooksPageQuery(sort = LibrarySort.Author, formats = LibraryCategory.Audiobooks.formats),
                    "c3",
                )
            }
        }
    }

    describe("refreshing") {
        it("TC-85: starts over from the new first page") {
            val repository = TestLibraryRepository()
            val viewModel = sutWithSixtyBooks(repository)
            repository.enqueue(pageOf(100L..101L, cursor = "new"))

            viewModel.refresh()

            viewModel.bookIds() shouldBe listOf(100L, 101L)
            viewModel.uiState.value.nextCursor shouldBe "new"
            repository.pageRequests.last().cursor shouldBe null
        }

        context("with a cached page before the network one") {
            it("TC-86: stays refreshing until the flow completes") {
                val repository = TestLibraryRepository()
                val viewModel = sut(repository)
                val network = CompletableDeferred<LibraryPageResult>()
                repository.enqueueFlow {
                    emit(page(7L))
                    emit(network.await())
                }

                viewModel.refresh()
                viewModel.bookIds() shouldBe listOf(7L)
                viewModel.uiState.value.isRefreshing shouldBe true

                network.complete(page(8L, 9L))
                viewModel.bookIds() shouldBe listOf(8L, 9L)
                viewModel.uiState.value.isRefreshing shouldBe false
            }
        }

        context("when it fails with books on screen") {
            it("TC-97: keeps the books, showing a transient error") {
                val repository = TestLibraryRepository()
                val viewModel = sutWithSixtyBooks(repository)
                repository.enqueue(unreachable)

                viewModel.refresh()

                val state = viewModel.uiState.value
                state.books.size shouldBe 60
                state.nextCursor shouldBe "c1"
                state.transientError shouldBe unreachableError
                state.isRefreshing shouldBe false
            }
        }

        context("with a load-more in flight") {
            it("TC-90: drops the load-more's late result") {
                val repository = TestLibraryRepository()
                val viewModel = sutWithSixtyBooks(repository)
                val loadMore = CompletableDeferred<LibraryPageResult>()
                repository.enqueue(loadMore)
                viewModel.loadMoreIfNeeded(0)
                repository.enqueue(page(100L, cursor = "n"))
                viewModel.refresh()

                loadMore.complete(page(61L, cursor = "c2"))

                viewModel.bookIds() shouldBe listOf(100L)
                viewModel.uiState.value.isLoadingMore shouldBe false
            }
        }

        context("with an older reload that completes last") {
            it("TC-89: drops the older result") {
                val repository = TestLibraryRepository().apply { enqueue(unreachable) }
                val viewModel = sut(repository)
                val older = CompletableDeferred<LibraryPageResult>()
                repository.enqueue(older)
                viewModel.retry()
                val newer = CompletableDeferred<LibraryPageResult>()
                repository.enqueue(newer)
                viewModel.refresh()

                newer.complete(page(2L))
                older.complete(page(1L))

                viewModel.bookIds() shouldBe listOf(2L)
            }
        }
    }

    describe("the transient error") {
        context("when it has been shown") {
            it("TC-98: clears") {
                val repository = TestLibraryRepository()
                val viewModel = sutWithSixtyBooks(repository)
                repository.enqueue(unreachable)
                viewModel.loadMoreIfNeeded(0)

                viewModel.transientErrorShown()

                viewModel.uiState.value.transientError shouldBe null
            }
        }
    }

    describe("changing the query") {
        it("TC-126: never saves a category") {
            val sortStore = TestLibrarySortPreferenceStore()
            val viewModel = sut(sortStore = sortStore)

            viewModel.selectCategory(LibraryCategory.Ebooks)

            sortStore.saves.shouldBeEmpty()
        }

        context("with a new sort") {
            it("TC-129: keeps the current direction and category") {
                val repository = TestLibraryRepository()
                val viewModel = sut(repository)
                viewModel.selectCategory(LibraryCategory.Audiobooks)

                viewModel.selectSort(LibrarySort.Title)

                repository.pageRequests.last() shouldBe TestLibraryRepository.PageRequest(
                    BooksPageQuery(sort = LibrarySort.Title, dir = SortDirection.Desc, formats = LibraryCategory.Audiobooks.formats),
                    null,
                )
            }
        }

        it("TC-130: flips the direction there and back") {
            val repository = TestLibraryRepository()
            val viewModel = sut(repository)

            viewModel.toggleDirection()
            repository.pageRequests.last().query.dir shouldBe SortDirection.Asc

            viewModel.toggleDirection()
            repository.pageRequests.last().query.dir shouldBe SortDirection.Desc
        }

        context("with the value already active") {
            it("TC-131: does nothing") {
                val repository = TestLibraryRepository()
                val sortStore = TestLibrarySortPreferenceStore()
                val viewModel = sut(repository, sortStore)
                val stateBefore = viewModel.uiState.value

                viewModel.selectCategory(LibraryCategory.All)
                viewModel.selectSort(LibrarySort.NewestAdded)

                repository.pageRequests.size shouldBe 1
                sortStore.saves.shouldBeEmpty()
                viewModel.uiState.value shouldBeEqual stateBefore
            }
        }

        it("TC-132: saves a sort or direction change") {
            val sortStore = TestLibrarySortPreferenceStore()
            val viewModel = sut(sortStore = sortStore)

            viewModel.selectSort(LibrarySort.Series)
            viewModel.toggleDirection()

            sortStore.saves shouldBe listOf(
                LibrarySortPreference(LibrarySort.Series, SortDirection.Desc),
                LibrarySortPreference(LibrarySort.Series, SortDirection.Asc),
            )
        }

        it("TC-137: drops the old cursor") {
            val repository = TestLibraryRepository()
            val viewModel = sutWithSixtyBooks(repository)
            repository.enqueue(CompletableDeferred())

            viewModel.selectSort(LibrarySort.Title)

            repository.pageRequests.last().cursor shouldBe null
            viewModel.uiState.value.nextCursor shouldBe null
        }

        context("while the reload is pending") {
            it("TC-138: keeps the old books on screen") {
                val repository = TestLibraryRepository().apply { enqueue(pageOf(1L..2L)) }
                val viewModel = sut(repository)
                repository.enqueue(CompletableDeferred())

                viewModel.selectSort(LibrarySort.Title)

                viewModel.bookIds() shouldBe listOf(1L, 2L)
                viewModel.uiState.value.screen shouldBe LibraryScreenState.Content
            }
        }
    }

    describe("scrolling to the top") {
        context("after a query change") {
            it("TC-140: is asked once, not again for later pages of that reload") {
                val repository = TestLibraryRepository().apply { enqueue(page(1L, 2L)) }
                val viewModel = sut(repository)
                val later = CompletableDeferred<LibraryPageResult>()
                repository.enqueueFlow {
                    emit(page(7L))
                    emit(later.await())
                }

                viewModel.selectCategory(LibraryCategory.Ebooks)
                viewModel.uiState.value.scrollToTop shouldBe true

                viewModel.scrolledToTop()
                later.complete(page(8L))

                viewModel.uiState.value.scrollToTop shouldBe false
            }

            context("when the reload fails") {
                it("TC-142: is not asked") {
                    val repository = TestLibraryRepository().apply { enqueue(page(1L, 2L)) }
                    val viewModel = sut(repository)
                    repository.enqueue(unreachable)

                    viewModel.selectSort(LibrarySort.Title)

                    viewModel.uiState.value.scrollToTop shouldBe false
                }
            }
        }

        it("TC-141: is never asked by the initial load, a refresh or a load-more") {
            val repository = TestLibraryRepository()
            val viewModel = sutWithSixtyBooks(repository)
            viewModel.uiState.value.scrollToTop shouldBe false

            repository.enqueue(pageOf(1L..60L, cursor = "c2"))
            viewModel.refresh()
            viewModel.uiState.value.scrollToTop shouldBe false

            repository.enqueue(pageOf(61L..62L, cursor = "c3"))
            viewModel.loadMoreIfNeeded(0)
            viewModel.uiState.value.scrollToTop shouldBe false
        }
    }

    describe("restoring the grouping setting") {
        it("#235 TC-8: has grouping on in the state that first shows the books") {
            val repository = TestLibraryRepository().apply { enqueue(page(1L, 2L)) }
            val viewModel = sut(repository, TestLibrarySortPreferenceStore(initialGroupBySeries = true))

            viewModel.uiState.test {
                val state = awaitItem()

                state.books.size shouldBe 2
                state.groupBySeries shouldBe true
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    describe("toggling grouping") {
        context("when grouping is off") {
            it("#235 TC-9: turns grouping on") {
                val viewModel = sut()

                viewModel.toggleGroupBySeries()

                viewModel.uiState.value.groupBySeries shouldBe true
            }

            it("#235 TC-9: saves grouping on") {
                val sortStore = TestLibrarySortPreferenceStore()

                sut(sortStore = sortStore).toggleGroupBySeries()

                sortStore.groupSaves shouldBe listOf(true)
            }
        }

        context("when grouping is on") {
            it("#235 TC-9: turns grouping off") {
                val viewModel = groupedSut()

                viewModel.toggleGroupBySeries()

                viewModel.uiState.value.groupBySeries shouldBe false
            }

            it("#235 TC-9: saves grouping off") {
                val sortStore = TestLibrarySortPreferenceStore(initialGroupBySeries = true)

                sut(sortStore = sortStore).toggleGroupBySeries()

                sortStore.groupSaves shouldBe listOf(false)
            }
        }
    }

    describe("unfolding series") {
        context("when grouping is on") {
            it("#235 TC-10: unfolds a series") {
                val viewModel = groupedSut()
                val series = randomLong()

                viewModel.unfoldSeries(series)

                viewModel.uiState.value.unfoldedSeries shouldBe setOf(series)
            }

            it("#235 TC-10: keeps several series unfolded at once") {
                val viewModel = groupedSut()
                val first = randomLong()
                val second = randomLong()

                viewModel.unfoldSeries(first)
                viewModel.unfoldSeries(second)

                viewModel.uiState.value.unfoldedSeries shouldBe setOf(first, second)
            }

            it("#235 TC-10: folds up only the series it is given") {
                val viewModel = groupedSut()
                val first = randomLong()
                val second = randomLong()
                viewModel.unfoldSeries(first)
                viewModel.unfoldSeries(second)

                viewModel.foldUpSeries(first)

                viewModel.uiState.value.unfoldedSeries shouldBe setOf(second)
            }

            it("#235 TC-10: unfolds none once grouping is turned off") {
                val viewModel = groupedSut()
                viewModel.unfoldSeries(randomLong())
                viewModel.unfoldSeries(randomLong())

                viewModel.toggleGroupBySeries()

                viewModel.uiState.value.unfoldedSeries.shouldBeEmpty()
            }
        }

        it("#235 TC-10: starts with none unfolded in a new ViewModel") {
            groupedSut().unfoldSeries(randomLong())

            groupedSut().uiState.value.unfoldedSeries.shouldBeEmpty()
        }
    }

    describe("grouping off") {
        /** Loads one page of two books in a series, then a page of a third, and changes the sort and category. */
        fun TestLibraryRepository.loadOffThroughQueryChanges(): LibraryViewModel {
            val series = randomLong()
            enqueue(pageOfBooks(listOf(loadedBook(1L, series), loadedBook(2L, series)), cursor = "c1"))
            val viewModel = sut(this)
            enqueue(pageOfBooks(listOf(loadedBook(3L, series))))
            viewModel.loadMoreIfNeeded(0)
            viewModel.selectSort(LibrarySort.Title)
            viewModel.selectCategory(LibraryCategory.Ebooks)
            return viewModel
        }

        it("#235 TC-11: lists every loaded book as its own entry, through paging and query changes") {
            val viewModel = TestLibraryRepository().loadOffThroughQueryChanges()
            val books = viewModel.uiState.value.books

            books.map { it.id } shouldBe listOf(1L, 2L, 3L)
            viewModel.uiState.value.entries shouldBe books.map { LibraryEntry.Single(it) }
        }

        it("#235 TC-11: reads no series") {
            val repository = TestLibraryRepository()

            repository.loadOffThroughQueryChanges()

            repository.seriesRequests.shouldBeEmpty()
        }
    }

    describe("reading series") {
        /** Series reads that come back as an error, for each kind of failed read. */
        fun failedReads() = Exhaustive.of(SeriesBooksResult.NoServer, SeriesBooksResult.Failure(LibraryError.Unreachable))

        /** A repository whose first page holds a book in each of [gates]' series, with each read held on its gate. */
        fun gatedRepository(gates: List<CompletableDeferred<SeriesBooksResult>>): TestLibraryRepository =
            TestLibraryRepository().apply {
                gates.forEachIndexed { index, gate -> enqueueSeries(index + 1L, gate) }
                enqueue(pageOfBooks((1L..6L).map { loadedBook(it, seriesId = it) }))
            }

        context("when a page is loaded") {
            it("#235 TC-12: reads each series once, however many pages repeat it") {
                val (first, second, third) = List(3) { randomLong() }
                val repository = TestLibraryRepository().apply {
                    defaultSeriesResult = { SeriesBooksResult.Success(randomSeriesBooks()) }
                    enqueue(
                        pageOfBooks(
                            listOf(loadedBook(1L, first), loadedBook(2L, first), loadedBook(3L, second), loadedBook(4L, null)),
                            cursor = "c1",
                        ),
                    )
                    enqueue(pageOfBooks(listOf(loadedBook(5L, second), loadedBook(6L, third))))
                }
                val viewModel = groupedSut(repository)

                viewModel.loadMoreIfNeeded(0)
                viewModel.selectCategory(LibraryCategory.Audiobooks)
                viewModel.toggleDirection()

                repository.seriesRequests shouldContainExactlyInAnyOrder listOf(first, second, third)
            }

            it("#235 TC-12: keeps each series' read through a category and a direction change") {
                val series = randomLong()
                val read = randomSeriesBooks()
                val repository = TestLibraryRepository().apply {
                    enqueue(pageOfBooks(listOf(loadedBook(1L, series))))
                    enqueueSeries(series, SeriesBooksResult.Success(read))
                }
                val viewModel = groupedSut(repository)

                viewModel.selectCategory(LibraryCategory.Audiobooks)
                viewModel.toggleDirection()

                viewModel.uiState.value.seriesBooks shouldBe mapOf(series to read)
            }

            it("#235 TC-13: starts SERIES_READ_CONCURRENCY reads and holds the rest") {
                val gates = List(6) { CompletableDeferred<SeriesBooksResult>() }
                val repository = gatedRepository(gates)

                groupedSut(repository)

                repository.seriesRequests.size shouldBe SERIES_READ_CONCURRENCY
            }

            it("#235 TC-13: completes all six reads once they are released") {
                val gates = List(6) { CompletableDeferred<SeriesBooksResult>() }
                val viewModel = groupedSut(gatedRepository(gates))

                gates.forEach { it.complete(SeriesBooksResult.Success(randomSeriesBooks())) }

                viewModel.uiState.value.seriesBooks.keys shouldBe (1L..6L).toSet()
            }

            it("#235 TC-14: raises no error for a read that gives NoServer or Failure") {
                checkAll(failedReads()) { failed ->
                    val series = randomLong()
                    val repository = TestLibraryRepository().apply {
                        enqueue(pageOfBooks(listOf(loadedBook(1L, series))))
                        enqueueSeries(series, failed)
                    }

                    val state = groupedSut(repository).uiState.value

                    state.error shouldBe null
                    state.transientError shouldBe null
                }
            }

            it("#235 TC-14: still lists the series from its loaded books") {
                checkAll(failedReads()) { failed ->
                    val series = randomLong()
                    val repository = TestLibraryRepository().apply {
                        enqueue(pageOfBooks(listOf(loadedBook(1L, series))))
                        enqueueSeries(series, failed)
                    }

                    groupedSut(repository).uiState.value.entries.single().shouldBeInstanceOf<LibraryEntry.Series>()
                }
            }
        }

        context("when the connection comes back") {
            it("#235 TC-15: reads a series that failed again, and fills it in") {
                val series = randomLong()
                val read = randomSeriesBooks()
                val monitor = TestConnectivityMonitor(online = true)
                val repository = TestLibraryRepository().apply {
                    enqueue(pageOfBooks(listOf(loadedBook(1L, series))))
                    enqueueSeries(series, SeriesBooksResult.Failure(LibraryError.Unreachable))
                    enqueueSeries(series, SeriesBooksResult.Success(read))
                }
                val viewModel = groupedSut(repository, monitor)

                monitor.isOnline.value = false
                monitor.isOnline.value = true

                repository.seriesRequests shouldBe listOf(series, series)
                viewModel.uiState.value.seriesBooks shouldBe mapOf(series to read)
            }
        }

        context("when refreshed") {
            it("#235 TC-16: reads every listed series again") {
                val first = randomLong()
                val second = randomLong()
                val repository = TestLibraryRepository().apply {
                    defaultSeriesResult = { SeriesBooksResult.Success(randomSeriesBooks()) }
                    enqueue(pageOfBooks(listOf(loadedBook(1L, first), loadedBook(2L, second))))
                }
                val viewModel = groupedSut(repository)
                repository.enqueue(pageOfBooks(listOf(loadedBook(1L, first), loadedBook(2L, second))))

                viewModel.refresh()

                repository.seriesRequests.groupingBy { it }.eachCount() shouldBe mapOf(first to 2, second to 2)
            }
        }

        context("when grouping is switched on again") {
            it("#235 TC-17: reads a series that failed before it was switched off") {
                val series = randomLong()
                val read = randomSeriesBooks()
                val repository = TestLibraryRepository().apply {
                    enqueue(pageOfBooks(listOf(loadedBook(1L, series))))
                    enqueueSeries(series, SeriesBooksResult.Failure(LibraryError.Unreachable))
                    defaultSeriesResult = { SeriesBooksResult.Success(read) }
                }
                val viewModel = groupedSut(repository)
                viewModel.toggleGroupBySeries()

                viewModel.toggleGroupBySeries()

                repository.seriesRequests shouldBe listOf(series, series)
                viewModel.uiState.value.seriesBooks shouldBe mapOf(series to read)
            }
        }
    }

    describe("#21 the offline state") {
        it("#21 TC-3: follows the monitor from the first state") {
            val monitor = TestConnectivityMonitor(online = false)
            val viewModel = sut(monitor = monitor)
            viewModel.uiState.value.isOffline shouldBe true

            monitor.isOnline.value = true

            viewModel.uiState.value.isOffline shouldBe false
        }
    }

    describe("#22 the Continue cards") {
        fun bookIn(vararg formats: String) = randomBook().copy(uniqueIdentifier = randomString(8..16), formats = formats.toList())

        fun reading() = randomResumePoint().about(bookIn("epub")).withRecord { copy(format = ProgressFormat.Reading) }

        fun success(vararg points: ResumePoint) = RecentProgressResult.Success(points.toList())

        fun TestLibraryRepository.shown(vararg points: ResumePoint, availability: BookAvailability = TestBookAvailability()): LibraryViewModel {
            enqueueRecent(success(*points))
            return sut(this, availability = availability).also { it.libraryShown() }
        }

        fun LibraryViewModel.cardIds() = uiState.value.continueCards.map { it.id }

        context("with an empty list") {
            it("#22 TC-19: clears the cards") {
                val repository = TestLibraryRepository()
                val viewModel = repository.shown(reading(), reading())
                repository.enqueueRecent(success())

                viewModel.libraryShown()

                viewModel.cardIds().shouldBeEmpty()
            }
        }

        context("when the load fails") {
            it("#22 TC-20: keeps the cards, with no error, for a failure or no server") {
                checkAll(
                    Exhaustive.of(RecentProgressResult.Failure(LibraryError.Unreachable), RecentProgressResult.NoServer),
                ) { failure ->
                    val repository = TestLibraryRepository()
                    val point = reading()
                    val viewModel = repository.shown(point)
                    repository.enqueueRecent(failure)

                    viewModel.libraryShown()

                    viewModel.cardIds() shouldBe listOf(point.cardId)
                    viewModel.uiState.value.error shouldBe null
                    viewModel.uiState.value.transientError shouldBe null
                }
            }
        }

        it("#22 TC-21: reload as the Library is shown again") {
            val repository = TestLibraryRepository()
            val viewModel = repository.shown(reading())
            val next = reading()
            repository.enqueueRecent(success(next))

            viewModel.libraryShown()

            viewModel.cardIds() shouldBe listOf(next.cardId)
        }

        context("when the library refreshes") {
            it("#22 TC-21: reload") {
                val repository = TestLibraryRepository()
                val viewModel = repository.shown(reading())
                val next = reading()
                repository.enqueueRecent(success(next))

                viewModel.refresh()

                viewModel.cardIds() shouldBe listOf(next.cardId)
            }
        }

        context("with a stale load") {
            it("#22 TC-22: applies only the newer one") {
                val repository = TestLibraryRepository()
                val viewModel = sut(repository)
                val first = CompletableDeferred<RecentProgressResult>()
                val newer = reading()
                repository.enqueueRecent(first)
                repository.enqueueRecent(success(newer))

                viewModel.libraryShown()
                viewModel.libraryShown()
                first.complete(success(reading()))

                viewModel.cardIds() shouldBe listOf(newer.cardId)
            }
        }

        context("with a refresh that moves the selected card") {
            it("#22 TC-23: keeps it selected") {
                val repository = TestLibraryRepository()
                val (a, b, c) = List(3) { reading() }
                val viewModel = repository.shown(a, b, c)
                viewModel.continueCardSettled(c.cardId)
                repository.enqueueRecent(success(c, a))

                viewModel.libraryShown()

                viewModel.uiState.value.continueSelectedId shouldBe c.cardId
            }
        }

        it("#22 TC-24: selects the settled card, ignoring an unknown id") {
            val (a, b) = List(2) { reading() }
            val viewModel = TestLibraryRepository().shown(a, b)

            viewModel.continueCardSettled(b.cardId)
            viewModel.continueCardSettled(randomString())

            viewModel.uiState.value.continueSelectedId shouldBe b.cardId
        }

        describe("resuming a card") {
            context("when its book can open") {
                it("#22 TC-25: opens the reader for a reading card") {
                    val point = reading()
                    val viewModel = TestLibraryRepository().shown(point, availability = TestBookAvailability(answer = true))

                    viewModel.resume(point.cardId)

                    viewModel.uiState.value.pendingResume shouldBe ResumeTarget.Reader(point.book.uuid)
                }

                it("#22 TC-25: opens the player at the saved file for a listening card") {
                    val point = randomResumePoint().about(bookIn("m4b")).withRecord { copy(format = ProgressFormat.Listening, bookFileId = 20L) }
                    val viewModel = TestLibraryRepository().shown(point, availability = TestBookAvailability(answer = true))

                    viewModel.resume(point.cardId)

                    viewModel.uiState.value.pendingResume shouldBe ResumeTarget.Player(point.book.uuid, 20L)
                }

                context("when the request is handled") {
                    it("#22 TC-26: clears the request") {
                        val point = reading()
                        val viewModel = TestLibraryRepository().shown(point)
                        viewModel.resume(point.cardId)

                        viewModel.resumeHandled()

                        viewModel.uiState.value.pendingResume shouldBe null
                    }
                }
            }

            context("when its book can't open") {
                fun unavailableAfterResume(point: ResumePoint) =
                    TestLibraryRepository().shown(point, availability = TestBookAvailability(false)).also { it.resume(point.cardId) }

                it("#22 TC-25: raises the unavailable alert") {
                    val point = reading()

                    unavailableAfterResume(point).uiState.value.unavailableBook shouldBe
                        UnavailableBook(point.book.displayTitle, ProgressFormat.Reading)
                }

                context("when the alert is dismissed") {
                    it("#22 TC-26: clears the alert") {
                        val viewModel = unavailableAfterResume(reading())

                        viewModel.unavailableDismissed()

                        viewModel.uiState.value.unavailableBook shouldBe null
                    }
                }
            }

            context("with an unknown card id") {
                it("#22 TC-25: changes nothing") {
                    val viewModel = TestLibraryRepository().shown(reading())
                    val before = viewModel.uiState.value

                    viewModel.resume(randomString())

                    viewModel.uiState.value shouldBeEqual before
                }
            }
        }
    }
})
