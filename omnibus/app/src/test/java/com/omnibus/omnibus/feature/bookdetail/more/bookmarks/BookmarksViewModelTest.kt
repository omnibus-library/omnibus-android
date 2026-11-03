package com.omnibus.omnibus.feature.bookdetail.more.bookmarks

import com.omnibus.omnibus.data.bookmarks.Bookmark
import com.omnibus.omnibus.data.bookmarks.BookmarksState
import com.omnibus.omnibus.data.bookmarks.FakeBookmarksRepository
import com.omnibus.omnibus.data.bookprogress.BookChaptersResult
import com.omnibus.omnibus.data.bookprogress.BookProgressError
import com.omnibus.omnibus.data.bookprogress.FakeBookProgressRepository
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.progress.BookChapter
import com.omnibus.omnibus.testing.CollectingScope
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

private const val UUID = "book-1"

/** 2025-11-02 12:00 UTC, the "now" of these tests. */
private const val NOW = 1_762_084_800_000L

private fun bookmark(
    id: Long?,
    clientId: String? = null,
    position: String = "epubcfi(/6/14!/4/2)",
    title: String? = null,
    fraction: Float? = 0.47f,
    createdAtSeconds: Long? = 1_761_609_600L,
) = Bookmark(
    id = id,
    clientId = clientId,
    bookUuid = UUID,
    position = position,
    title = title,
    chapterTitle = null,
    fraction = fraction,
    createdAtSeconds = createdAtSeconds,
)

/** Nine ebook chapters; 0.47 falls in the seventh. */
private val ebookChapters = listOf(0f, 0.1f, 0.2f, 0.3f, 0.35f, 0.4f, 0.45f, 0.5f, 0.6f)
    .mapIndexed { index, start -> BookChapter(index, null, start) }

/** Five audio chapters; 0.28 falls in the fourth. */
private val audioChapters = listOf(0f, 0.1f, 0.2f, 0.25f, 0.3f)
    .mapIndexed { index, start -> BookChapter(index, null, start) }

private fun chapters(total: Long? = 7_200L) = BookChaptersResult.Success(
    ebookChapters = ebookChapters,
    audioChapters = audioChapters,
    audioFileId = 3L,
    audioTotalSeconds = total,
)

/** A repository, a progress source and a collected ViewModel, with chapter answers queued in order. */
private class Harness(chapterAnswers: List<BookChaptersResult> = emptyList()) {
    val repository = FakeBookmarksRepository()
    val progress = FakeBookProgressRepository().apply { chaptersResults.addAll(chapterAnswers) }
    val collecting = CollectingScope()
    val vm = BookmarksViewModel(UUID, repository, progress, TimeSource { NOW })
        .also { collecting.collect(it.uiState) }
}

/** Runs [block] on a [Harness], and cancels its collection afterwards. */
private inline fun withHarness(chapterAnswers: List<BookChaptersResult> = emptyList(), block: Harness.() -> Unit) {
    val harness = Harness(chapterAnswers)
    try {
        harness.block()
    } finally {
        harness.collecting.cancel()
    }
}

class BookmarksViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    describe("opening and closing the sheet") {

        it("#117 TC-8: an offline open maps to failed and does not throw") {
            withHarness {
                repository.seed(UUID, BookmarksState.Failed)
                vm.openSheet()
                vm.uiState.value.content shouldBe BookmarksContent.Failed
            }
        }

        it("#117 TC-11: closing clears the sheet open flag and stops the collection") {
            withHarness {
                repository.seed(UUID, BookmarksState.Loaded(listOf(bookmark(id = 1))))
                vm.openSheet()
                vm.closeSheet()

                vm.uiState.value.sheetOpen shouldBe false
                repository.seed(UUID, BookmarksState.Loaded(emptyList()))
                repository.flowRequests.size shouldBe 1
            }
        }

        it("#117 TC-13: no bookmarks are read before the sheet opens") {
            withHarness {
                repository.flowRequests.size shouldBe 0
                vm.uiState.value.sheetOpen shouldBe false
                vm.uiState.value.content shouldBe BookmarksContent.Loading
            }
        }

