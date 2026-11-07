package com.omnibus.omnibus.feature.audiobook.bookmarks

import com.omnibus.omnibus.data.audiobook.playback.TestAudiobookController.Command
import com.omnibus.omnibus.data.audiobook.playback.TestAudiobookController
import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.chapters
import com.omnibus.omnibus.data.audiobook.playback.playbackState
import com.omnibus.omnibus.data.audiobook.playback.playingBook
import com.omnibus.omnibus.data.bookmarks.Bookmark
import com.omnibus.omnibus.data.bookmarks.BookmarkError
import com.omnibus.omnibus.data.bookmarks.BookmarkWriteResult
import com.omnibus.omnibus.data.bookmarks.BookmarksState
import com.omnibus.omnibus.data.bookmarks.TestBookmarksRepository
import com.omnibus.omnibus.feature.bookdetail.more.bookmarks.BookmarksContent
import com.omnibus.omnibus.testing.CollectingScope
import com.omnibus.omnibus.testing.TestTimeSource
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

private const val UUID = "book-1"

/** 2025-11-02 12:00 UTC, the "now" of these tests. */
private const val NOW = 1_762_084_800_000L

private fun bookmark(id: Long?, position: String = "3725", clientId: String? = null) = Bookmark(
    id = id,
    clientId = clientId,
    bookUuid = UUID,
    position = position,
    title = null,
    chapterTitle = null,
    fraction = 0.47f,
    createdAtSeconds = 1_761_609_600L,
)

/** The player's controller, repository, clock and ViewModel, with the cell's flow kept hot. */
private class PlayerHarness(initial: PlaybackState = PlaybackState()) {
    val controller = TestAudiobookController(initial)
    val repository = TestBookmarksRepository()
    val time = TestTimeSource(NOW)
    val collecting = CollectingScope()
    val vm = PlayerBookmarksViewModel(UUID, controller, repository, time)
        .also { collecting.collect(it.canAddBookmark) }
}

/** Runs [block] on a [PlayerHarness], and cancels its collection afterwards. */
private inline fun withPlayerHarness(initial: PlaybackState = PlaybackState(), block: PlayerHarness.() -> Unit) {
    val harness = PlayerHarness(initial)
    try {
        harness.block()
    } finally {
        harness.collecting.cancel()
    }
}

class PlayerBookmarksViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    describe("the Bookmark cell") {

        // TC15, R1, R2, P-BMK-1: the cell adds only while this book is loaded and settled
        it("TC15: canAddBookmark is true only for this book, loaded and settled") {
            withPlayerHarness {
                controller.state.value = PlaybackState()
                vm.canAddBookmark.value shouldBe false

                controller.state.value = playbackState(isLoading = true)
                vm.canAddBookmark.value shouldBe false

                controller.state.value = playbackState(book = playingBook(bookUuid = "other-book"))
                vm.canAddBookmark.value shouldBe false

                controller.state.value = playbackState()
                vm.canAddBookmark.value shouldBe true

                controller.state.value = playbackState(isLoading = true)
                vm.canAddBookmark.value shouldBe false
            }
        }

        // TC16, R1, R2, AC1: a tap on the loaded book drafts its position and chapter
        it("TC16: a tap adds one draft at the position, titled by the chapter") {
            withPlayerHarness {
                controller.state.value = playbackState(
                    chapters = chapters(300.0, 600.0, titles = listOf("Intro", "Two")),
                    positionSeconds = 90.0,
                )

                vm.onAddBookmark()

                repository.drafts.size shouldBe 1
                repository.drafts.single().bookUuid shouldBe UUID
                repository.drafts.single().position shouldBe "90"
                repository.drafts.single().title shouldBe "Intro"
            }
        }

        // TC17, R1, R2: nothing is added while the book is not ready
        it("TC17: a tap while the book is not ready adds nothing") {
            withPlayerHarness {
                val notReady = listOf(
                    PlaybackState(),
                    playbackState(isLoading = true),
                    playbackState(book = playingBook(bookUuid = "other-book")),
                    PlaybackState(book = playingBook(), positionSeconds = Double.NaN),
                )

                for (state in notReady) {
                    controller.state.value = state
                    vm.onAddBookmark()
                }

                repository.drafts.isEmpty() shouldBe true
            }
        }

        // TC18, R2: the position is read at the tap, not when the ViewModel was made
        it("TC18: the draft carries the position at the time of the tap") {
            withPlayerHarness {
                controller.state.value = playbackState(positionSeconds = 10.0)
                controller.state.value = playbackState(positionSeconds = 42.5)

                vm.onAddBookmark()

                repository.drafts.single().position shouldBe "42.5"
            }
        }

