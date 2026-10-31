package com.omnibus.omnibus.feature.reader

import com.omnibus.omnibus.data.library.BookResult
import com.omnibus.omnibus.data.library.FakeLibraryRepository
import com.omnibus.omnibus.data.library.LibraryError
import com.omnibus.omnibus.data.library.testBook
import com.omnibus.omnibus.data.reader.BookFileResult
import com.omnibus.omnibus.data.reader.ReaderAssetLoaderFactory
import com.omnibus.omnibus.data.reader.ReaderSettings
import com.omnibus.omnibus.data.reader.remote.BookFileSource
import com.omnibus.omnibus.feature.library.LibraryUIError
import com.omnibus.omnibus.feature.reader.ui.ReaderEvent
import com.omnibus.omnibus.feature.reader.ui.ReaderLoadFailure
import com.omnibus.omnibus.feature.reader.ui.ReaderLoadStatus
import com.omnibus.omnibus.feature.reader.ui.RelocateData
import com.omnibus.omnibus.feature.reader.ui.TocEntry
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    lateinit var repository: FakeLibraryRepository

    beforeEach {
        // The book stays Loading here, so the layer-1 cases only see page state.
        repository = FakeLibraryRepository().apply { pendingBook = CompletableDeferred() }
    }

    /** The one place a test builds the ViewModel, so later layers only change this helper. */
    fun viewModel() = ReaderViewModel(
        bookUuid = "book-1",
        libraryRepository = repository,
        assetLoaderFactory = ReaderAssetLoaderFactory(
            mockk(relaxed = true),
            Dispatchers.Unconfined,
            BookFileSource { BookFileResult.NotFound },
        ),
    )

    val chapterOne = TocEntry(label = "Loomings", href = "chapter1.xhtml", level = 0)
    val chapterTwo = TocEntry(label = "The Carpet-Bag", href = "chapter2.xhtml#carpet", level = 1)

    fun relocate(cfi: String, page: Int, pct: Int, echo: Boolean = false) =
        ReaderEvent.Relocate(RelocateData(cfi = cfi, page = page, totalPages = 310, pct = pct, echo = echo))

    /** Nothing arrives on the commands flow within a short wait. */
    suspend fun ReaderViewModel.noCommandArrives() = withTimeoutOrNull(100) { commands.first() } shouldBe null

    describe("ReaderViewModel state") {

        it("TC-30: a relocate replaces the location") {
            val vm = viewModel()

            vm.onPageEvent(relocate("epubcfi(/6/8)", page = 3, pct = 2))
            vm.onPageEvent(relocate("epubcfi(/6/10)", page = 4, pct = 3))

            vm.uiState.value.location shouldBe RelocateData(
                cfi = "epubcfi(/6/10)",
                page = 4,
                totalPages = 310,
                pct = 3,
            )
        }

        it("TC-31: an echo relocate is stored flagged as not a movement") {
            val vm = viewModel()
            vm.onPageEvent(relocate("epubcfi(/6/8)", page = 3, pct = 2))

            vm.onPageEvent(relocate("epubcfi(/6/6)", page = 2, pct = 1, echo = true))

            val location = vm.uiState.value.location!!
            location.echo shouldBe true
            location.isMovement shouldBe false
            location.cfi shouldBe "epubcfi(/6/6)"
            location.page shouldBe 2
        }

        it("TC-32: a movement after an echo clears the flag") {
            val vm = viewModel()
            vm.onPageEvent(relocate("epubcfi(/6/8)", page = 3, pct = 2))
            vm.onPageEvent(relocate("epubcfi(/6/6)", page = 2, pct = 1, echo = true))

            vm.onPageEvent(relocate("epubcfi(/6/10)", page = 4, pct = 3))

            val location = vm.uiState.value.location!!
            location.echo shouldBe false
            location.isMovement shouldBe true
        }

        it("TC-33: a second toc replaces the first and never appends") {
            val vm = viewModel()
            val bare = listOf(
                TocEntry(label = "A", href = "a.xhtml", level = 0),
                TocEntry(label = "B", href = "b.xhtml", level = 0),
                TocEntry(label = "C", href = "c.xhtml", level = 0),
            )
            val enriched = listOf(
                TocEntry(label = "A", href = "a.xhtml", level = 0, page = 1, pct = 0),
                TocEntry(label = "B", href = "b.xhtml", level = 0, page = 12, pct = 4),
                TocEntry(label = "C", href = "c.xhtml", level = 0, page = 31, pct = 10),
            )

            vm.onPageEvent(ReaderEvent.Toc(bare))
            vm.onPageEvent(ReaderEvent.Toc(enriched))

            vm.uiState.value.toc shouldBe enriched
        }

        it("TC-34: a later bare toc replaces an enriched one, and an empty toc clears it") {
            val vm = viewModel()
            val enriched = listOf(TocEntry(label = "A", href = "a.xhtml", level = 0, page = 1, pct = 0))
            val bare = listOf(TocEntry(label = "B", href = "b.xhtml", level = 0))

            vm.onPageEvent(ReaderEvent.Toc(enriched))
            vm.onPageEvent(ReaderEvent.Toc(bare))
            vm.uiState.value.toc shouldBe bare

            vm.onPageEvent(ReaderEvent.Toc(emptyList()))
            vm.uiState.value.toc shouldBe emptyList()
        }

        it("TC-35: chrome toggles from the ToggleChrome event and from the intent") {
            val vm = viewModel()

            vm.onPageEvent(ReaderEvent.ToggleChrome)
            vm.uiState.value.chromeVisible shouldBe true
            vm.onPageEvent(ReaderEvent.ToggleChrome)
            vm.uiState.value.chromeVisible shouldBe false

            vm.onChromeToggled()
            vm.uiState.value.chromeVisible shouldBe true
        }

        it("TC-36: load status follows LoadStatusChanged") {
            val vm = viewModel()

            vm.onPageEvent(ReaderEvent.LoadStatusChanged(ReaderLoadStatus.Ready))
            vm.uiState.value.loadStatus shouldBe ReaderLoadStatus.Ready

            vm.onPageEvent(ReaderEvent.LoadStatusChanged(ReaderLoadStatus.Failed(ReaderLoadFailure.NotFound)))
            vm.uiState.value.loadStatus shouldBe ReaderLoadStatus.Failed(ReaderLoadFailure.NotFound)

            vm.onPageEvent(ReaderEvent.LoadStatusChanged(ReaderLoadStatus.Loading))
            vm.uiState.value.loadStatus shouldBe ReaderLoadStatus.Loading
        }

        it("TC-37: the page's status event does not set load status") {
            val vm = viewModel()

            vm.onPageEvent(ReaderEvent.Status("ready"))
            vm.uiState.value.loadStatus shouldBe ReaderLoadStatus.Loading
            vm.onPageEvent(ReaderEvent.Status("error"))
            vm.uiState.value.loadStatus shouldBe ReaderLoadStatus.Loading

            vm.onPageEvent(ReaderEvent.LoadStatusChanged(ReaderLoadStatus.Ready))
            vm.onPageEvent(ReaderEvent.Status("error"))
            vm.uiState.value.loadStatus shouldBe ReaderLoadStatus.Ready
        }

        it("TC-39: the sheet intents set the sheet") {
            val vm = viewModel()

            vm.onContentsRequested()
            vm.uiState.value.sheet shouldBe ReaderSheet.Contents

            vm.onSettingsRequested()
            vm.uiState.value.sheet shouldBe ReaderSheet.Settings

            vm.onSheetDismissed()
            vm.uiState.value.sheet shouldBe null
        }

        it("TC-40: choosing a contents entry closes the sheet and displays it") {
            val vm = viewModel()
            vm.onContentsRequested()

            vm.onTocEntryChosen(chapterOne)

            vm.uiState.value.sheet shouldBe null
            vm.commands.first() shouldBe ReaderCommand.Display(chapterOne.href)
            vm.noCommandArrives()
        }

        it("TC-41: commands are delivered exactly once, in order, and not re-delivered") {
            val vm = viewModel()

            // Both are chosen before anything collects, so the buffer holds them.
            vm.onTocEntryChosen(chapterOne)
            vm.onTocEntryChosen(chapterTwo)

            vm.commands.take(2).toList() shouldBe listOf(
                ReaderCommand.Display(chapterOne.href),
                ReaderCommand.Display(chapterTwo.href),
            )
            // A second collection sees nothing again.
            vm.noCommandArrives()
        }

        it("TC-43: a full fake event stream reduces to the expected state") {
            val vm = viewModel()

            vm.onPageEvent(ReaderEvent.LoadStatusChanged(ReaderLoadStatus.Loading))
            vm.onPageEvent(ReaderEvent.HostReady)
            vm.onPageEvent(ReaderEvent.Status("ready"))
            vm.onPageEvent(relocate("epubcfi(/6/8)", page = 3, pct = 2))
            vm.onPageEvent(ReaderEvent.Toc(listOf(chapterOne, chapterTwo)))
            vm.onPageEvent(
                ReaderEvent.Toc(
                    listOf(
                        chapterOne.copy(page = 12, pct = 4),
                        chapterTwo.copy(page = 31, pct = 10),
                    ),
                ),
            )
            vm.onPageEvent(ReaderEvent.ToggleChrome)
            vm.onPageEvent(relocate("epubcfi(/6/14!/4/2/1:0)", page = 12, pct = 4, echo = true))

            // Status is not load status, so the load status stays Loading here.
            vm.uiState.value shouldBe ReaderUIState(
                loadStatus = ReaderLoadStatus.Loading,
                location = RelocateData(
                    cfi = "epubcfi(/6/14!/4/2/1:0)",
                    page = 12,
                    totalPages = 310,
                    pct = 4,
                    echo = true,
                ),
                toc = listOf(
                    chapterOne.copy(page = 12, pct = 4),
                    chapterTwo.copy(page = 31, pct = 10),
                ),
                chromeVisible = true,
                settings = ReaderSettings(),
                sheet = null,
            )
        }
    }

    describe("ReaderViewModel book") {

        beforeEach {
            // These cases start from the fake's own result instead of holding the book in Loading.
            repository.pendingBook = null
        }

        it("TC-50: loads an EPUB book into Ready") {
            val gate = CompletableDeferred<BookResult>()
            repository.pendingBook = gate
            val vm = viewModel()

            vm.uiState.value.book shouldBe ReaderBookState.Loading

            gate.complete(BookResult.Success(testBook(1, title = "Moby Dick")))

            vm.uiState.value.book shouldBe ReaderBookState.Ready(title = "Moby Dick")
        }

        it("TC-51: a book with no epub is Unavailable NoEpub") {
            repository.bookResult = BookResult.Success(testBook(1, formats = listOf("pdf")))

            viewModel().uiState.value.book shouldBe ReaderBookState.Unavailable(ReaderUnavailableReason.NoEpub)
        }

        it("TC-52: a 404 is Unavailable NotFound") {
            repository.bookResult = BookResult.NotFound

            viewModel().uiState.value.book shouldBe ReaderBookState.Unavailable(ReaderUnavailableReason.NotFound)
        }

        it("TC-53: no server is Failed with LibraryUIError.NoServer") {
            repository.bookResult = BookResult.NoServer

            viewModel().uiState.value.book shouldBe ReaderBookState.Failed(LibraryUIError.NoServer)
        }

        it("TC-53: a failure is Failed with the error") {
            repository.bookResult = BookResult.Failure(LibraryError.Unreachable)

            viewModel().uiState.value.book shouldBe
                ReaderBookState.Failed(LibraryUIError.Failed(LibraryError.Unreachable))
        }

        it("TC-54: retry after Failed goes Loading, then Ready") {
            repository.bookResult = BookResult.Failure(LibraryError.Unreachable)
            val vm = viewModel()
            vm.uiState.value.book shouldBe ReaderBookState.Failed(LibraryUIError.Failed(LibraryError.Unreachable))

            val gate = CompletableDeferred<BookResult>()
            repository.pendingBook = gate
            vm.onRetryBookRequested()

            vm.uiState.value.book shouldBe ReaderBookState.Loading

            gate.complete(BookResult.Success(testBook(1, title = "Moby Dick")))

            vm.uiState.value.book shouldBe ReaderBookState.Ready(title = "Moby Dick")
            repository.bookRequests shouldBe listOf("book-1", "book-1")
        }

        it("TC-54: retry while Ready does not refetch or reset to Loading") {
            val vm = viewModel()
            vm.uiState.value.book shouldBe ReaderBookState.Ready(title = "Book 1")

            vm.onRetryBookRequested()

            vm.uiState.value.book shouldBe ReaderBookState.Ready(title = "Book 1")
            repository.bookRequests shouldBe listOf("book-1")
        }

        it("TC-55: loads the book for its uuid on creation, exactly once") {
            viewModel()

            repository.bookRequests shouldBe listOf("book-1")
        }

        it("TC-56: page events before Ready still reduce location, toc and chrome, and leave book Loading") {
            val gate = CompletableDeferred<BookResult>()
            repository.pendingBook = gate
            val vm = viewModel()

            vm.onPageEvent(ReaderEvent.ToggleChrome)
            vm.onPageEvent(ReaderEvent.Relocate(RelocateData(cfi = "epubcfi(/6/8)", page = 3, totalPages = 310, pct = 2)))
            vm.onPageEvent(ReaderEvent.Toc(listOf(chapterOne)))

            vm.uiState.value.book shouldBe ReaderBookState.Loading
            vm.uiState.value.chromeVisible shouldBe true
            vm.uiState.value.location?.cfi shouldBe "epubcfi(/6/8)"
            vm.uiState.value.toc shouldBe listOf(chapterOne)
        }

        it("TC-56: page events while Failed leave book Failed") {
            val failed = ReaderBookState.Failed(LibraryUIError.Failed(LibraryError.Unreachable))
            repository.bookResult = BookResult.Failure(LibraryError.Unreachable)
            val vm = viewModel()

            vm.onPageEvent(ReaderEvent.ToggleChrome)
            vm.onPageEvent(ReaderEvent.Relocate(RelocateData(cfi = "epubcfi(/6/8)", page = 3, totalPages = 310, pct = 2)))
            vm.onPageEvent(ReaderEvent.Toc(listOf(chapterOne)))

            vm.uiState.value.book shouldBe failed
            vm.uiState.value.chromeVisible shouldBe true
            vm.uiState.value.location?.cfi shouldBe "epubcfi(/6/8)"
            vm.uiState.value.toc shouldBe listOf(chapterOne)
        }
    }
})
