package com.omnibus.omnibus.feature.series

import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.data.series.SeriesDetails
import com.omnibus.omnibus.data.series.SeriesDetailsResult
import com.omnibus.omnibus.data.series.SeriesError
import com.omnibus.omnibus.data.series.TestSeriesRepository
import com.omnibus.omnibus.testing.randomLong
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

/** A successful read of [books] under the series name [name]. */
private fun succeeds(vararg books: Book, name: String = randomString()): SeriesDetailsResult =
    SeriesDetailsResult.Success(SeriesDetails(name, books.toList()))

/** A failed read with [error]. */
private fun fails(error: SeriesError): SeriesDetailsResult = SeriesDetailsResult.Failure(error)

/** A book whose series number is [index]. */
private fun bookNumbered(index: String?): Book = randomBook().copy(seriesIndex = index)

@OptIn(ExperimentalCoroutinesApi::class)
class SeriesDetailsViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun sut(
        repository: TestSeriesRepository = TestSeriesRepository(),
        seriesId: Long = randomLong(),
        name: String = randomString(),
    ) = SeriesDetailsViewModel(seriesId = seriesId, name = name, seriesRepository = repository)

    describe("opening the page") {

        it("#349 TC-12: shows the route's name until the server answers") {
            val repository = TestSeriesRepository()
            val gate = CompletableDeferred<Unit>()
            val routeName = randomString()
            repository.detailsResults += succeeds(randomBook(), name = randomString())
            repository.detailsGates += gate

            val viewModel = sut(repository, name = routeName)

            viewModel.uiState.value.name shouldBe routeName
            gate.complete(Unit)
        }

        it("#349 TC-12: shows the server's name once it answers") {
            val repository = TestSeriesRepository()
            val serverName = randomString()
            repository.detailsResults += succeeds(randomBook(), name = serverName)

            val viewModel = sut(repository, name = randomString())

            viewModel.uiState.value.name shouldBe serverName
        }

        it("#349 TC-12: reads the series once without an intent") {
            val repository = TestSeriesRepository()
            val seriesId = randomLong()
            repository.detailsResults += succeeds(randomBook())

            sut(repository, seriesId = seriesId)

            repository.detailsCalls shouldBe listOf(seriesId)
        }
    }

    describe("a loaded series") {

        it("#349 TC-13: keeps the server's books as given, in the server's order") {
            val repository = TestSeriesRepository()
            val books = listOf(bookNumbered("3"), bookNumbered("1"), bookNumbered("1"))
            repository.detailsResults += succeeds(*books.toTypedArray())

            sut(repository).uiState.value.books shouldBe books
        }

        it("#349 TC-13: keeps the placeholders out of the books") {
            val repository = TestSeriesRepository()
            val books = listOf(bookNumbered("1"), bookNumbered("6"))
            repository.detailsResults += succeeds(*books.toTypedArray())

            val state = sut(repository).uiState.value

            state.entries.count { it is SeriesEntry.Missing } shouldBe 1
            state.books shouldBe books
        }

        context("when the series has books") {

            it("#349 TC-13: is Loaded") {
                val repository = TestSeriesRepository()
                repository.detailsResults += succeeds(randomBook())

                sut(repository).uiState.value.screen shouldBe SeriesDetailsScreenState.Loaded
            }
        }

        context("when the series has no books") {

            it("#349 TC-13: is Empty") {
                val repository = TestSeriesRepository()
                repository.detailsResults += succeeds()

                sut(repository).uiState.value.screen shouldBe SeriesDetailsScreenState.Empty
            }
        }
    }

    describe("a failed refresh") {

        context("when the server cannot be reached and books are on screen") {

            it("#349 TC-14: keeps the books and reports Unreachable") {
                val repository = TestSeriesRepository()
                val books = listOf(randomBook(), randomBook())
                repository.detailsResults += succeeds(*books.toTypedArray())
                repository.detailsResults += fails(SeriesError.Unreachable)
                val viewModel = sut(repository)

                viewModel.refresh()

                viewModel.uiState.value.books shouldBe books
                viewModel.uiState.value.refreshFailure shouldBe SeriesRefreshFailure.Unreachable
            }

            it("#349 TC-14: clears the failure once the snackbar has shown it") {
                val repository = TestSeriesRepository()
                repository.detailsResults += succeeds(randomBook())
                repository.detailsResults += fails(SeriesError.Unreachable)
                val viewModel = sut(repository)
                viewModel.refresh()

                viewModel.refreshFailureShown()

                viewModel.uiState.value.refreshFailure.shouldBeNull()
            }

            it("#349 TC-14: a retry from the snackbar reads the series again") {
                val repository = TestSeriesRepository()
                repository.detailsResults += succeeds(randomBook())
                repository.detailsResults += fails(SeriesError.Unreachable)
                repository.detailsResults += succeeds(randomBook())
                val viewModel = sut(repository)
                viewModel.refresh()

                viewModel.refresh()

                repository.detailsCalls.size shouldBe 3
                viewModel.uiState.value.screen shouldBe SeriesDetailsScreenState.Loaded
            }
        }

        context("when any other failure happens and books are on screen") {

            it("#349 TC-14: keeps the books and reports WentWrong") {
                val repository = TestSeriesRepository()
                val books = listOf(randomBook())
                repository.detailsResults += succeeds(*books.toTypedArray())
                repository.detailsResults += fails(SeriesError.Http(500))
                val viewModel = sut(repository)

                viewModel.refresh()

                viewModel.uiState.value.books shouldBe books
                viewModel.uiState.value.refreshFailure shouldBe SeriesRefreshFailure.WentWrong
            }
        }
    }

    describe("a series that is gone") {

        context("when a refresh finds it gone with books on screen") {

            it("#349 TC-15: is Not found and the books are gone") {
                val repository = TestSeriesRepository()
                repository.detailsResults += succeeds(randomBook())
                repository.detailsResults += fails(SeriesError.NotFound)
                val viewModel = sut(repository)

                viewModel.refresh()

                viewModel.uiState.value.screen shouldBe SeriesDetailsScreenState.NotFound
                viewModel.uiState.value.books.shouldBeNull()
            }
        }

        context("when the first load finds it gone") {

            it("#349 TC-15: is Not found with no books") {
                val repository = TestSeriesRepository()
                repository.detailsResults += fails(SeriesError.NotFound)

                val viewModel = sut(repository)

                viewModel.uiState.value.screen shouldBe SeriesDetailsScreenState.NotFound
                viewModel.uiState.value.books.shouldBeNull()
            }
        }
    }

    describe("a load that fails with nothing on screen") {

        context("when the server cannot be reached") {

            it("#349 TC-16: shows Can't reach your server") {
                val repository = TestSeriesRepository()
                repository.detailsResults += fails(SeriesError.Unreachable)

                sut(repository).uiState.value.screen shouldBe SeriesDetailsScreenState.Unreachable
            }
        }

        context("when the answer is an error or unexpected") {

            it("#349 TC-16: shows Something went wrong") {
                checkAll(Exhaustive.of(SeriesError.UnexpectedResponse, SeriesError.Http(500))) { error ->
                    val repository = TestSeriesRepository()
                    repository.detailsResults += fails(error)

                    sut(repository).uiState.value.screen shouldBe SeriesDetailsScreenState.WentWrong
                }
            }
        }

        context("when the first load has failed") {

            it("#349 TC-16: a retry reads again and reaches Loaded") {
                val repository = TestSeriesRepository()
                repository.detailsResults += fails(SeriesError.Unreachable)
                repository.detailsResults += succeeds(randomBook())
                val viewModel = sut(repository)

                viewModel.retry()

                repository.detailsCalls.size shouldBe 2
                viewModel.uiState.value.screen shouldBe SeriesDetailsScreenState.Loaded
            }
        }
    }

    describe("an expired session") {

        context("when the session has expired") {

            it("#349 TC-17: shows the session expired state") {
                val repository = TestSeriesRepository()
                repository.detailsResults += fails(SeriesError.SessionExpired)

                sut(repository).uiState.value.screen shouldBe SeriesDetailsScreenState.SessionExpired
            }
        }

        context("when no server is saved") {

            it("#349 TC-17: shows the session expired state") {
                val repository = TestSeriesRepository()
                repository.detailsResults += SeriesDetailsResult.NoServer

                sut(repository).uiState.value.screen shouldBe SeriesDetailsScreenState.SessionExpired
            }
        }
    }

    describe("overlapping loads") {

        it("#349 TC-18: a slow earlier read never overwrites a newer one") {
            val repository = TestSeriesRepository()
            val slow = CompletableDeferred<Unit>()
            val stale = List(4) { randomBook() }
            val fresh = List(2) { randomBook() }
            repository.detailsResults += succeeds(*stale.toTypedArray())
            repository.detailsGates += slow
            repository.detailsResults += succeeds(*fresh.toTypedArray())
            val viewModel = sut(repository)

            viewModel.refresh()
            slow.complete(Unit)

            viewModel.uiState.value.books shouldBe fresh
        }
    }
})
