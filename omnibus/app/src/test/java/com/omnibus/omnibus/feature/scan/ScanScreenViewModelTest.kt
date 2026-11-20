package com.omnibus.omnibus.feature.scan

import app.cash.turbine.test
import com.omnibus.omnibus.data.scan.IsbnRejection
import com.omnibus.omnibus.data.scan.ScanBook
import com.omnibus.omnibus.data.scan.ScanError
import com.omnibus.omnibus.data.scan.ScanOutcome
import com.omnibus.omnibus.data.scan.ScanResult
import com.omnibus.omnibus.data.scan.TestScanRepository
import com.omnibus.omnibus.data.scan.randomScanBook
import com.omnibus.omnibus.data.scan.randomValidIsbn10
import com.omnibus.omnibus.data.scan.randomValidIsbn13
import com.omnibus.omnibus.testing.TestTimeSource
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** Makes the screen show [outcome] for a valid barcode read by the camera. */
private fun TestScope.openOutcome(
    viewModel: ScanScreenViewModel,
    repository: TestScanRepository,
    outcome: ScanOutcome,
    isbn: String = randomValidIsbn13(),
) {
    repository.resolveAnswers.addLast(ScanResult.Success(outcome))
    viewModel.barcodesSeen(listOf(isbn))
    runCurrent()
}

/** The success stage a check-in of [book] answered with [uuid] should show. */
private fun successFor(book: ScanBook, uuid: String, kind: SuccessKind) =
    ScanStage.Success(book.title, SuccessCover.Library(book), uuid, kind)

