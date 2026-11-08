package com.omnibus.omnibus.feature.reader

import com.omnibus.omnibus.data.bookmarks.BookmarkDraft
import com.omnibus.omnibus.data.bookmarks.BookmarksState
import com.omnibus.omnibus.data.bookmarks.TestBookmarksRepository
import com.omnibus.omnibus.data.bookmarks.randomBookmark
import com.omnibus.omnibus.feature.reader.ui.ReaderContentsTab
import com.omnibus.omnibus.feature.reader.ui.ReaderEvent
import com.omnibus.omnibus.feature.reader.ui.randomCfi
import com.omnibus.omnibus.feature.reader.ui.randomRelocateData
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelBookmarksTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun ReaderViewModel.relocateTo(cfi: String?, chapterTitle: String = randomString()) =
        onPageEvent(ReaderEvent.Relocate(randomRelocateData().copy(cfi = cfi, chapterTitle = chapterTitle)))

    describe("the book's bookmarks") {
        it("TC-40: mirror the repository's state for this book") {
            val uuid = randomString()
            val rows = listOf(randomBookmark(), randomBookmark())
            val bookmarks = TestBookmarksRepository().apply { seed(uuid, BookmarksState.Loading) }

            val vm = sut(bookUuid = uuid, bookmarksRepository = bookmarks)
            vm.uiState.value.bookmarksStatus shouldBe ReaderBookmarksStatus.Loading

            bookmarks.seed(uuid, BookmarksState.Loaded(rows))
            vm.uiState.value.bookmarksStatus shouldBe ReaderBookmarksStatus.Loaded
            vm.uiState.value.bookmarks shouldBe rows

            bookmarks.seed(uuid, BookmarksState.Failed)
            vm.uiState.value.bookmarksStatus shouldBe ReaderBookmarksStatus.Failed
        }
    }

    describe("toggling the page's bookmark") {
        context("on an unbookmarked page") {
            it("TC-43: adds a draft built from the location") {
                val uuid = randomString()
                val cfi = randomCfi()
                val title = randomString()
                val bookmarks = TestBookmarksRepository().apply { onAdd = { awaitCancellation() } }
                val vm = sut(bookUuid = uuid, bookmarksRepository = bookmarks)
                vm.relocateTo(cfi, chapterTitle = title)

                vm.onBookmarkToggled()

                bookmarks.drafts shouldBe listOf(BookmarkDraft.create(uuid, cfi, title))
            }

            it("#224 TC-6: records the trimmed cfi as the page tapped") {
                val cfi = randomCfi()
                val bookmarks = TestBookmarksRepository().apply { onAdd = { awaitCancellation() } }
                val vm = sut(bookmarksRepository = bookmarks)
                vm.relocateTo(" $cfi ")

                vm.onBookmarkToggled()

                vm.uiState.value.bookmarkTapPage shouldBe cfi
            }
        }

        context("without a current cfi") {
            it("TC-45: adds nothing, for a null or blank cfi") {
                checkAll(Exhaustive.of(null, "   ")) { cfi ->
                    val bookmarks = TestBookmarksRepository()
                    val vm = sut(bookmarksRepository = bookmarks)
                    vm.relocateTo(cfi)

                    vm.onBookmarkToggled()

                    bookmarks.drafts.shouldBeEmpty()
                }
            }

            it("#224 TC-6: records no tapped page, for a null or blank cfi") {
                checkAll(Exhaustive.of(null, "   ")) { cfi ->
                    val vm = sut()
                    vm.relocateTo(cfi)

                    vm.onBookmarkToggled()

                    vm.uiState.value.bookmarkTapPage shouldBe null
                }
            }
        }

        context("on a bookmarked page") {
            it("TC-46: removes every bookmark at that cfi, whitespace aside") {
                val uuid = randomString()
                val cfi = randomCfi()
                val first = randomBookmark().copy(position = cfi)
                val second = randomBookmark().copy(position = " $cfi ")
                val elsewhere = randomBookmark().copy(position = randomCfi())
                val bookmarks = TestBookmarksRepository().apply {
                    seed(uuid, BookmarksState.Loaded(listOf(first, second, elsewhere)))
                    onRemove = { awaitCancellation() }
                }
                val vm = sut(bookUuid = uuid, bookmarksRepository = bookmarks)
                vm.relocateTo(cfi)

                vm.onBookmarkToggled()

                bookmarks.removed shouldContainExactlyInAnyOrder listOf(first, second)
            }

            it("#224 TC-6: records the trimmed cfi as the page tapped") {
                val uuid = randomString()
                val cfi = randomCfi()
                val bookmarks = TestBookmarksRepository().apply {
                    seed(uuid, BookmarksState.Loaded(listOf(randomBookmark().copy(position = cfi))))
                    onRemove = { awaitCancellation() }
                }
                val vm = sut(bookUuid = uuid, bookmarksRepository = bookmarks)
                vm.relocateTo(" $cfi ")

                vm.onBookmarkToggled()

                vm.uiState.value.bookmarkTapPage shouldBe cfi
            }
        }

        context("after a tap on the page") {
            it("#224 TC-7: clears the tapped page when the location moves to another cfi") {
                val cfi = randomCfi()
                val bookmarks = TestBookmarksRepository().apply { onAdd = { awaitCancellation() } }
                val vm = sut(bookmarksRepository = bookmarks)
                vm.relocateTo(cfi)
                vm.onBookmarkToggled()

                vm.relocateTo(randomCfi())

                vm.uiState.value.bookmarkTapPage shouldBe null
            }

            it("#224 TC-7: keeps the tapped page when the location echoes the same cfi, untrimmed") {
                val cfi = randomCfi()
                val bookmarks = TestBookmarksRepository().apply { onAdd = { awaitCancellation() } }
                val vm = sut(bookmarksRepository = bookmarks)
                vm.relocateTo(cfi)
                vm.onBookmarkToggled()

                vm.relocateTo("  $cfi\n")

                vm.uiState.value.bookmarkTapPage shouldBe cfi
            }
        }
    }

    describe("asking for the contents") {
        context("after the sheet was on its bookmarks tab") {
            it("TC-50: opens on the contents tab") {
                val vm = sut()
                vm.onMenuRowChosen(ReaderMenuRow.Bookmarks)

                vm.onContentsRequested()

                vm.uiState.value.contentsTab shouldBe ReaderContentsTab.Contents
            }
        }
    }

    describe("retrying a failed load") {
        it("TC-52: refreshes this book's bookmarks") {
            val uuid = randomString()
            val bookmarks = TestBookmarksRepository()
            val vm = sut(bookUuid = uuid, bookmarksRepository = bookmarks)

            vm.onBookmarksRetryRequested()

            bookmarks.refreshCalls shouldBe listOf(uuid)
        }
    }
})
