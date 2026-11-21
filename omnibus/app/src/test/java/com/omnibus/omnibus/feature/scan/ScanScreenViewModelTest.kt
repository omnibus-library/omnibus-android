package com.omnibus.omnibus.feature.scan

import app.cash.turbine.test
import com.omnibus.omnibus.data.scan.IsbnRejection
import com.omnibus.omnibus.data.scan.ProviderRecord
import com.omnibus.omnibus.data.scan.ScanBook
import com.omnibus.omnibus.data.scan.ScanError
import com.omnibus.omnibus.data.scan.ScanOutcome
import com.omnibus.omnibus.data.scan.ScanResult
import com.omnibus.omnibus.data.scan.TestScanRepository
import com.omnibus.omnibus.data.requests.randomLibraryHit
import com.omnibus.omnibus.data.search.LibraryBookMatches
import com.omnibus.omnibus.data.search.LibraryHit
import com.omnibus.omnibus.data.search.LibrarySearchResult
import com.omnibus.omnibus.data.search.TestLibrarySearchRepository
import com.omnibus.omnibus.data.wishlist.TestWishlistRepository
import com.omnibus.omnibus.data.wishlist.WishlistState
import com.omnibus.omnibus.data.wishlist.randomWishlistEntry
import com.omnibus.omnibus.data.scan.randomProviderRecord
import com.omnibus.omnibus.data.scan.randomScanBook
import com.omnibus.omnibus.data.scan.randomValidIsbn10
import com.omnibus.omnibus.data.scan.randomValidIsbn13
import com.omnibus.omnibus.data.wishlist.WishlistSource
import com.omnibus.omnibus.testing.TestTimeSource
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import io.kotest.property.exhaustive.enum
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

/** How long the library picker waits after a keystroke before it searches. The picker's own debounce. */
private const val PICKER_DEBOUNCE = 250L

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

/** Makes the screen show [outcome] for a typed ISBN, or for a camera read, as [via] says. */
private fun TestScope.openProviderOutcome(
    viewModel: ScanScreenViewModel,
    repository: TestScanRepository,
    record: ProviderRecord,
    via: FoundVia,
) {
    val outcome = ScanOutcome.NotInLibrary(record)
    when (via) {
        FoundVia.Scan -> openOutcome(viewModel, repository, outcome)
        FoundVia.Manual -> {
            repository.resolveAnswers.addLast(ScanResult.Success(outcome))
            viewModel.enterIsbnTapped()
            viewModel.isbnChanged(record.isbn13)
            viewModel.findBookTapped()
            runCurrent()
        }
        FoundVia.Search -> openVia(viewModel, repository, outcome, via, record.isbn13, record)
    }
}

/**
 * Makes the screen show [outcome] as [via] found it: a camera read, a typed ISBN, or a title-search pick of [record]. The
 * ISBN in hand is [isbn] for a camera read or a typed ISBN, and the record's own for a pick.
 */
private fun TestScope.openVia(
    viewModel: ScanScreenViewModel,
    repository: TestScanRepository,
    outcome: ScanOutcome,
    via: FoundVia,
    isbn: String,
    record: ProviderRecord,
) {
    when (via) {
        FoundVia.Scan -> openOutcome(viewModel, repository, outcome, isbn)
        FoundVia.Manual -> {
            repository.resolveAnswers.addLast(ScanResult.Success(outcome))
            viewModel.enterIsbnTapped()
            viewModel.isbnChanged(isbn)
            viewModel.findBookTapped()
            runCurrent()
        }
        FoundVia.Search -> {
            repository.searchAnswers.addLast(ScanResult.Success(listOf(record)))
            viewModel.searchByTitleTapped()
            viewModel.titleQueryChanged(record.title)
            viewModel.titleSearchSubmitted()
            runCurrent()
            repository.resolveRecordAnswers.addLast(ScanResult.Success(outcome))
            viewModel.searchResultPicked(record)
            runCurrent()
        }
    }
}

