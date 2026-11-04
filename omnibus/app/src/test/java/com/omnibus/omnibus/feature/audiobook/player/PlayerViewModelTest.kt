package com.omnibus.omnibus.feature.audiobook.player

import com.omnibus.omnibus.data.audiobook.playback.FakeAudiobookController
import com.omnibus.omnibus.data.audiobook.playback.FakeAudiobookController.Command
import com.omnibus.omnibus.data.audiobook.playback.PlaybackError
import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.playbackState
import com.omnibus.omnibus.data.audiobook.playback.playingBook
import com.omnibus.omnibus.data.library.BookResult
import com.omnibus.omnibus.data.library.FakeBookAvailability
import com.omnibus.omnibus.data.library.FakeLibraryRepository
import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.data.library.testBook
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

private const val BOOK = "uuid-1"

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    /** The one place a test builds the ViewModel, so the route arguments stay in one spot. */
    fun viewModel(
        controller: FakeAudiobookController,
        availability: FakeBookAvailability = FakeBookAvailability(answer = true),
        library: FakeLibraryRepository = FakeLibraryRepository(),
        fileId: Long? = 9L,
        position: Double? = 120.0,
    ) = PlayerViewModel(BOOK, fileId, position, controller, availability, library)

    describe("offline gate") {

        // P-SCR-2, P-SCR-AC6, AND-RUSE-7
        it("#165 TC-11: shows the offline gate and never loads (P-SCR-2)") {
            val controller = FakeAudiobookController()
            val availability = FakeBookAvailability(answer = false)

            val vm = viewModel(controller, availability)

            vm.uiState.value.gate shouldBe PlayerGate.Offline
            controller.commands.filterIsInstance<Command.Load>() shouldBe emptyList()
            availability.calls shouldBe listOf(BOOK to ProgressFormat.Listening)
        }

        // P-SCR-2 (exemption for a book already loaded)
        it("#165 TC-12: a book the controller already holds skips the gate") {
            val controller = FakeAudiobookController(playbackState(book = playingBook(BOOK)))
            val availability = FakeBookAvailability(answer = false)

            val vm = viewModel(controller, availability)

            vm.uiState.value.gate shouldBe PlayerGate.Open
            controller.commands.filterIsInstance<Command.Load>().size shouldBe 1
            availability.calls shouldBe emptyList()
        }

        // P-SCR-2 (exemption is per book)
        it("#165 TC-13: another loaded book does not exempt") {
            val controller = FakeAudiobookController(playbackState(book = playingBook("other-book")))
            val availability = FakeBookAvailability(answer = false)

            val vm = viewModel(controller, availability)

            vm.uiState.value.gate shouldBe PlayerGate.Offline
            controller.commands.filterIsInstance<Command.Load>() shouldBe emptyList()
            availability.calls shouldBe listOf(BOOK to ProgressFormat.Listening)
        }
    }

    describe("loading") {

        // P-SCR-1, P-SYNC-2, C2
        it("#165 TC-17: loads once with autoplay and the route's file and position (P-SCR-1)") {
            val controller = FakeAudiobookController()

            val vm = viewModel(controller, fileId = 9L, position = 120.0)

            controller.commands shouldBe listOf(Command.Load(BOOK, 9L, 120.0, autoplay = true))
            vm.uiState.value.gate shouldBe PlayerGate.Open
        }

        // R4
        it("#165 TC-18: state updates never reload") {
            val controller = FakeAudiobookController()
            viewModel(controller)

            controller.state.value = playbackState(book = playingBook(BOOK), positionSeconds = 30.0)
            controller.state.value = playbackState(book = playingBook(BOOK), positionSeconds = 31.0, rate = 1.5)
            controller.state.value = playbackState(book = playingBook(BOOK), isPlaying = true)

            controller.commands.filterIsInstance<Command.Load>().size shouldBe 1
        }
    }

    describe("library book") {

        // R8, P-SCR-6
        it("#165 TC-19: uses the library book until the controller knows it") {
            val controller = FakeAudiobookController()
            val library = FakeLibraryRepository()
            val held = CompletableDeferred<BookResult>()
            library.pendingBook = held

            val vm = viewModel(controller, library = library)
            vm.uiState.value.title shouldBe ""

            held.complete(BookResult.Success(testBook(1, title = "Library Title")))

            vm.uiState.value.title shouldBe "Library Title"
            library.bookRequests shouldBe listOf("uuid-1")
        }

        // R4
        it("#165 TC-19: ignores a failed library read") {
            val controller = FakeAudiobookController()
            val library = FakeLibraryRepository()
            library.bookResult = BookResult.NotFound

            val vm = viewModel(controller, library = library)

            vm.uiState.value.title shouldBe ""
            vm.uiState.value.gate shouldBe PlayerGate.Open
            controller.commands.filterIsInstance<Command.Load>().size shouldBe 1
        }
    }

    describe("unload") {

        // R20
        it("#165 TC-20: leaves when the controller drops the book") {
            val controller = FakeAudiobookController()
            val vm = viewModel(controller)

            controller.state.value = playbackState(book = playingBook(BOOK))
            vm.uiState.value.leave shouldBe false

            controller.state.value = PlaybackState()
            vm.uiState.value.leave shouldBe true
        }

        // R20
        it("#165 TC-21: leaves when another book loads") {
            val controller = FakeAudiobookController()
            val vm = viewModel(controller)

            controller.state.value = playbackState(book = playingBook(BOOK))
            controller.state.value = playbackState(book = playingBook("other-book"))

            vm.uiState.value.leave shouldBe true
        }

        // R20
        it("#165 TC-22: does not leave while the load is still landing") {
            val controller = FakeAudiobookController(playbackState(book = playingBook("other-book")))
            val vm = viewModel(controller)

            controller.state.value = PlaybackState()
            vm.uiState.value.leave shouldBe false

            controller.state.value = playbackState(book = playingBook(BOOK))
            vm.uiState.value.leave shouldBe false
        }
    }

    describe("intents") {

        // P-PLAY-3-5, C2
        it("#165 TC-24: each intent maps to its controller command") {
            val controller = FakeAudiobookController()
            val vm = viewModel(controller)
            controller.commands.clear()

            vm.onPlayPauseToggled()
            vm.onSkipBack()
            vm.onSkipForward()
            vm.onPreviousChapter()
            vm.onNextChapter()
            vm.onSeek(5.0)
            vm.onRetry()

            controller.commands shouldBe listOf(
                Command.TogglePlayPause,
                Command.SkipBack,
                Command.SkipForward,
                Command.PreviousChapter,
                Command.NextChapter,
                Command.SeekTo(5.0),
                Command.Play,
            )
        }
    }

    describe("retry") {

        // I-HLTH-3
        it("#165 TC-83: retry plays after an error (I-HLTH-3)") {
            val controller = FakeAudiobookController()
            val vm = viewModel(controller)
            controller.state.value = playbackState(
                book = playingBook(BOOK),
                error = PlaybackError.StoppedAfterRetries,
            )

            vm.uiState.value.error shouldBe PlaybackError.StoppedAfterRetries

            controller.commands.clear()
            vm.onRetry()

            controller.commands shouldBe listOf(Command.Play)
        }
    }
})
