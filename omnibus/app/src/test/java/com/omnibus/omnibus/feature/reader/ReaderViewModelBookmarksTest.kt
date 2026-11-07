package com.omnibus.omnibus.feature.reader

import com.omnibus.omnibus.data.bookmarks.Bookmark
import com.omnibus.omnibus.data.bookmarks.BookmarkError
import com.omnibus.omnibus.data.bookmarks.BookmarkWriteResult
import com.omnibus.omnibus.data.bookmarks.BookmarksState
import com.omnibus.omnibus.data.bookmarks.TestBookmarksRepository
import com.omnibus.omnibus.data.library.TestLibraryRepository
import com.omnibus.omnibus.data.progress.TestProgressRepository
import com.omnibus.omnibus.data.reader.ReaderAssetLoaderFactory
import com.omnibus.omnibus.data.reader.BookFileResult
import com.omnibus.omnibus.data.reader.local.TestReaderSettingsStore
import com.omnibus.omnibus.data.reader.remote.BookFileSource
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.feature.reader.ui.ReaderContentsTab
import com.omnibus.omnibus.feature.reader.ui.ReaderEvent
import com.omnibus.omnibus.feature.reader.ui.RelocateData
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelBookmarksTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    /** The one place these cases build the ViewModel. The book stays Loading, so only the bookmark state is in play. */
    fun viewModel(bookmarks: TestBookmarksRepository = TestBookmarksRepository()): ReaderViewModel = ReaderViewModel(
        bookUuid = "book-1",
        libraryRepository = TestLibraryRepository().apply { pendingBook = CompletableDeferred() },
        assetLoaderFactory = ReaderAssetLoaderFactory(
            mockk(relaxed = true),
            Dispatchers.Unconfined,
            BookFileSource { BookFileResult.NotFound },
        ),
        progressRepository = TestProgressRepository(),
        settingsStore = TestReaderSettingsStore(),
        bookmarksRepository = bookmarks,
        timeSource = TimeSource { 0L },
        persistScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    )

    fun bookmark(id: Long?, clientId: String?, position: String, title: String? = null) =
        Bookmark(id, clientId, "book-1", position, title, null, null, null)

    fun relocate(cfi: String?, chapterTitle: String = "") =
        ReaderEvent.Relocate(RelocateData(cfi = cfi, chapterTitle = chapterTitle))

    /** Nothing arrives on the commands flow within a short wait. */
    suspend fun ReaderViewModel.noCommandArrives() = withTimeoutOrNull(100) { commands.first() } shouldBe null

    describe("ReaderViewModel bookmarks") {

        // TC-40: the state mirrors the repository's list and status, for this book's uuid.
        it("TC-40: the state mirrors the repository's bookmarks and status") {
            val bookmarks = TestBookmarksRepository()
            val a = bookmark(1L, "client-a", "epubcfi(/6/4)")
            val b = bookmark(2L, "client-b", "epubcfi(/6/8)")
            bookmarks.seed("book-1", BookmarksState.Loading)

            val vm = viewModel(bookmarks)
            vm.uiState.value.bookmarksStatus shouldBe ReaderBookmarksStatus.Loading
            bookmarks.flowRequests shouldBe listOf("book-1")

            bookmarks.seed("book-1", BookmarksState.Loaded(listOf(a, b)))
            vm.uiState.value.bookmarksStatus shouldBe ReaderBookmarksStatus.Loaded
            vm.uiState.value.bookmarks shouldBe listOf(a, b)

            bookmarks.seed("book-1", BookmarksState.Failed)
            vm.uiState.value.bookmarksStatus shouldBe ReaderBookmarksStatus.Failed
        }

        // TC-42: the count rises when an add starts and drops back when the repository rolls it back.
        it("TC-42: the count follows optimistic adds and rollbacks") {
            val bookmarks = TestBookmarksRepository()
            val gate = CompletableDeferred<Unit>()
            bookmarks.onAdd = { draft ->
                val optimistic = Bookmark(null, "client-1", draft.bookUuid, draft.position, draft.title, null, null, 1L)
                bookmarks.seed("book-1", BookmarksState.Loaded(listOf(optimistic)))
                gate.await()
                bookmarks.seed("book-1", BookmarksState.Loaded(emptyList()))
                BookmarkWriteResult.Failed(BookmarkError.Unreachable)
            }
            val vm = viewModel(bookmarks)
            vm.onPageEvent(relocate("epubcfi(/6/8)", chapterTitle = "Ch 1"))

            vm.onBookmarkToggled()
            vm.uiState.value.bookmarkCount shouldBe 1

            gate.complete(Unit)
            vm.uiState.value.bookmarkCount shouldBe 0
        }

        // TC-43: an unbookmarked page sends one add built from the location; the open menu stays open.
        it("TC-43: toggling an unbookmarked page adds a draft built from the location") {
            val bookmarks = TestBookmarksRepository()
            val vm = viewModel(bookmarks)
            vm.onPageEvent(relocate("  epubcfi(/6/8)  ", chapterTitle = "Ch 1 "))
            vm.onChromeToggled()
            vm.onMenuOpened()

            vm.onBookmarkToggled()

            bookmarks.drafts.size shouldBe 1
            bookmarks.drafts.single().bookUuid shouldBe "book-1"
            bookmarks.drafts.single().position shouldBe "epubcfi(/6/8)"
            bookmarks.drafts.single().title shouldBe "Ch 1"
            bookmarks.removed.size shouldBe 0
            vm.uiState.value.menuOpen shouldBe true
        }

        // TC-44: the draft limits apply before the repository sees anything.
        it("TC-44: the toggle applies the draft limits before calling the repository") {
            val bookmarks = TestBookmarksRepository()
            val vm = viewModel(bookmarks)

            vm.onPageEvent(relocate("e".repeat(4097), chapterTitle = "Ch"))
            vm.onBookmarkToggled()
            bookmarks.drafts.size shouldBe 0

            vm.onPageEvent(relocate("epubcfi(/6/8)", chapterTitle = "t".repeat(600)))
            vm.onBookmarkToggled()
            bookmarks.drafts.last().title shouldBe "t".repeat(512)

            vm.onPageEvent(relocate("epubcfi(/6/10)", chapterTitle = "   "))
            vm.onBookmarkToggled()
            bookmarks.drafts.last().title shouldBe null
        }

        // TC-45: with no location, a null cfi or a blank cfi, the toggle does nothing.
        it("TC-45: toggling without a current cfi does nothing") {
            val bookmarks = TestBookmarksRepository()
            val vm = viewModel(bookmarks)

            vm.onBookmarkToggled()
            vm.onPageEvent(relocate(null))
            vm.onBookmarkToggled()
            vm.onPageEvent(relocate("   "))
            vm.onBookmarkToggled()

            bookmarks.drafts.size shouldBe 0
            bookmarks.removed.size shouldBe 0
        }

        // TC-46: a bookmarked page removes every bookmark at its cfi, whitespace aside, and nothing else.
        it("TC-46: toggling a bookmarked page removes every bookmark at that cfi") {
            val bookmarks = TestBookmarksRepository()
            val first = bookmark(1L, "client-a", "epubcfi(/6/8)")
            val second = bookmark(2L, "client-b", " epubcfi(/6/8) ")
            val elsewhere = bookmark(3L, "client-c", "epubcfi(/6/10)")
            bookmarks.seed("book-1", BookmarksState.Loaded(listOf(first, second, elsewhere)))
            val vm = viewModel(bookmarks)
            vm.onPageEvent(relocate("epubcfi(/6/8)"))
            vm.onChromeToggled()
            vm.onMenuOpened()

            vm.onBookmarkToggled()

            bookmarks.removed shouldContainExactlyInAnyOrder listOf(first, second)
            bookmarks.drafts.size shouldBe 0
            vm.uiState.value.menuOpen shouldBe true
        }

        // TC-50: the Bookmarks row opens the sheet on its segment; Contents and onContentsRequested open on Contents.
        it("TC-50: choosing the bookmarks row opens the contents sheet on its bookmarks tab") {
            val vm = viewModel()
            vm.onChromeToggled()

            vm.onMenuOpened()
            vm.onMenuRowChosen(ReaderMenuRow.Bookmarks)
            vm.uiState.value.sheet shouldBe ReaderSheet.Contents
            vm.uiState.value.contentsTab shouldBe ReaderContentsTab.Bookmarks
            vm.uiState.value.menuOpen shouldBe false

            vm.onMenuOpened()
            vm.onMenuRowChosen(ReaderMenuRow.Contents)
            vm.uiState.value.contentsTab shouldBe ReaderContentsTab.Contents
            vm.uiState.value.menuOpen shouldBe false

            vm.onMenuRowChosen(ReaderMenuRow.Bookmarks)
            vm.onContentsRequested()
            vm.uiState.value.sheet shouldBe ReaderSheet.Contents
            vm.uiState.value.contentsTab shouldBe ReaderContentsTab.Contents
            vm.uiState.value.menuOpen shouldBe false
        }

        // TC-51: a chosen bookmark closes the sheet and sends exactly one display command.
        it("TC-51: choosing a bookmark closes the sheet and sends one display command") {
            val vm = viewModel()
            val chosen = bookmark(1L, "client-a", "epubcfi(/6/12)")
            vm.onContentsRequested()

            vm.onBookmarkChosen(chosen)

            vm.uiState.value.sheet shouldBe null
            vm.commands.first() shouldBe ReaderCommand.Display("epubcfi(/6/12)")
            vm.noCommandArrives()
        }

        // TC-52: retry asks the repository to refresh this book, once.
        it("TC-52: retry refreshes the book's bookmarks") {
            val bookmarks = TestBookmarksRepository()
            val vm = viewModel(bookmarks)

            vm.onBookmarksRetryRequested()

            bookmarks.refreshCalls shouldBe listOf("book-1")
        }
    }
})