@OptIn(ExperimentalCoroutinesApi::class)
class ScanScreenViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(StandardTestDispatcher()) }

    afterSpec { Dispatchers.resetMain() }

    fun sut(
        repository: TestScanRepository = TestScanRepository(),
        time: TestTimeSource = TestTimeSource(randomLong()),
    ): ScanScreenViewModel = ScanScreenViewModel(repository, time)

    describe("reading a barcode") {

        it("#317 TC-12: a valid read starts the lookup with a haptic") {
            runTest {
                val isbn = randomValidIsbn13()
                val repository = TestScanRepository().apply { resolveGate = CompletableDeferred() }
                val viewModel = sut(repository)

                viewModel.effects.test {
                    viewModel.barcodesSeen(listOf(isbn))
                    runCurrent()

                    viewModel.uiState.value.stage shouldBe ScanStage.Resolving(isbn)
                    repository.resolveCalls shouldBe listOf(isbn)
                    awaitItem() shouldBe ScanEffect.Confirm
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }
    }

    describe("showing a barcode that isn't a book's ISBN") {

        it("#317 TC-13: keeps the notice until 3 s have passed, then clears it") {
            runTest {
                val viewModel = sut()
                viewModel.barcodesSeen(listOf("9780000000000"))
                runCurrent()

                advanceTimeBy(2_999)
                runCurrent()
                (viewModel.uiState.value.stage as ScanStage.Viewfinder).rejected shouldBe
                    RejectedNotice("9780000000000", announce = true)

                advanceTimeBy(1)
                runCurrent()
                (viewModel.uiState.value.stage as ScanStage.Viewfinder).rejected shouldBe null
            }
        }

        it("#317 TC-13: clears the notice as soon as a valid read starts a lookup") {
            runTest {
                val isbn = randomValidIsbn13()
                val repository = TestScanRepository().apply { resolveGate = CompletableDeferred() }
                val viewModel = sut(repository)
                viewModel.barcodesSeen(listOf("9780000000000"))
                runCurrent()

                viewModel.barcodesSeen(listOf(isbn))
                runCurrent()

                viewModel.uiState.value.stage shouldBe ScanStage.Resolving(isbn)
            }
        }
    }

    describe("entering an ISBN by hand") {

        it("#317 TC-16: shows no error under 10 cleaned characters") {
            val viewModel = sut()
            viewModel.enterIsbnTapped()

            viewModel.isbnChanged("0-306-406")

            (viewModel.uiState.value.stage as ScanStage.Manual).shownRejection shouldBe null
        }

        it("#317 TC-16: shows the rejection from 10 cleaned characters on") {
            val viewModel = sut()
            viewModel.enterIsbnTapped()

            viewModel.isbnChanged("0306406150")

            (viewModel.uiState.value.stage as ScanStage.Manual).shownRejection shouldBe IsbnRejection.BadCheckDigit
        }

        it("#317 TC-16: shows the rejection after Find when the ISBN is too short") {
            val viewModel = sut()
            viewModel.enterIsbnTapped()
            viewModel.isbnChanged("123")

            viewModel.findBookTapped()

            (viewModel.uiState.value.stage as ScanStage.Manual).shownRejection shouldBe IsbnRejection.WrongLength
        }

        it("#317 TC-16: allows Find only for a valid ISBN") {
            val viewModel = sut()
            viewModel.enterIsbnTapped()
            viewModel.isbnChanged("0-306-40615-2")

            (viewModel.uiState.value.stage as ScanStage.Manual).canFind shouldBe true
        }

        it("#317 TC-16: Find looks up the cleaned ISBN as entered by hand") {
            runTest {
                val repository = TestScanRepository().apply {
                    resolveAnswers.addLast(ScanResult.Failure(ScanError.Unreachable))
                }
                val viewModel = sut(repository)
                viewModel.enterIsbnTapped()
                viewModel.isbnChanged("0-306-40615-2")

                viewModel.findBookTapped()
                runCurrent()

                repository.resolveCalls shouldBe listOf("0306406152")
                viewModel.uiState.value.stage shouldBe
                    ScanStage.Viewfinder(lookupFailed = LookupFailure("0306406152", FoundVia.Manual))
            }
        }

        it("#317 TC-16: Scan instead returns to the camera") {
            val viewModel = sut()
            viewModel.enterIsbnTapped()

            viewModel.scanInsteadTapped()

            viewModel.uiState.value.stage shouldBe ScanStage.Viewfinder()
        }
    }

    describe("looking a book up") {

        context("when the lookup fails") {

            it("#317 TC-18: any lookup failure shows the banner, never an outcome") {
                runTest {
                    checkAll(
                        Exhaustive.of(
                            ScanResult.NoServer,
                            ScanResult.Failure(ScanError.ProvidersUnreachable),
                            ScanResult.Failure(ScanError.Rejected),
                            ScanResult.Failure(ScanError.SessionExpired),
                            ScanResult.Failure(ScanError.NotFound),
                            ScanResult.Failure(ScanError.Http(500)),
                            ScanResult.Failure(ScanError.Unreachable),
                            ScanResult.Failure(ScanError.UnexpectedResponse),
                        ),
                    ) { answer ->
                        val isbn = randomValidIsbn13()
                        val repository = TestScanRepository().apply { resolveAnswers.addLast(answer) }
                        val viewModel = sut(repository)

                        viewModel.barcodesSeen(listOf(isbn))
                        runCurrent()

                        viewModel.uiState.value.stage shouldBe
                            ScanStage.Viewfinder(lookupFailed = LookupFailure(isbn, FoundVia.Scan))
                    }
                }
            }

            it("#317 TC-19: Try again repeats a failed scan lookup with the same ISBN") {
                runTest {
                    val isbn = randomValidIsbn13()
                    val repository = TestScanRepository().apply {
                        resolveAnswers.addLast(ScanResult.Failure(ScanError.Unreachable))
                    }
                    val viewModel = sut(repository)
                    viewModel.barcodesSeen(listOf(isbn))
                    runCurrent()
                    repository.resolveGate = CompletableDeferred()

                    viewModel.tryAgainTapped()
                    runCurrent()

                    repository.resolveCalls shouldBe listOf(isbn, isbn)
                    viewModel.uiState.value.stage shouldBe ScanStage.Resolving(isbn)
                }
            }

            it("#317 TC-19: Try again repeats a failed typed lookup with the same ISBN") {
                runTest {
                    val repository = TestScanRepository().apply {
                        resolveAnswers.addLast(ScanResult.Failure(ScanError.Unreachable))
                    }
                    val viewModel = sut(repository)
                    viewModel.enterIsbnTapped()
                    viewModel.isbnChanged("0306406152")
                    viewModel.findBookTapped()
                    runCurrent()
                    repository.resolveGate = CompletableDeferred()

                    viewModel.tryAgainTapped()
                    runCurrent()

                    repository.resolveCalls shouldBe listOf("0306406152", "0306406152")
                    viewModel.uiState.value.stage shouldBe ScanStage.Resolving("0306406152")
                }
            }
        }

        context("when the lookup succeeds") {

            it("#317 TC-24: an answer opens its outcome with an empty note") {
                runTest {
                    checkAll(
                        Exhaustive.of(
                            ScanOutcome.InLibraryUnowned(randomScanBook()),
                            ScanOutcome.OnWishlist(randomScanBook()),
                            ScanOutcome.AlreadyOwned(randomScanBook()),
                            ScanOutcome.Unplaced,
                        ),
                    ) { outcome ->
                        val isbn = randomValidIsbn13()
                        val repository = TestScanRepository()
                        val viewModel = sut(repository)

                        openOutcome(viewModel, repository, outcome, isbn)

                        viewModel.uiState.value.stage shouldBe ScanStage.Outcome(outcome, isbn, FoundVia.Scan)
                    }
                }
            }
        }
    }

    describe("checking a copy in") {

        it("#317 TC-26: a double tap writes once") {
            runTest {
                val repository = TestScanRepository()
                val viewModel = sut(repository)
                openOutcome(viewModel, repository, ScanOutcome.InLibraryUnowned(randomScanBook()))
                repository.checkInGate = CompletableDeferred()

                viewModel.checkInTapped()
                viewModel.checkInTapped()
                runCurrent()

                repository.checkInCalls.size shouldBe 1
            }
        }

        it("#317 TC-26: nothing else acts while the save is in flight") {
            runTest {
                val repository = TestScanRepository()
                val viewModel = sut(repository)
                openOutcome(viewModel, repository, ScanOutcome.InLibraryUnowned(randomScanBook()))
                repository.checkInGate = CompletableDeferred()
                viewModel.checkInTapped()
                runCurrent()
                val saving = viewModel.uiState.value.stage

                viewModel.scanAnotherTapped()
                viewModel.backTapped()
                viewModel.noteChanged(randomString())
                runCurrent()

                viewModel.uiState.value.stage shouldBe saving
            }
        }

        it("#317 TC-27: a failed save keeps the outcome and shows the failure") {
            runTest {
                val isbn = randomValidIsbn13()
                val outcome = ScanOutcome.InLibraryUnowned(randomScanBook())
                val repository = TestScanRepository()
                val viewModel = sut(repository)
                openOutcome(viewModel, repository, outcome, isbn)
                repository.checkInAnswers.addLast(ScanResult.Failure(ScanError.Unreachable))

                viewModel.checkInTapped()
                runCurrent()

                viewModel.uiState.value.stage shouldBe
                    ScanStage.Outcome(outcome, isbn, FoundVia.Scan, writing = null, writeFailed = true)
            }
        }

        it("#317 TC-27: a failed save can be retried, which calls check-in again") {
            runTest {
                val repository = TestScanRepository()
                val viewModel = sut(repository)
                openOutcome(viewModel, repository, ScanOutcome.InLibraryUnowned(randomScanBook()))
                repository.checkInAnswers.addLast(ScanResult.Failure(ScanError.Unreachable))
                viewModel.checkInTapped()
                runCurrent()
                repository.checkInGate = CompletableDeferred()

                viewModel.checkInTapped()
                runCurrent()

                repository.checkInCalls.size shouldBe 2
            }
        }

        it("#317 TC-28: files the copy under the ISBN in hand, not the book's own") {
            runTest {
                val typed = randomValidIsbn10()
                val book = randomScanBook().copy(isbn = randomValidIsbn13())
                val repository = TestScanRepository().apply {
                    resolveAnswers.addLast(ScanResult.Success(ScanOutcome.InLibraryUnowned(book)))
                }
                val viewModel = sut(repository)
                viewModel.enterIsbnTapped()
                viewModel.isbnChanged(typed)
                viewModel.findBookTapped()
                runCurrent()
                repository.checkInAnswers.addLast(ScanResult.Failure(ScanError.Unreachable))

                viewModel.checkInTapped()
                runCurrent()

                repository.checkInCalls.single().isbn shouldBe typed
            }
        }

        it("#317 TC-28: sends no note when the note is blank") {
            runTest {
                checkAll(Exhaustive.collection(listOf("", "   ", "\t"))) { blank ->
                    val repository = TestScanRepository().apply {
                        checkInAnswers.addLast(ScanResult.Failure(ScanError.Unreachable))
                    }
                    val viewModel = sut(repository)
                    openOutcome(viewModel, repository, ScanOutcome.InLibraryUnowned(randomScanBook()))
                    viewModel.noteChanged(blank)

                    viewModel.checkInTapped()
                    runCurrent()

                    repository.checkInCalls.single().note shouldBe null
                }
            }
        }

        it("#317 TC-28: sends the trimmed note when it isn't blank") {
            runTest {
                val text = randomString()
                val repository = TestScanRepository().apply {
                    checkInAnswers.addLast(ScanResult.Failure(ScanError.Unreachable))
                }
                val viewModel = sut(repository)
                openOutcome(viewModel, repository, ScanOutcome.InLibraryUnowned(randomScanBook()))
                viewModel.noteChanged("  $text  ")

                viewModel.checkInTapped()
                runCurrent()

                repository.checkInCalls.single().note shouldBe text
            }
        }

        it("#317 TC-30: a check-in from an on-wishlist book ends in the off-wishlist success") {
            runTest {
                val book = randomScanBook()
                val repository = TestScanRepository().apply {
                    checkInAnswers.addLast(ScanResult.Success("u-new"))
                }
                val viewModel = sut(repository)
                openOutcome(viewModel, repository, ScanOutcome.OnWishlist(book))

                viewModel.checkInTapped()
                runCurrent()

                viewModel.uiState.value.stage shouldBe successFor(book, "u-new", SuccessKind.OffWishlist)
            }
        }

        it("#317 TC-30: a check-in from an in-library book ends in the checked-in success") {
            runTest {
                val book = randomScanBook()
                val repository = TestScanRepository().apply {
                    checkInAnswers.addLast(ScanResult.Success("u-new"))
                }
                val viewModel = sut(repository)
                openOutcome(viewModel, repository, ScanOutcome.InLibraryUnowned(book))

                viewModel.checkInTapped()
                runCurrent()

                viewModel.uiState.value.stage shouldBe successFor(book, "u-new", SuccessKind.CheckedIn)
            }
        }

        it("#317 TC-30: a check-in emits one success haptic") {
            runTest {
                val repository = TestScanRepository().apply {
                    checkInAnswers.addLast(ScanResult.Success("u-new"))
                }
                val viewModel = sut(repository)
                openOutcome(viewModel, repository, ScanOutcome.InLibraryUnowned(randomScanBook()))

                viewModel.effects.test {
                    awaitItem() shouldBe ScanEffect.Confirm // the camera read's haptic, sent before the collector started
                    viewModel.checkInTapped()
                    runCurrent()

                    awaitItem() shouldBe ScanEffect.Confirm
                    expectNoEvents()
                }
            }
        }
    }

    describe("leaving an outcome or a success") {

        it("#317 TC-35: Scan another returns to a fresh viewfinder from an outcome") {
            runTest {
                val repository = TestScanRepository()
                val viewModel = sut(repository)
                openOutcome(viewModel, repository, ScanOutcome.AlreadyOwned(randomScanBook()))

                viewModel.scanAnotherTapped()

                viewModel.uiState.value.stage shouldBe ScanStage.Viewfinder()
            }
        }

        it("#317 TC-35: back returns to a fresh viewfinder from an outcome") {
            runTest {
                val repository = TestScanRepository()
                val viewModel = sut(repository)
                openOutcome(viewModel, repository, ScanOutcome.AlreadyOwned(randomScanBook()))

                viewModel.backTapped()

                viewModel.uiState.value.stage shouldBe ScanStage.Viewfinder()
            }
        }

        it("#317 TC-35: Scan another returns to a fresh viewfinder from a success") {
            runTest {
                val repository = TestScanRepository().apply {
                    checkInAnswers.addLast(ScanResult.Success("u-new"))
                }
                val viewModel = sut(repository)
                openOutcome(viewModel, repository, ScanOutcome.InLibraryUnowned(randomScanBook()))
                viewModel.checkInTapped()
                runCurrent()

                viewModel.scanAnotherTapped()

                viewModel.uiState.value.stage shouldBe ScanStage.Viewfinder()
            }
        }

        it("#317 TC-35: back returns to a fresh viewfinder from a success") {
            runTest {
                val repository = TestScanRepository().apply {
                    checkInAnswers.addLast(ScanResult.Success("u-new"))
                }
                val viewModel = sut(repository)
                openOutcome(viewModel, repository, ScanOutcome.InLibraryUnowned(randomScanBook()))
                viewModel.checkInTapped()
                runCurrent()

                viewModel.backTapped()

                viewModel.uiState.value.stage shouldBe ScanStage.Viewfinder()
            }
        }
    }

    describe("answers that arrive after the reader moved on") {

        it("#317 TC-36: ignores a lookup answer that arrives after Scan another") {
            runTest {
                val gate = CompletableDeferred<Unit>()
                val repository = TestScanRepository().apply { resolveGate = gate }
                val viewModel = sut(repository)
                viewModel.barcodesSeen(listOf(randomValidIsbn13()))
                runCurrent()

                viewModel.scanAnotherTapped()
                repository.resolveAnswers.addLast(ScanResult.Success(ScanOutcome.Unplaced))
                gate.complete(Unit)
                runCurrent()

                viewModel.uiState.value.stage shouldBe ScanStage.Viewfinder()
            }
        }

        it("#317 TC-36: ignores a lookup answer that arrives after a newer lookup started") {
            runTest {
                val gate = CompletableDeferred<Unit>()
                val stale = randomValidIsbn13()
                val fresh = randomValidIsbn10()
                val repository = TestScanRepository().apply { resolveGate = gate }
                val viewModel = sut(repository)
                viewModel.barcodesSeen(listOf(stale))
                runCurrent()

                repository.resolveGate = null
                repository.resolveAnswers.addLast(ScanResult.Success(ScanOutcome.Unplaced))
                viewModel.enterIsbnTapped()
                viewModel.isbnChanged(fresh)
                viewModel.findBookTapped()
                runCurrent()
                val current = viewModel.uiState.value.stage
                repository.resolveAnswers.addLast(ScanResult.Success(ScanOutcome.AlreadyOwned(randomScanBook())))
                gate.complete(Unit)
                runCurrent()

                viewModel.uiState.value.stage shouldBe current
            }
        }
    }
})
