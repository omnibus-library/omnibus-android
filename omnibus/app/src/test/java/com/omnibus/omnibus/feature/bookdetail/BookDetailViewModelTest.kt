package com.omnibus.omnibus.feature.bookdetail

import com.omnibus.omnibus.data.library.BookResult
import com.omnibus.omnibus.data.library.LibraryError
import com.omnibus.omnibus.data.library.testBook
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class BookDetailViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    val book1 = testBook(1)
    val book2 = testBook(1, title = "Second printing")
    val book3 = testBook(1, title = "Third printing")
    val cached = testBook(1, title = "Cached")
    val network = testBook(1, title = "Network")
    val otherBook = testBook(2)

    /** Every failure the repository can report for a read. */
    val failures: List<LibraryError> = listOf(
        LibraryError.Unreachable,
        LibraryError.SessionExpired,
        LibraryError.UnexpectedResponse,
        LibraryError.Http(500, null),
    )

    /** The one place a test builds the ViewModel, so later layers only change this helper. */
    fun viewModel(repo: ScriptedBookRepository) = BookDetailViewModel("uuid-1", repo)

    describe("initial load") {

        it("#93 TC-51: starts in Loading") {
            val repo = ScriptedBookRepository()
            val held = CompletableDeferred<BookResult>()
            repo.enqueue(held)

            val vm = viewModel(repo)

            val state = vm.uiState.value
            state.isLoading shouldBe true
            state.book shouldBe null
            state.error shouldBe null
            state.screen shouldBe BookDetailScreenState.Loading
            state.reloadKey shouldBe 0
            repo.bookRequests shouldBe listOf("uuid-1")
        }

        it("#93 TC-52: first load shows content") {
            val repo = ScriptedBookRepository()
            val vm = viewModel(repo)

            val state = vm.uiState.value
            state.screen shouldBe BookDetailScreenState.Content(book1)
            state.isLoading shouldBe false
            state.reloadKey shouldBe 0
            repo.bookRequests.size shouldBe 1
        }

        it("#93 TC-53: maps each failure to a full-screen error") {
            for (result in listOf(BookResult.NotFound, BookResult.NoServer) + failures.map { BookResult.Failure(it) }) {
                val repo = ScriptedBookRepository()
                repo.enqueue(result)

                val state = viewModel(repo).uiState.value

                val expected = when (result) {
                    BookResult.NotFound -> BookDetailError.NotFound
                    BookResult.NoServer -> BookDetailError.NoServer
                    is BookResult.Failure -> BookDetailError.Failed(result.error)
                    is BookResult.Success -> error("not a failure")
                }
                state.screen shouldBe BookDetailScreenState.Error(expected)
                state.isLoading shouldBe false
                state.transientError shouldBe null
            }
        }
    }

    describe("retry") {

        it("#93 TC-54: retry reloads from Loading") {
            val repo = ScriptedBookRepository()
            repo.enqueue(BookResult.NotFound)
            val vm = viewModel(repo)
            vm.uiState.value.screen shouldBe BookDetailScreenState.Error(BookDetailError.NotFound)

            val held = CompletableDeferred<BookResult>()
            repo.enqueue(held)
            vm.retry()

            val loading = vm.uiState.value
            loading.isLoading shouldBe true
            loading.error shouldBe null
            loading.screen shouldBe BookDetailScreenState.Loading
            loading.reloadKey shouldBe 1

            held.complete(BookResult.Success(book1))
            vm.uiState.value.screen shouldBe BookDetailScreenState.Content(book1)
        }

        it("#93 TC-55: retry that fails shows the error again") {
            val repo = ScriptedBookRepository()
            repo.enqueue(BookResult.NotFound)
            val vm = viewModel(repo)

            repo.enqueue(BookResult.Failure(LibraryError.Unreachable))
            vm.retry()

            val state = vm.uiState.value
            state.screen shouldBe BookDetailScreenState.Error(BookDetailError.Failed(LibraryError.Unreachable))
            state.reloadKey shouldBe 1
        }

        it("#93 TC-72: success clears error") {
            val repo = ScriptedBookRepository()
            repo.enqueue(BookResult.Failure(LibraryError.Unreachable))
            val vm = viewModel(repo)
            vm.uiState.value.error shouldBe BookDetailError.Failed(LibraryError.Unreachable)

            vm.retry()

            val state = vm.uiState.value
            state.error shouldBe null
            state.transientError shouldBe null
            state.screen shouldBe BookDetailScreenState.Content(book1)
        }
    }

    describe("refresh") {

        it("#93 TC-56: refresh keeps content and spins") {
            val repo = ScriptedBookRepository()
            val vm = viewModel(repo)
            val held = CompletableDeferred<BookResult>()
            repo.enqueue(held)

            vm.refresh()

            val state = vm.uiState.value
            state.isRefreshing shouldBe true
            state.isLoading shouldBe false
            state.book shouldBe book1
            state.screen shouldBe BookDetailScreenState.Content(book1)
            state.reloadKey shouldBe 1
        }

        it("#93 TC-57: refresh success replaces the book") {
            val repo = ScriptedBookRepository()
            val vm = viewModel(repo)
            val held = CompletableDeferred<BookResult>()
            repo.enqueue(held)
            vm.refresh()

            held.complete(BookResult.Success(book2))

            val state = vm.uiState.value
            state.book shouldBe book2
            state.isRefreshing shouldBe false
            state.transientError shouldBe null
        }

        it("#93 TC-58: failed refresh raises a transient error") {
            for (failure in failures.map { BookResult.Failure(it) } + listOf(BookResult.NotFound, BookResult.NoServer)) {
                val repo = ScriptedBookRepository()
                val vm = viewModel(repo)
                repo.enqueue(failure)

                vm.refresh()

                val state = vm.uiState.value
                state.book shouldBe book1
                state.error shouldBe null
                state.isRefreshing shouldBe false
                state.transientError shouldBe when (failure) {
                    BookResult.NotFound -> BookDetailError.NotFound
                    BookResult.NoServer -> BookDetailError.NoServer
                    is BookResult.Failure -> BookDetailError.Failed(failure.error)
                    is BookResult.Success -> error("not a failure")
                }

                vm.transientErrorShown()
                vm.uiState.value.transientError shouldBe null
            }
        }
    }

    describe("silent reload") {

        it("#93 TC-59: reloadBook is silent") {
            val repo = ScriptedBookRepository()
            val vm = viewModel(repo)
            val held = CompletableDeferred<BookResult>()
            repo.enqueue(held)

            vm.reloadBook()

            val pending = vm.uiState.value
            pending.isLoading shouldBe false
            pending.isRefreshing shouldBe false
            pending.reloadKey shouldBe 1

            held.complete(BookResult.Success(book2))
            vm.uiState.value.book shouldBe book2
        }

        it("#93 TC-60: silent reload failure shows nothing") {
            val repo = ScriptedBookRepository()
            val vm = viewModel(repo)
            repo.enqueue(BookResult.Failure(LibraryError.Unreachable))

            vm.reloadBook()

            val state = vm.uiState.value
            state.book shouldBe book1
            state.error shouldBe null
            state.transientError shouldBe null
        }

        it("#93 TC-61: silent reload from the error state") {
            val repo = ScriptedBookRepository()
            repo.enqueue(BookResult.NotFound)
            val vm = viewModel(repo)
            repo.enqueue(BookResult.Success(book2))

            vm.reloadBook()

            vm.uiState.value.screen shouldBe BookDetailScreenState.Content(book2)
            vm.uiState.value.error shouldBe null

            val failing = ScriptedBookRepository()
            failing.enqueue(BookResult.NotFound)
            val failingVm = viewModel(failing)
            failing.enqueue(BookResult.Failure(LibraryError.Unreachable))

            failingVm.reloadBook()

            failingVm.uiState.value.screen shouldBe
                BookDetailScreenState.Error(BookDetailError.Failed(LibraryError.Unreachable))
        }
    }

    describe("screenShown") {

        it("#93 TC-62: first screenShown is ignored") {
            val repo = ScriptedBookRepository()
            val vm = viewModel(repo)

            vm.screenShown()

            repo.bookRequests.size shouldBe 1
            vm.uiState.value.reloadKey shouldBe 0
        }

        it("#93 TC-63: later screenShown reloads silently") {
            val repo = ScriptedBookRepository()
            val vm = viewModel(repo)
            vm.screenShown() // the first call is ignored

            vm.screenShown()

            repo.bookRequests.size shouldBe 2
            vm.uiState.value.reloadKey shouldBe 1
            vm.uiState.value.isLoading shouldBe false
            vm.uiState.value.isRefreshing shouldBe false

            vm.screenShown()

            repo.bookRequests.size shouldBe 3
            vm.uiState.value.reloadKey shouldBe 2
        }
    }

    describe("reloadKey") {

        it("#93 TC-64: reloadKey bumps once per trigger") {
            val repo = ScriptedBookRepository()
            val vm = viewModel(repo)

            vm.retry()
            vm.uiState.value.reloadKey shouldBe 1

            vm.refresh()
            vm.uiState.value.reloadKey shouldBe 2

            vm.reloadBook()
            vm.uiState.value.reloadKey shouldBe 3

            vm.screenShown() // ignored: the first call
            vm.uiState.value.reloadKey shouldBe 3

            vm.screenShown()
            vm.uiState.value.reloadKey shouldBe 4
        }
    }

    describe("bookChanged") {

        it("#93 TC-65: bookChanged replaces the book") {
            val repo = ScriptedBookRepository()
            val vm = viewModel(repo)

            vm.bookChanged(book2)

            vm.uiState.value.book shouldBe book2
            repo.bookRequests.size shouldBe 1
            vm.uiState.value.reloadKey shouldBe 0
        }

        it("#93 TC-66: bookChanged with another uuid is ignored") {
            val repo = ScriptedBookRepository()
            val vm = viewModel(repo)

            vm.bookChanged(otherBook)

            vm.uiState.value.book shouldBe book1
        }

        it("#93 TC-67: bookChanged during the first load") {
            val repo = ScriptedBookRepository()
            val held = CompletableDeferred<BookResult>()
            repo.enqueue(held)
            val vm = viewModel(repo)

            vm.bookChanged(book2)

            vm.uiState.value.screen shouldBe BookDetailScreenState.Content(book2)

            held.complete(BookResult.Success(book3))
            vm.uiState.value.book shouldBe book3
        }
    }

    describe("stale results") {

        it("#93 TC-68: a superseded reload never overwrites a newer one") {
            for (completeFirstA in listOf(false, true)) {
                val repo = ScriptedBookRepository()
                val vm = viewModel(repo)
                val a = CompletableDeferred<BookResult>()
                val b = CompletableDeferred<BookResult>()
                repo.enqueue(a)
                vm.refresh()
                repo.enqueue(b)
                vm.reloadBook()

                if (completeFirstA) {
                    a.complete(BookResult.Success(book1))
                    b.complete(BookResult.Success(book2))
                } else {
                    b.complete(BookResult.Success(book2))
                    a.complete(BookResult.Success(book1))
                }

                vm.uiState.value.book shouldBe book2
            }
        }

        it("#93 TC-69: stale failure is ignored") {
            val repo = ScriptedBookRepository()
            val vm = viewModel(repo)
            val a = CompletableDeferred<BookResult>()
            repo.enqueue(a)
            vm.refresh()
            repo.enqueue(BookResult.Success(book2))
            vm.reloadBook()

            a.complete(BookResult.Failure(LibraryError.Unreachable))

            val state = vm.uiState.value
            state.transientError shouldBe null
            state.isRefreshing shouldBe false
            state.book shouldBe book2
        }
    }

    describe("several emissions") {

        it("#93 TC-70: every emission of one load is applied in order") {
            val repo = ScriptedBookRepository()
            val gate = CompletableDeferred<Unit>()
            repo.enqueueFlow {
                emit(BookResult.Success(cached))
                gate.await()
                emit(BookResult.Success(network))
            }

            val vm = viewModel(repo)

            val afterCached = vm.uiState.value
            afterCached.screen shouldBe BookDetailScreenState.Content(cached)
            afterCached.isLoading shouldBe false

            gate.complete(Unit)

            vm.uiState.value.screen shouldBe BookDetailScreenState.Content(network)
        }
    }

    describe("screen precedence") {

        it("#93 TC-71: screen precedence") {
            BookDetailUIState(book = book1, error = BookDetailError.NotFound).screen shouldBe
                BookDetailScreenState.Content(book1)
            BookDetailUIState(error = BookDetailError.NotFound).screen shouldBe
                BookDetailScreenState.Error(BookDetailError.NotFound)
            BookDetailUIState().screen shouldBe BookDetailScreenState.Loading
        }
    }
})