/** Runs the add-a-book action [action] on [viewModel]. */
private fun ScanScreenViewModel.add(action: WritingAction) = when (action) {
    WritingAction.CheckIn -> checkInTapped()
    WritingAction.Physical -> addPhysicalTapped()
    WritingAction.Wishlist -> addToWishlistTapped()
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
        wishlist: TestWishlistRepository = TestWishlistRepository(),
        librarySearch: TestLibrarySearchRepository = TestLibrarySearchRepository(),
    ): ScanScreenViewModel = ScanScreenViewModel(repository, time, wishlist, librarySearch)

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
                            ScanOutcome.Unresolved,
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

    describe("adding a book the library doesn't have") {

        it("#336 TC-48: adding ends in the right success") {
            runTest {
                checkAll(Exhaustive.collection(FoundVia.entries)) { via ->
                    val record = randomProviderRecord()
                    val physical = TestScanRepository().apply { physicalOnlyAnswers.addLast(ScanResult.Success("u-new")) }
                    val physicalViewModel = sut(physical)
                    openProviderOutcome(physicalViewModel, physical, record, via)

                    physicalViewModel.addPhysicalTapped()
                    runCurrent()

                    physicalViewModel.uiState.value.stage shouldBe
                        ScanStage.Success(record.title, SuccessCover.Provider(record), "u-new", SuccessKind.CheckedIn)

                    val wishlist = TestScanRepository().apply { wishlistAnswers.addLast(ScanResult.Success("u-new")) }
                    val wishlistViewModel = sut(wishlist)
                    openProviderOutcome(wishlistViewModel, wishlist, record, via)

                    wishlistViewModel.addToWishlistTapped()
                    runCurrent()

                    wishlistViewModel.uiState.value.stage shouldBe
                        ScanStage.Success(record.title, SuccessCover.Provider(record), "u-new", SuccessKind.Wishlisted)
                    wishlist.wishlistCalls.single().source shouldBe
                        when (via) {
                            FoundVia.Scan -> WishlistSource.Scan
                            FoundVia.Manual -> WishlistSource.Manual
                            FoundVia.Search -> WishlistSource.Search
                        }
                }
            }
        }

        it("#336 TC-49: each add writes once and can be retried") {
            runTest {
                checkAll(Exhaustive.of(WritingAction.Physical, WritingAction.Wishlist)) { action ->
                    val other = if (action == WritingAction.Physical) WritingAction.Wishlist else WritingAction.Physical
                    val repository = TestScanRepository().apply {
                        physicalOnlyGate = CompletableDeferred()
                        wishlistGate = CompletableDeferred()
                        physicalOnlyAnswers.addLast(ScanResult.Failure(ScanError.Unreachable))
                        wishlistAnswers.addLast(ScanResult.Failure(ScanError.Unreachable))
                    }
                    val viewModel = sut(repository)
                    openProviderOutcome(viewModel, repository, randomProviderRecord(), FoundVia.Scan)

                    viewModel.add(action)
                    viewModel.add(action)
                    viewModel.add(other)
                    viewModel.scanAnotherTapped()
                    runCurrent()

                    repository.physicalOnlyCalls.size + repository.wishlistCalls.size shouldBe 1
                    (viewModel.uiState.value.stage as ScanStage.Outcome).writing shouldBe action

                    repository.physicalOnlyGate?.complete(Unit)
                    repository.wishlistGate?.complete(Unit)
                    runCurrent()

                    (viewModel.uiState.value.stage as ScanStage.Outcome).let { stage ->
                        stage.writing shouldBe null
                        stage.writeFailed shouldBe true
                    }

                    repository.physicalOnlyGate = null
                    repository.wishlistGate = null
                    if (action == WritingAction.Physical) {
                        repository.physicalOnlyAnswers.addLast(ScanResult.Success("u-new"))
                    } else {
                        repository.wishlistAnswers.addLast(ScanResult.Success("u-new"))
                    }
                    viewModel.add(action)
                    runCurrent()

                    repository.physicalOnlyCalls.size + repository.wishlistCalls.size shouldBe 2
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
                repository.resolveAnswers.addLast(ScanResult.Success(ScanOutcome.Unresolved))
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
                repository.resolveAnswers.addLast(ScanResult.Success(ScanOutcome.Unresolved))
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

    describe("searching by title") {

        it("#335 TC-58: a title search lists the providers' matches") {
            runTest {
                val record = randomProviderRecord()
                val repository = TestScanRepository().apply { searchAnswers.addLast(ScanResult.Success(listOf(record))) }
                val viewModel = sut(repository)

                viewModel.searchByTitleTapped()
                viewModel.titleQueryChanged("dune")
                viewModel.titleSearchSubmitted()
                runCurrent()

                viewModel.uiState.value.stage shouldBe ScanStage.TitleSearch(query = "dune", results = listOf(record))
            }
        }

        it("#335 TC-58: picking a match opens the outcome a scan would, filed under the record's ISBN") {
            runTest {
                val record = randomProviderRecord()
                val outcome = ScanOutcome.NotInLibrary(record)
                val repository = TestScanRepository()
                val viewModel = sut(repository)
                openVia(viewModel, repository, outcome, FoundVia.Search, record.isbn13, record)

                viewModel.uiState.value.stage shouldBe ScanStage.Outcome(outcome, record.isbn13, FoundVia.Search)
            }
        }

        it("#335 TC-58: a picked match is matched from its record, without a second provider lookup") {
            runTest {
                val record = randomProviderRecord()
                val repository = TestScanRepository()
                openVia(sut(repository), repository, ScanOutcome.NotInLibrary(record), FoundVia.Search, record.isbn13, record)

                repository.resolveCalls shouldBe emptyList()
                repository.resolveRecordCalls shouldBe listOf(record)
            }
        }

        it("#335 TC-58: wishlisting a picked match uses the search source") {
            runTest {
                val record = randomProviderRecord()
                val repository = TestScanRepository().apply { wishlistAnswers.addLast(ScanResult.Success("u-new")) }
                val viewModel = sut(repository)
                openVia(viewModel, repository, ScanOutcome.NotInLibrary(record), FoundVia.Search, record.isbn13, record)

                viewModel.addToWishlistTapped()
                runCurrent()

                repository.wishlistCalls.single().source shouldBe WishlistSource.Search
            }
        }

        it("#335 TC-58: Scan instead returns to the camera from the title search") {
            runTest {
                val viewModel = sut()

                viewModel.searchByTitleTapped()
                viewModel.scanInsteadTapped()

                viewModel.uiState.value.stage shouldBe ScanStage.Viewfinder()
            }
        }

        context("when an unresolved ISBN carries a typed title") {

            it("#335 TC-58: opens the title search with that title") {
                runTest {
                    val repository = TestScanRepository().apply { searchAnswers.addLast(ScanResult.Success(emptyList())) }
                    val viewModel = sut(repository)
                    openOutcome(viewModel, repository, ScanOutcome.Unresolved, randomValidIsbn13())
                    viewModel.titleQueryChanged("wind")

                    viewModel.searchByTitleTapped()
                    runCurrent()

                    viewModel.uiState.value.stage shouldBe ScanStage.TitleSearch(query = "wind", results = emptyList())
                }
            }

            it("#335 TC-58: searches the typed title") {
                runTest {
                    val repository = TestScanRepository().apply { searchAnswers.addLast(ScanResult.Success(emptyList())) }
                    val viewModel = sut(repository)
                    openOutcome(viewModel, repository, ScanOutcome.Unresolved, randomValidIsbn13())
                    viewModel.titleQueryChanged("wind")

                    viewModel.searchByTitleTapped()
                    runCurrent()

                    repository.searchCalls shouldBe listOf("wind")
                }
            }
        }
    }

    describe("a close match") {

        it("#335 TC-63: a picked candidate is filed under the scanned record's ISBN") {
            runTest {
                checkAll(Exhaustive.enum<FoundVia>()) { via ->
                    val candidate = randomScanBook()
                    val record = randomProviderRecord()
                    val repository = TestScanRepository().apply { checkInAnswers.addLast(ScanResult.Success(candidate.uuid)) }
                    val viewModel = sut(repository)
                    openVia(viewModel, repository, ScanOutcome.CloseMatch(listOf(candidate), record), via, randomValidIsbn13(), record)

                    viewModel.candidatePicked(candidate)
                    viewModel.checkInTapped()
                    runCurrent()

                    repository.checkInCalls.single().isbn shouldBe record.isbn13
                }
            }
        }

        it("#335 TC-63: a library book picked in the picker is filed under the ISBN in hand") {
            runTest {
                checkAll(Exhaustive.enum<FoundVia>()) { via ->
                    val record = randomProviderRecord()
                    val repository = TestScanRepository().apply { checkInAnswers.addLast(ScanResult.Success("u-new")) }
                    val librarySearch = TestLibrarySearchRepository().apply {
                        answers.addLast(LibrarySearchResult.Success(LibraryBookMatches(listOf(randomLibraryHit()), 1)))
                    }
                    val viewModel = sut(repository, librarySearch = librarySearch)
                    openVia(viewModel, repository, ScanOutcome.CloseMatch(listOf(randomScanBook()), record), via, randomValidIsbn13(), record)
                    val inHand = (viewModel.uiState.value.stage as ScanStage.Outcome).isbn
                    viewModel.alreadyHaveTapped()
                    viewModel.libraryQueryChanged("lo")
                    advanceTimeBy(PICKER_DEBOUNCE)
                    runCurrent()

                    viewModel.libraryHitPicked((viewModel.uiState.value.stage as ScanStage.LibraryPicker).hits.single())
                    viewModel.checkInTapped()
                    runCurrent()

                    repository.checkInCalls.single().isbn shouldBe inHand
                }
            }
        }

        it("#335 TC-64: marks the candidates that are on the wishlist") {
            runTest {
                val present = randomScanBook()
                val absent = randomScanBook()
                val wishlist = TestWishlistRepository().apply {
                    seed(present.uuid, WishlistState.Present(randomWishlistEntry()))
                    seed(absent.uuid, WishlistState.Absent)
                }
                val repository = TestScanRepository()
                val viewModel = sut(repository, wishlist = wishlist)
                openOutcome(viewModel, repository, ScanOutcome.CloseMatch(listOf(present, absent), randomProviderRecord()))
                runCurrent()

                (viewModel.uiState.value.stage as ScanStage.Outcome).wishlistedCandidates shouldBe setOf(present.uuid)
            }
        }

        it("#335 TC-64: picking a wishlisted candidate opens its wishlist confirm") {
            runTest {
                val present = randomScanBook()
                val wishlist = TestWishlistRepository().apply { seed(present.uuid, WishlistState.Present(randomWishlistEntry())) }
                val repository = TestScanRepository()
                val viewModel = sut(repository, wishlist = wishlist)
                openOutcome(viewModel, repository, ScanOutcome.CloseMatch(listOf(present), randomProviderRecord()))
                runCurrent()

                viewModel.candidatePicked(present)

                (viewModel.uiState.value.stage as ScanStage.Outcome).outcome shouldBe ScanOutcome.OnWishlist(present)
            }
        }

        it("#335 TC-64: checking a wishlisted candidate in ends off the wishlist") {
            runTest {
                val present = randomScanBook()
                val wishlist = TestWishlistRepository().apply { seed(present.uuid, WishlistState.Present(randomWishlistEntry())) }
                val repository = TestScanRepository().apply { checkInAnswers.addLast(ScanResult.Success(present.uuid)) }
                val viewModel = sut(repository, wishlist = wishlist)
                openOutcome(viewModel, repository, ScanOutcome.CloseMatch(listOf(present), randomProviderRecord()))
                runCurrent()
                viewModel.candidatePicked(present)

                viewModel.checkInTapped()
                runCurrent()

                viewModel.uiState.value.stage shouldBe successFor(present, present.uuid, SuccessKind.OffWishlist)
            }
        }

        it("#335 TC-65: saying no offers adding the scanned book") {
            runTest {
                val record = randomProviderRecord()
                val isbn = randomValidIsbn13()
                val repository = TestScanRepository()
                val viewModel = sut(repository)
                openVia(viewModel, repository, ScanOutcome.CloseMatch(listOf(randomScanBook()), record), FoundVia.Manual, isbn, record)

                viewModel.closeMatchDeclined()

                viewModel.uiState.value.stage shouldBe
                    ScanStage.Outcome(ScanOutcome.NotInLibrary(record), isbn, FoundVia.Manual, declined = true)
            }
        }
    }

    describe("the library picker") {

        it("#335 TC-67: opens from a close match, a book not in the library and an unresolved ISBN") {
            runTest {
                val record = randomProviderRecord()
                checkAll(
                    Exhaustive.of(
                        ScanOutcome.CloseMatch(listOf(randomScanBook()), record),
                        ScanOutcome.NotInLibrary(record),
                        ScanOutcome.Unresolved,
                    ),
                ) { outcome ->
                    val repository = TestScanRepository()
                    val viewModel = sut(repository)
                    openOutcome(viewModel, repository, outcome, randomValidIsbn13())
                    val returnTo = viewModel.uiState.value.stage as ScanStage.Outcome

                    viewModel.alreadyHaveTapped()

                    viewModel.uiState.value.stage shouldBe ScanStage.LibraryPicker(returnTo = returnTo)
                }
            }
        }

        it("#335 TC-67: Back returns to the outcome it came from, exactly") {
            runTest {
                checkAll(Exhaustive.of(ScanOutcome.NotInLibrary(randomProviderRecord()), ScanOutcome.Unresolved)) { outcome ->
                    val repository = TestScanRepository()
                    val viewModel = sut(repository)
                    openOutcome(viewModel, repository, outcome, randomValidIsbn13())
                    val returnTo = viewModel.uiState.value.stage

                    viewModel.alreadyHaveTapped()
                    viewModel.backTapped()
                    runCurrent()

                    viewModel.uiState.value.stage shouldBe returnTo
                }
            }
        }

        it("#335 TC-67: keeps at most five hits") {
            runTest {
                val repository = TestScanRepository()
                val librarySearch = TestLibrarySearchRepository().apply {
                    answers.addLast(LibrarySearchResult.Success(LibraryBookMatches(List(7) { randomLibraryHit() }, 7)))
                }
                val viewModel = sut(repository, librarySearch = librarySearch)
                openOutcome(viewModel, repository, ScanOutcome.Unresolved, randomValidIsbn13())
                viewModel.alreadyHaveTapped()

                viewModel.libraryQueryChanged("lo")
                advanceTimeBy(PICKER_DEBOUNCE)
                runCurrent()

                (viewModel.uiState.value.stage as ScanStage.LibraryPicker).hits.size shouldBe 5
            }
        }

        it("#335 TC-67: a pick opens the library book as a copy to check in") {
            runTest {
                val hit = randomLibraryHit()
                val repository = TestScanRepository()
                val librarySearch = TestLibrarySearchRepository().apply {
                    answers.addLast(LibrarySearchResult.Success(LibraryBookMatches(listOf(hit), 1)))
                }
                val viewModel = sut(repository, librarySearch = librarySearch)
                val isbn = randomValidIsbn13()
                openOutcome(viewModel, repository, ScanOutcome.Unresolved, isbn)
                viewModel.alreadyHaveTapped()
                viewModel.libraryQueryChanged("lo")
                advanceTimeBy(PICKER_DEBOUNCE)
                runCurrent()

                viewModel.libraryHitPicked(hit)

                viewModel.uiState.value.stage shouldBe ScanStage.Outcome(
                    ScanOutcome.InLibraryUnowned(
                        ScanBook(
                            uuid = hit.uuid,
                            title = hit.title,
                            authors = listOf(hit.authorDisplay),
                            coverUrl = hit.coverUrl,
                            hasPhysical = false,
                            hasFiles = hit.formats.isNotEmpty(),
                            isbn = null,
                        ),
                    ),
                    isbn,
                    FoundVia.Scan,
                )
            }
        }
    }

    describe("out-of-date search answers") {

        context("when the title query changed while a title search was in flight") {
            it("#335 TC-70: drops the title answer") {
                runTest {
                    val gate = CompletableDeferred<Unit>()
                    val repository = TestScanRepository().apply { searchGate = gate }
                    val viewModel = sut(repository)
                    viewModel.searchByTitleTapped()
                    viewModel.titleQueryChanged("dune")
                    viewModel.titleSearchSubmitted()
                    runCurrent()

                    repository.searchGate = null
                    viewModel.titleQueryChanged("dunes")
                    repository.searchAnswers.addLast(ScanResult.Success(listOf(randomProviderRecord())))
                    gate.complete(Unit)
                    runCurrent()

                    viewModel.uiState.value.stage shouldBe ScanStage.TitleSearch(query = "dunes")
                }
            }
        }

        context("when the reader left the title search") {
            it("#335 TC-70: drops the title answer") {
                runTest {
                    val gate = CompletableDeferred<Unit>()
                    val repository = TestScanRepository().apply { searchGate = gate }
                    val viewModel = sut(repository)
                    viewModel.searchByTitleTapped()
                    viewModel.titleQueryChanged("dune")
                    viewModel.titleSearchSubmitted()
                    runCurrent()

                    viewModel.scanInsteadTapped()
                    repository.searchAnswers.addLast(ScanResult.Success(listOf(randomProviderRecord())))
                    gate.complete(Unit)
                    runCurrent()

                    viewModel.uiState.value.stage shouldBe ScanStage.Viewfinder()
                }
            }
        }

        context("when a newer title search started") {
            it("#335 TC-70: shows only the newest search's results") {
                runTest {
                    val gate = CompletableDeferred<Unit>()
                    val stale = randomProviderRecord()
                    val fresh = randomProviderRecord()
                    val repository = TestScanRepository().apply { searchGate = gate }
                    val viewModel = sut(repository)
                    viewModel.searchByTitleTapped()
                    viewModel.titleQueryChanged("dune")
                    viewModel.titleSearchSubmitted()
                    runCurrent()

                    repository.searchGate = null
                    repository.searchAnswers.addLast(ScanResult.Success(listOf(fresh)))
                    viewModel.titleQueryChanged("dunes")
                    viewModel.titleSearchSubmitted()
                    runCurrent()
                    repository.searchAnswers.addLast(ScanResult.Success(listOf(stale)))
                    gate.complete(Unit)
                    runCurrent()

                    viewModel.uiState.value.stage shouldBe ScanStage.TitleSearch(query = "dunes", results = listOf(fresh))
                }
            }
        }

        context("when the picker query changed while a library search was in flight") {
            it("#335 TC-70: shows only the newest library search's hits") {
                runTest {
                    val gate = CompletableDeferred<Unit>()
                    val fresh = randomLibraryHit()
                    val repository = TestScanRepository()
                    val librarySearch = TestLibrarySearchRepository().apply { this.gate = gate }
                    val viewModel = sut(repository, librarySearch = librarySearch)
                    openOutcome(viewModel, repository, ScanOutcome.Unresolved, randomValidIsbn13())
                    viewModel.alreadyHaveTapped()
                    viewModel.libraryQueryChanged("lo")
                    advanceTimeBy(PICKER_DEBOUNCE)
                    runCurrent()

                    librarySearch.gate = null
                    librarySearch.answers.addLast(LibrarySearchResult.Success(LibraryBookMatches(listOf(fresh), 1)))
                    viewModel.libraryQueryChanged("lom")
                    advanceTimeBy(PICKER_DEBOUNCE)
                    runCurrent()

                    (viewModel.uiState.value.stage as ScanStage.LibraryPicker).hits shouldBe listOf(fresh)
                }
            }
        }

        context("when the reader left the library picker") {
            it("#335 TC-70: drops the library answer") {
                runTest {
                    val gate = CompletableDeferred<Unit>()
                    val repository = TestScanRepository()
                    val librarySearch = TestLibrarySearchRepository().apply {
                        this.gate = gate
                        answers.addLast(LibrarySearchResult.Success(LibraryBookMatches(listOf(randomLibraryHit()), 1)))
                    }
                    val viewModel = sut(repository, librarySearch = librarySearch)
                    openOutcome(viewModel, repository, ScanOutcome.Unresolved, randomValidIsbn13())
                    val returnTo = viewModel.uiState.value.stage
                    viewModel.alreadyHaveTapped()
                    viewModel.libraryQueryChanged("lo")
                    advanceTimeBy(PICKER_DEBOUNCE)
                    runCurrent()

                    viewModel.backTapped()
                    gate.complete(Unit)
                    runCurrent()

                    viewModel.uiState.value.stage shouldBe returnTo
                }
            }
        }
    }
})
