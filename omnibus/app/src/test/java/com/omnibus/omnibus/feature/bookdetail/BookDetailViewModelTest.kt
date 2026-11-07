package com.omnibus.omnibus.feature.bookdetail

import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.BookResult
import com.omnibus.omnibus.data.library.LibraryError
import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.boolean
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** Every failure the repository can report for a read, with the error the page shows for it. */
private val failures = Exhaustive.of(
    BookResult.NotFound to BookDetailError.NotFound,
    BookResult.NoServer to BookDetailError.NoServer,
    BookResult.Failure(LibraryError.Unreachable) to BookDetailError.Failed(LibraryError.Unreachable),
    BookResult.Failure(LibraryError.SessionExpired) to BookDetailError.Failed(LibraryError.SessionExpired),
    BookResult.Failure(LibraryError.UnexpectedResponse) to BookDetailError.Failed(LibraryError.UnexpectedResponse),
    BookResult.Failure(LibraryError.Http(500, null)) to BookDetailError.Failed(LibraryError.Http(500, null)),
)

/** A later printing of this book, so it always differs from it. */
private fun Book.reprinted(): Book = copy(title = "$title, reprinted")

@OptIn(ExperimentalCoroutinesApi::class)
class BookDetailViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun sut(
        repo: ScriptedBookRepository = ScriptedBookRepository(),
        bookUuid: String = randomString(),
    ) = BookDetailViewModel(bookUuid, repo)

    /** A ViewModel whose first load gave [book]. */
    fun sutShowing(book: Book, repo: ScriptedBookRepository = ScriptedBookRepository()): BookDetailViewModel {
        repo.enqueue(BookResult.Success(book))
        return sut(repo, book.uuid)
    }

    /** A ViewModel whose first load found nothing. */
    fun sutOverError(repo: ScriptedBookRepository = ScriptedBookRepository()): BookDetailViewModel {
        repo.enqueue(BookResult.NotFound)
        return sut(repo)
    }

    describe("the first load") {

        context("when the load is in flight") {
            it("#93 TC-51: shows the loading view") {
                val repo = ScriptedBookRepository()
                repo.enqueue(CompletableDeferred<BookResult>())

                sut(repo).uiState.value.screen shouldBe BookDetailScreenState.Loading
            }
        }

        context("when the load succeeds") {
            it("#93 TC-52: shows the book") {
                val book = randomBook()

                sutShowing(book).uiState.value.screen shouldBe BookDetailScreenState.Content(book)
            }
        }

        context("when the load fails") {
            it("#93 TC-53: shows a full-screen error for every failure") {
                checkAll(failures) { (result, error) ->
                    val repo = ScriptedBookRepository()
                    repo.enqueue(result)

                    sut(repo).uiState.value.screen shouldBe BookDetailScreenState.Error(error)
                }
            }
        }

        context("when the load emits a cached book before the network book") {
            it("#93 TC-70: shows each in turn") {
                val cached = randomBook()
                val network = cached.reprinted()
                val gate = CompletableDeferred<Unit>()
                val repo = ScriptedBookRepository()
                repo.enqueueFlow {
                    emit(BookResult.Success(cached))
                    gate.await()
                    emit(BookResult.Success(network))
                }
                val vm = sut(repo, cached.uuid)
                vm.uiState.value.screen shouldBe BookDetailScreenState.Content(cached)

                gate.complete(Unit)

                vm.uiState.value.screen shouldBe BookDetailScreenState.Content(network)
            }
        }
    }

    describe("retrying from the error") {

        context("when the retry is in flight") {
            it("#93 TC-54: shows the loading view") {
                val repo = ScriptedBookRepository()
                val vm = sutOverError(repo)
                repo.enqueue(CompletableDeferred<BookResult>())

                vm.retry()

                vm.uiState.value.screen shouldBe BookDetailScreenState.Loading
            }
        }

        context("when the retry succeeds") {
            it("#93 TC-72: shows the book") {
                val book = randomBook()
                val repo = ScriptedBookRepository()
                val vm = sutOverError(repo)
                repo.enqueue(BookResult.Success(book))

                vm.retry()

                vm.uiState.value.screen shouldBe BookDetailScreenState.Content(book)
            }
        }
    }

    describe("pulling to refresh") {

        context("when the refresh is in flight") {
            it("#93 TC-56: spins over the book") {
                val book = randomBook()
                val repo = ScriptedBookRepository()
                val vm = sutShowing(book, repo)
                repo.enqueue(CompletableDeferred<BookResult>())

                vm.refresh()

                vm.uiState.value.isRefreshing shouldBe true
                vm.uiState.value.screen shouldBe BookDetailScreenState.Content(book)
            }
        }

        context("when the refresh succeeds") {
            it("#93 TC-57: stops spinning on the new book") {
                val book = randomBook()
                val repo = ScriptedBookRepository()
                val vm = sutShowing(book, repo)
                repo.enqueue(BookResult.Success(book.reprinted()))

                vm.refresh()

                vm.uiState.value.isRefreshing shouldBe false
                vm.uiState.value.book shouldBe book.reprinted()
            }
        }

        context("when the refresh fails") {
            it("#93 TC-58: keeps the book under a one-shot error for every failure") {
                checkAll(failures) { (result, error) ->
                    val book = randomBook()
                    val repo = ScriptedBookRepository()
                    val vm = sutShowing(book, repo)
                    repo.enqueue(result)

                    vm.refresh()

                    vm.uiState.value.transientError shouldBe error
                    vm.uiState.value.screen shouldBe BookDetailScreenState.Content(book)
                }
            }

            it("#93 TC-58: clears the error once it has been shown") {
                val repo = ScriptedBookRepository()
                val vm = sutShowing(randomBook(), repo)
                repo.enqueue(BookResult.NotFound)
                vm.refresh()

                vm.transientErrorShown()

                vm.uiState.value.transientError shouldBe null
            }
        }
    }

    describe("reloading silently") {

        context("when a book is on screen") {
            context("when the reload is in flight") {
                it("#93 TC-59: shows no progress") {
                    val repo = ScriptedBookRepository()
                    val vm = sutShowing(randomBook(), repo)
                    repo.enqueue(CompletableDeferred<BookResult>())

                    vm.reloadBook()

                    vm.uiState.value.isLoading shouldBe false
                    vm.uiState.value.isRefreshing shouldBe false
                }
            }

            context("when the reload fails") {
                it("#93 TC-60: shows no error") {
                    val book = randomBook()
                    val repo = ScriptedBookRepository()
                    val vm = sutShowing(book, repo)
                    repo.enqueue(BookResult.Failure(LibraryError.Unreachable))

                    vm.reloadBook()

                    vm.uiState.value.transientError shouldBe null
                    vm.uiState.value.screen shouldBe BookDetailScreenState.Content(book)
                }
            }
        }

        context("when the error is on screen") {
            context("when the reload fails") {
                it("#93 TC-61: shows the new error") {
                    val repo = ScriptedBookRepository()
                    val vm = sutOverError(repo)
                    repo.enqueue(BookResult.Failure(LibraryError.Unreachable))

                    vm.reloadBook()

                    vm.uiState.value.screen shouldBe
                        BookDetailScreenState.Error(BookDetailError.Failed(LibraryError.Unreachable))
                }
            }
        }
    }

    describe("returning to the screen") {

        context("when the screen is shown for the first time") {
            it("#93 TC-62: makes no further request") {
                val repo = ScriptedBookRepository()
                val vm = sut(repo)

                vm.screenShown()

                repo.bookRequests.size shouldBe 1
            }
        }

        context("when the screen is shown again") {
            it("#93 TC-63: reloads the book") {
                val repo = ScriptedBookRepository()
                val vm = sut(repo)
                vm.screenShown()

                vm.screenShown()

                repo.bookRequests.size shouldBe 2
            }
        }
    }

    describe("the reload key sections follow") {

        it("#93 TC-64: rises once for each reload") {
            val vm = sut()
            vm.screenShown()

            vm.retry()
            vm.refresh()
            vm.reloadBook()
            vm.screenShown()

            vm.uiState.value.reloadKey shouldBe 4
        }
    }

    describe("a section changing the book") {

        context("when the change is for this book") {
            it("#93 TC-65: shows it without a reload") {
                val book = randomBook()
                val repo = ScriptedBookRepository()
                val vm = sutShowing(book, repo)

                vm.bookChanged(book.reprinted())

                vm.uiState.value.book shouldBe book.reprinted()
                repo.bookRequests.size shouldBe 1
            }
        }

        context("when the change is for another book") {
            it("#93 TC-66: ignores it") {
                val book = randomBook()
                val vm = sutShowing(book)

                vm.bookChanged(book.copy(uniqueIdentifier = "${book.uuid}-other"))

                vm.uiState.value.book shouldBe book
            }
        }
    }

    describe("a load another load has replaced") {

        it("#93 TC-68: never overwrites the newer book, whichever finishes first") {
            checkAll(Exhaustive.boolean()) { olderFinishesFirst ->
                val book = randomBook()
                val repo = ScriptedBookRepository()
                val vm = sutShowing(book, repo)
                val older = CompletableDeferred<BookResult>()
                val newer = CompletableDeferred<BookResult>()
                repo.enqueue(older)
                vm.refresh()
                repo.enqueue(newer)
                vm.reloadBook()

                val inOrder = listOf(older to book, newer to book.reprinted())
                (if (olderFinishesFirst) inOrder else inOrder.reversed()).forEach { (load, result) ->
                    load.complete(BookResult.Success(result))
                }

                vm.uiState.value.book shouldBe book.reprinted()
            }
        }
    }
})