        it("#117 TC-14: each open re-reads the server, and the chapters are not reloaded after a success") {
            withHarness(listOf(chapters())) {
                repository.seed(UUID, BookmarksState.Loaded(emptyList()))
                vm.openSheet()
                vm.closeSheet()
                vm.openSheet()

                repository.flowRequests.size shouldBe 2
                progress.chaptersCalls.size shouldBe 1
            }
        }
    }

    describe("loading the rows") {

        it("#117 TC-12: loaded bookmarks map to rows in repository order, with their placement and date") {
            withHarness(listOf(chapters())) {
                repository.seed(
                    UUID,
                    BookmarksState.Loaded(
                        listOf(
                            bookmark(id = 1, fraction = 0.47f, createdAtSeconds = 1_761_609_600L),
                            bookmark(id = 2, position = "2016", fraction = 0.9f, createdAtSeconds = 1_761_350_400L),
                            bookmark(id = 3, fraction = null, createdAtSeconds = null),
                        ),
                    ),
                )
                vm.openSheet()

                val rows = (vm.uiState.value.content as BookmarksContent.Loaded).rows
                rows.map { it.key } shouldBe listOf("id:1", "id:2", "id:3")
                rows.map { it.isAudio } shouldBe listOf(false, true, false)
                rows[1].listeningSeconds shouldBe 2016.0
                rows.map { it.placement } shouldBe listOf(
                    BookmarkPlacement(7, 47),
                    BookmarkPlacement(4, 28),
                    BookmarkPlacement(null, null),
                )
                rows.map { it.createdAtMillis } shouldBe listOf(1_761_609_600_000L, 1_761_350_400_000L, null)
                rows[0].destination shouldBe BookDetailDestination.Reader(UUID, "epubcfi(/6/14!/4/2)")
                rows[1].destination shouldBe BookDetailDestination.Player(UUID, null, 2016.0)
            }
        }

        it("#117 TC-17: rows without an id, and duplicate ids, still get unique keys") {
            withHarness {
                repository.seed(
                    UUID,
                    BookmarksState.Loaded(
                        listOf(
                            bookmark(id = null),
                            bookmark(id = null),
                            bookmark(id = 5),
                            bookmark(id = 5),
                        ),
                    ),
                )
                vm.openSheet()

                val keys = (vm.uiState.value.content as BookmarksContent.Loaded).rows.map { it.key }
                keys shouldBe listOf("index:0", "index:1", "id:5", "index:3")
                keys.toSet().size shouldBe 4
            }
        }

        it("#117 TC-32: chapters that load feed chapter numbers, and a failed load drops only the chapter") {
            withHarness(listOf(chapters())) {
                repository.seed(UUID, BookmarksState.Loaded(listOf(bookmark(id = 1))))
                vm.openSheet()
                vm.closeSheet()
                vm.openSheet()

                (vm.uiState.value.content as BookmarksContent.Loaded).rows.single().placement shouldBe
                    BookmarkPlacement(7, 47)
                progress.chaptersCalls.size shouldBe 1
            }

            withHarness(listOf(BookChaptersResult.Failed(BookProgressError.NoServer))) {
                repository.seed(UUID, BookmarksState.Loaded(listOf(bookmark(id = 1))))
                vm.openSheet()

                val row = (vm.uiState.value.content as BookmarksContent.Loaded).rows.single()
                row.placement shouldBe BookmarkPlacement(null, 47)
                row.createdAtMillis shouldBe 1_761_609_600_000L
            }
        }

        it("#117 TC-33: retry loads the chapters again when they are missing, and refreshes the bookmarks") {
            withHarness(listOf(BookChaptersResult.Failed(BookProgressError.NoServer), chapters())) {
                repository.seed(UUID, BookmarksState.Loaded(listOf(bookmark(id = 1))))
                vm.openSheet()
                progress.chaptersCalls.size shouldBe 1

                vm.retry()

                progress.chaptersCalls.size shouldBe 2
                repository.refreshCalls shouldBe listOf(UUID)
                (vm.uiState.value.content as BookmarksContent.Loaded).rows.single().placement shouldBe
                    BookmarkPlacement(7, 47)
            }
        }

        it("#117 TC-38: an empty loaded list is loaded, not loading") {
            withHarness {
                repository.seed(UUID, BookmarksState.Loaded(emptyList()))
                vm.openSheet()
                vm.uiState.value.content shouldBe BookmarksContent.Loaded(rows = emptyList())
            }
        }

        it("#117 TC-41: each repository state maps to its sheet content") {
            withHarness {
                vm.openSheet()

                repository.seed(UUID, BookmarksState.Loading)
                vm.uiState.value.content shouldBe BookmarksContent.Loading

                repository.seed(UUID, BookmarksState.Failed)
                vm.uiState.value.content shouldBe BookmarksContent.Failed

                repository.seed(UUID, BookmarksState.Loaded(listOf(bookmark(id = 1))))
                (vm.uiState.value.content as BookmarksContent.Loaded).rows.size shouldBe 1
            }
        }

        it("#117 TC-42: retry from failed refreshes the repository once with the book uuid") {
            withHarness {
                repository.seed(UUID, BookmarksState.Failed)
                vm.openSheet()

                vm.retry()

                repository.refreshCalls shouldBe listOf(UUID)
            }
        }

        it("#117 TC-43: a refresh never replaces a loaded list") {
            withHarness {
                repository.seed(UUID, BookmarksState.Loaded(listOf(bookmark(id = 1))))
                vm.openSheet()
                val before = vm.uiState.value.content

                vm.retry()

                vm.uiState.value.content shouldBe before
            }
        }
    }
})
