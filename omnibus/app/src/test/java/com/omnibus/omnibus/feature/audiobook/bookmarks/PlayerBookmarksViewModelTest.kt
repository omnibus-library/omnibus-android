package com.omnibus.omnibus.feature.audiobook.bookmarks

import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.TestAudiobookController
import com.omnibus.omnibus.data.audiobook.playback.TestAudiobookController.Command
import com.omnibus.omnibus.data.audiobook.playback.randomPlaybackState
import com.omnibus.omnibus.data.audiobook.playback.randomPlayingBook
import com.omnibus.omnibus.data.bookmarks.BookmarksState
import com.omnibus.omnibus.data.bookmarks.TestBookmarksRepository
import com.omnibus.omnibus.data.bookmarks.randomBookmark
import com.omnibus.omnibus.feature.bookdetail.more.bookmarks.BookmarksContent
import com.omnibus.omnibus.testing.TestTimeSource
import com.omnibus.omnibus.testing.randomDouble
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** Keeps [flow] hot for as long as the view model lives, so `.value` stays current. */
private fun PlayerBookmarksViewModel.keepCollecting(flow: Flow<*>) {
    viewModelScope.launch { flow.collect {} }
}

/** A state holding the book with [bookUuid], and random otherwise. */
private fun holding(bookUuid: String): PlaybackState =
    randomPlaybackState().copy(book = randomPlayingBook().copy(bookUuid = bookUuid))

class PlayerBookmarksViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun sut(
        bookUuid: String = randomString(),
        controller: TestAudiobookController = TestAudiobookController(),
        repository: TestBookmarksRepository = TestBookmarksRepository(),
        time: TestTimeSource = TestTimeSource(),
    ) = PlayerBookmarksViewModel(bookUuid, controller, repository, time)

    describe("the Bookmark cell") {
        // TC15, R1, R2, P-BMK-1: the cell adds only while this book is loaded and settled
        it("TC15: can add once this book has settled") {
            val uuid = randomString()
            val loading = holding(uuid).copy(isLoading = true, positionSeconds = randomDouble(0.0, 10_000.0))
            val controller = TestAudiobookController(loading)
            val vm = sut(uuid, controller)
            vm.keepCollecting(vm.canAddBookmark)
            vm.canAddBookmark.value shouldBe false

            controller.state.value = loading.copy(isLoading = false)

            vm.canAddBookmark.value shouldBe true
        }

        // TC16, R1, R2, AC1: a tap on the loaded book drafts its position and chapter, read at the tap
        it("TC16: adds one draft of the state at the tap") {
            val uuid = randomString()
            val controller = TestAudiobookController()
            val repository = TestBookmarksRepository()
            val vm = sut(uuid, controller, repository)
            val state = holding(uuid).copy(isLoading = false)

            controller.state.value = state
            vm.onAddBookmark()

            repository.drafts shouldBe listOf(audioBookmarkDraft(uuid, state).shouldNotBeNull())
        }

        context("when the book isn't ready") {
            // TC17, R1, R2: nothing is added while the book is not ready
            it("TC17: adds nothing") {
                val repository = TestBookmarksRepository()

                sut(repository = repository).onAddBookmark()

                repository.drafts.shouldBeEmpty()
            }
        }
    }

    describe("the sheet") {
        // TC20, R4, P-BMK-3: failed and loaded states come through, dated at the clock's now
        it("TC20: maps a failed or loaded repository state to the sheet, dated at the clock's now") {
            val uuid = randomString()
            val now = randomLong()
            val bookmark = randomBookmark().copy(bookUuid = uuid)

            checkAll(
                Exhaustive.of<Pair<BookmarksState, BookmarksContent>>(
                    BookmarksState.Failed to BookmarksContent.Failed,
                    BookmarksState.Loaded(listOf(bookmark)) to BookmarksContent.Loaded(playerBookmarkRows(uuid, listOf(bookmark))),
                ),
            ) { (state, expected) ->
                val repository = TestBookmarksRepository()
                repository.seed(uuid, state)
                val vm = sut(uuid, repository = repository, time = TestTimeSource(now))

                vm.keepCollecting(vm.sheetState)

                vm.sheetState.value shouldBe PlayerBookmarksUIState(expected, now)
            }
        }

        // TC21, R3, R4: the bookmarks are read only once the sheet collects, not when the player opens
        it("TC21: reads the bookmarks only once collected") {
            val uuid = randomString()
            val repository = TestBookmarksRepository()
            val vm = sut(uuid, repository = repository)
            repository.flowRequests.shouldBeEmpty()

            vm.keepCollecting(vm.sheetState)

            repository.flowRequests shouldBe listOf(uuid)
        }

        // TC22, R4: a retry asks the repository to refresh this book
        it("TC22: refreshes this book on a retry") {
            val uuid = randomString()
            val repository = TestBookmarksRepository()

            sut(uuid, repository = repository).onRetry()

            repository.refreshCalls shouldBe listOf(uuid)
        }

        // TC23, R6, AC2: a removed row is passed to the repository
        it("TC23: removes a row's bookmark") {
            val uuid = randomString()
            val bookmark = randomBookmark().copy(bookUuid = uuid)
            val repository = TestBookmarksRepository()

            sut(uuid, repository = repository).onBookmarkRemoved(playerBookmarkRows(uuid, listOf(bookmark)).single())

            repository.removed shouldBe listOf(bookmark)
        }
    }

    describe("a tap on an audio row") {
        // TC25, TC26, R5, AC2: a paused book seeks to the row and then plays; a playing book seeks and keeps playing
        it("TC25 TC26: seeks, then plays only a paused book") {
            val uuid = randomString()
            val seconds = randomDouble(0.0, 10_000.0)

            checkAll(
                Exhaustive.of(
                    false to listOf(Command.SeekTo(seconds), Command.Play),
                    true to listOf(Command.SeekTo(seconds)),
                ),
            ) { (isPlaying, expected) ->
                val controller = TestAudiobookController(holding(uuid).copy(isPlaying = isPlaying))

                sut(uuid, controller).onListeningBookmarkChosen(seconds)

                controller.commands shouldBe expected
            }
        }

        // TC27, R5: another book, or none, is left alone
        it("TC27: sends nothing for another book or no book") {
            val uuid = randomString()

            checkAll(Exhaustive.of(holding("$uuid-other"), PlaybackState())) { state ->
                val controller = TestAudiobookController(state)

                sut(uuid, controller).onListeningBookmarkChosen(randomDouble(0.0, 10_000.0))

                controller.commands.shouldBeEmpty()
            }
        }
    }
})