        // TC19, R2, I-BMK-2: a failed add records the draft once, and the sheet keeps its seeded rows
        it("TC19: a failed add leaves the sheet as it was") {
            withPlayerHarness {
                controller.state.value = playbackState()
                repository.seed(UUID, BookmarksState.Loaded(emptyList()))
                repository.onAdd = { BookmarkWriteResult.Failed(BookmarkError.Unreachable) }
                collecting.collect(vm.sheetState)

                vm.onAddBookmark()

                repository.drafts.size shouldBe 1
                vm.sheetState.value.content shouldBe BookmarksContent.Loaded(emptyList())
            }
        }
    }

    describe("the sheet") {

        // TC20, R4, P-BMK-3: loading, failed and loaded states come through, dated at the clock's now
        it("TC20: maps the repository states to the sheet's content") {
            withPlayerHarness {
                repository.seed(UUID, BookmarksState.Loading)
                collecting.collect(vm.sheetState)
                vm.sheetState.value.content shouldBe BookmarksContent.Loading

                repository.seed(UUID, BookmarksState.Loaded(listOf(bookmark(1))))
                val loaded = vm.sheetState.value.content as BookmarksContent.Loaded
                loaded.rows.map { it.key } shouldBe listOf("id:1")
                vm.sheetState.value.nowMillis shouldBe NOW

                repository.seed(UUID, BookmarksState.Failed)
                vm.sheetState.value.content shouldBe BookmarksContent.Failed

                repository.seed(UUID, BookmarksState.Loaded(emptyList()))
                vm.sheetState.value.content shouldBe BookmarksContent.Loaded(emptyList())
            }
        }

        // TC21, R3, R4: the bookmarks are read only once the sheet collects, not when the player opens
        it("TC21: reads the bookmarks only while the sheet is collected") {
            withPlayerHarness {
                repository.flowRequests.size shouldBe 0

                collecting.collect(vm.sheetState)

                repository.flowRequests shouldBe listOf(UUID)
            }
        }

        // TC22, R4: a retry asks the repository to refresh this book
        it("TC22: retry refreshes this book") {
            withPlayerHarness {
                vm.onRetry()

                repository.refreshCalls shouldBe listOf(UUID)
            }
        }

        // TC23, R6, AC2: a removed row is passed to the repository and leaves the sheet
        it("TC23: removing a row removes it from the repository and the sheet") {
            withPlayerHarness {
                val first = bookmark(1)
                val second = bookmark(2, position = "90")
                repository.seed(UUID, BookmarksState.Loaded(listOf(first, second)))
                repository.onRemove = {
                    repository.seed(UUID, BookmarksState.Loaded(listOf(second)))
                    BookmarkWriteResult.Removed
                }
                collecting.collect(vm.sheetState)
                val row = playerBookmarkRows(UUID, listOf(first)).single()

                vm.onBookmarkRemoved(row)

                repository.removed shouldBe listOf(first)
                (vm.sheetState.value.content as BookmarksContent.Loaded).rows.map { it.key } shouldBe listOf("id:2")
            }
        }

        // TC24, R6: a failed removal is recorded once and the sheet keeps the row the repository still holds
        it("TC24: a failed removal keeps the row on the sheet") {
            withPlayerHarness {
                val first = bookmark(1)
                repository.seed(UUID, BookmarksState.Loaded(listOf(first)))
                repository.onRemove = { BookmarkWriteResult.Failed(BookmarkError.Unreachable) }
                collecting.collect(vm.sheetState)
                val row = playerBookmarkRows(UUID, listOf(first)).single()

                vm.onBookmarkRemoved(row)

                repository.removed shouldBe listOf(first)
                vm.sheetState.value.content shouldBe BookmarksContent.Loaded(playerBookmarkRows(UUID, listOf(first)))
            }
        }
    }

    describe("a tap on an audio row") {

        // TC25, R5, AC2: a paused book seeks to the row and then plays
        it("TC25: seeks, then plays, while paused") {
            withPlayerHarness {
                controller.state.value = playbackState(isPlaying = false)

                vm.onListeningBookmarkChosen(125.0)

                controller.commands shouldBe listOf(Command.SeekTo(125.0), Command.Play)
            }
        }

        // TC26, R5: a playing book seeks and keeps playing
        it("TC26: seeks without a play command while playing") {
            withPlayerHarness {
                controller.state.value = playbackState(isPlaying = true)

                vm.onListeningBookmarkChosen(125.0)

                controller.commands shouldBe listOf(Command.SeekTo(125.0))
            }
        }

        // TC27, R5: another book, or none, is left alone
        it("TC27: does nothing when another book or no book is loaded") {
            withPlayerHarness {
                controller.state.value = playbackState(book = playingBook(bookUuid = "other-book"))
                vm.onListeningBookmarkChosen(125.0)

                controller.state.value = PlaybackState()
                vm.onListeningBookmarkChosen(125.0)

                controller.commands.isEmpty() shouldBe true
            }
        }
    }
})
