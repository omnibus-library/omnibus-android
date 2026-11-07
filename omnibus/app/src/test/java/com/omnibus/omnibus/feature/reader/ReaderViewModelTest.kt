package com.omnibus.omnibus.feature.reader

import app.cash.turbine.test
import com.omnibus.omnibus.data.bookmarks.randomBookmark
import com.omnibus.omnibus.data.library.BookResult
import com.omnibus.omnibus.data.library.LibraryError
import com.omnibus.omnibus.data.library.TestLibraryRepository
import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.feature.library.LibraryUIError
import com.omnibus.omnibus.feature.library.ui.coverIdentity
import com.omnibus.omnibus.feature.reader.ui.ReaderContentsTab
import com.omnibus.omnibus.feature.reader.ui.ReaderEvent
import com.omnibus.omnibus.feature.reader.ui.ReaderLoadFailure
import com.omnibus.omnibus.feature.reader.ui.ReaderLoadStatus
import com.omnibus.omnibus.feature.reader.ui.TocEntry
import com.omnibus.omnibus.feature.reader.ui.randomCfi
import com.omnibus.omnibus.feature.reader.ui.randomRelocateData
import com.omnibus.omnibus.testing.randomInt
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.equals.shouldBeEqual
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

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun tocEntry() = TocEntry(label = randomString(), href = randomString(), level = randomInt(0..3))

    fun epubBook() = randomBook().copy(formats = listOf("epub"))

    describe("the location") {
        it("TC-30: is the latest relocate") {
            val vm = sut()
            val latest = randomRelocateData()

            vm.onPageEvent(ReaderEvent.Relocate(randomRelocateData()))
            vm.onPageEvent(ReaderEvent.Relocate(latest))

            vm.uiState.value.location shouldBe latest
        }

        // #199: before locations the page reports pct 0; the echo once they load carries the real one.
        it("TC-199: shows a menu percent only once the book has locations") {
            val vm = sut()
            val location = randomRelocateData().copy(pct = randomInt(1..100))

            vm.onPageEvent(ReaderEvent.Relocate(location.copy(totalPages = 0)))
            vm.uiState.value.menuPercent shouldBe null

            vm.onPageEvent(ReaderEvent.Relocate(location.copy(totalPages = randomInt(1..1000), echo = true)))
            vm.uiState.value.menuPercent shouldBe location.pct
        }
    }

    describe("the contents") {
        it("TC-33: are replaced by a later toc") {
            val vm = sut()
            val enriched = listOf(tocEntry().copy(page = randomInt(1..100)))

            vm.onPageEvent(ReaderEvent.Toc(listOf(tocEntry(), tocEntry())))
            vm.onPageEvent(ReaderEvent.Toc(enriched))

            vm.uiState.value.toc shouldBe enriched
        }

        context("when an entry is chosen") {
            it("TC-40: closes the sheet") {
                val vm = sut()
                vm.onContentsRequested()

                vm.onTocEntryChosen(tocEntry())

                vm.uiState.value.sheet shouldBe null
            }

            it("TC-40: asks the page to show the entry, once") {
                val vm = sut()
                val entry = tocEntry()

                vm.onTocEntryChosen(entry)

                vm.commands.test {
                    awaitItem() shouldBe ReaderCommand.Display(entry.href)
                    expectNoEvents()
                }
            }
        }
    }

    describe("the load status") {
        it("TC-36: follows the host's status") {
            val vm = sut()
            val failed = ReaderLoadStatus.Failed(ReaderLoadFailure.NotFound)

            vm.onPageEvent(ReaderEvent.LoadStatusChanged(failed))

            vm.uiState.value.loadStatus shouldBe failed
        }

        it("TC-37: ignores the page's own status") {
            val vm = sut()

            vm.onPageEvent(ReaderEvent.Status("ready"))

            vm.uiState.value.loadStatus shouldBe ReaderLoadStatus.Loading
        }
    }

    describe("the sheets") {
        it("TC-39: follow the sheet intents") {
            val vm = sut()

            vm.onContentsRequested()
            vm.uiState.value.sheet shouldBe ReaderSheet.Contents

            vm.onSettingsRequested()
            vm.uiState.value.sheet shouldBe ReaderSheet.Settings

            vm.onSheetDismissed()
            vm.uiState.value.sheet shouldBe null
        }
    }

    describe("loading the book") {
        context("with an EPUB") {
            it("TC-50: goes from loading to ready") {
                val book = epubBook()
                val gate = CompletableDeferred<BookResult>()

                val vm = sut(libraryRepository = TestLibraryRepository().apply { pendingBook = gate })
                vm.uiState.value.book shouldBe ReaderBookState.Loading

                gate.complete(BookResult.Success(book))
                vm.uiState.value.book shouldBe ReaderBookState.Ready(book.displayTitle, book.coverIdentity())
            }
        }

        context("when it can't be read") {
            it("TC-51: reports why, for no EPUB, not found, no server or a failure") {
                checkAll(
                    Exhaustive.of(
                        BookResult.Success(randomBook().copy(formats = listOf("pdf"))) to
                            ReaderBookState.Unavailable(ReaderUnavailableReason.NoEpub),
                        BookResult.NotFound to ReaderBookState.Unavailable(ReaderUnavailableReason.NotFound),
                        BookResult.NoServer to ReaderBookState.Failed(LibraryUIError.NoServer),
                        BookResult.Failure(LibraryError.Unreachable) to
                            ReaderBookState.Failed(LibraryUIError.Failed(LibraryError.Unreachable)),
                    ),
                ) { (result, state) ->
                    val vm = sut(libraryRepository = TestLibraryRepository().apply { bookResult = result })

                    vm.uiState.value.book shouldBeEqual state
                }
            }
        }
    }

    describe("retrying the book") {
        context("after a failed read") {
            it("TC-54: reads the book again") {
                val uuid = randomString()
                val book = epubBook()
                val library = TestLibraryRepository().apply { bookResult = BookResult.Failure(LibraryError.Unreachable) }
                val vm = sut(bookUuid = uuid, libraryRepository = library)
                library.bookResult = BookResult.Success(book)

                vm.onRetryBookRequested()

                library.bookRequests shouldBe listOf(uuid, uuid)
                vm.uiState.value.book shouldBe ReaderBookState.Ready(book.displayTitle, book.coverIdentity())
            }
        }

        context("while the book is ready") {
            it("TC-54: does not read it again") {
                val library = TestLibraryRepository().apply { bookResult = BookResult.Success(epubBook()) }
                val vm = sut(libraryRepository = library)

                vm.onRetryBookRequested()

                library.bookRequests.size shouldBe 1
            }
        }
    }

    describe("the chrome") {
        it("TC-35: toggles from the page's centre tap") {
            val vm = sut()

            vm.onPageEvent(ReaderEvent.ToggleChrome)
            vm.uiState.value.chromeVisible shouldBe true

            vm.onPageEvent(ReaderEvent.ToggleChrome)
            vm.uiState.value.chromeVisible shouldBe false
        }
    }

    describe("the menu") {
        fun sutWithChromeUp() = sut().also { it.onChromeToggled() }

        it("CS-1: offers the Contents, Bookmarks and Settings rows") {
            sut().uiState.value.menuRows shouldBe
                listOf(ReaderMenuRow.Contents, ReaderMenuRow.Bookmarks, ReaderMenuRow.Settings)
        }

        context("with the chrome hidden") {
            it("RC-14: does not open") {
                val vm = sut()

                vm.onMenuOpened()

                vm.uiState.value.menuOpen shouldBe false
            }
        }

        context("with the chrome up") {
            it("RC-15: opens") {
                val vm = sutWithChromeUp()

                vm.onMenuOpened()

                vm.uiState.value.menuOpen shouldBe true
            }
        }

        context("while open") {
            fun sutWithMenuOpen() = sutWithChromeUp().also { it.onMenuOpened() }

            it("RC-16: leaves the chrome up as it is dismissed") {
                val vm = sutWithMenuOpen()

                vm.onMenuDismissed()

                vm.uiState.value.menuOpen shouldBe false
                vm.uiState.value.chromeVisible shouldBe true
            }

            it("RC-17: closes with the chrome") {
                val vm = sutWithMenuOpen()

                vm.onChromeToggled()

                vm.uiState.value.menuOpen shouldBe false
            }

            it("RC-18: gives way to the chosen row's sheet, for every row") {
                checkAll(
                    Exhaustive.of(
                        Triple(ReaderMenuRow.Contents, ReaderSheet.Contents, ReaderContentsTab.Contents),
                        Triple(ReaderMenuRow.Bookmarks, ReaderSheet.Contents, ReaderContentsTab.Bookmarks),
                        Triple(ReaderMenuRow.Settings, ReaderSheet.Settings, ReaderContentsTab.Contents),
                    ),
                ) { (row, sheet, tab) ->
                    val vm = sutWithMenuOpen()

                    vm.onMenuRowChosen(row)

                    val state = vm.uiState.value
                    state.menuOpen shouldBe false
                    state.sheet shouldBe sheet
                    state.contentsTab shouldBe tab
                }
            }
        }
    }

    describe("a bookmark chosen in the sheet") {
        fun bookmark(position: String) = randomBookmark().copy(position = position)

        it("TC-51: closes the sheet") {
            val vm = sut()
            vm.onContentsRequested()

            vm.onBookmarkChosen(bookmark(randomCfi()))

            vm.uiState.value.sheet shouldBe null
        }

        it("TC-51: asks the page to show its position, once") {
            val vm = sut()
            val cfi = randomCfi()

            vm.onBookmarkChosen(bookmark(cfi))

            vm.commands.test {
                awaitItem() shouldBe ReaderCommand.Display(cfi)
                expectNoEvents()
            }
        }
    }
})
