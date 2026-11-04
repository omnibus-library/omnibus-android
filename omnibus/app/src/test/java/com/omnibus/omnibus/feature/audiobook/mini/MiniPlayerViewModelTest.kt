package com.omnibus.omnibus.feature.audiobook.mini

import com.omnibus.omnibus.data.audiobook.playback.FakeAudiobookController
import com.omnibus.omnibus.data.audiobook.playback.FakeAudiobookController.Command
import com.omnibus.omnibus.data.audiobook.playback.PlaybackError
import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.chapters
import com.omnibus.omnibus.data.audiobook.playback.playbackState
import com.omnibus.omnibus.data.audiobook.playback.playingBook
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class MiniPlayerViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    describe("first frame and visibility") {

        // P-MINI-1
        it("#172 TC-15: a book already loaded when the screen opens is visible on the first read, with no command") {
            val controller = FakeAudiobookController(playbackState())

            val vm = MiniPlayerViewModel(controller)

            vm.uiState.value.visible shouldBe true
            controller.commands.shouldBeEmpty()
        }

        // P-MINI-1, P-MINI-2
        it("#172 TC-16: the bar follows the controller's book and sends nothing on show or hide") {
            val controller = FakeAudiobookController()
            val vm = MiniPlayerViewModel(controller)
            vm.uiState.value.visible shouldBe false

            controller.state.value = playbackState()
            vm.uiState.value.visible shouldBe true

            controller.state.value = PlaybackState()
            vm.uiState.value.visible shouldBe false

            controller.commands.shouldBeEmpty()
        }
    }

    describe("controls") {

        // P-MINI-AC2
        it("#172 TC-17: skip back calls the controller's skip back") {
            val controller = FakeAudiobookController(playbackState())

            MiniPlayerViewModel(controller).onSkipBack()

            controller.commands shouldBe listOf(Command.SkipBack)
        }

        // P-MINI-AC2
        it("#172 TC-18: play/pause calls the controller's toggle") {
            val controller = FakeAudiobookController(playbackState())

            MiniPlayerViewModel(controller).onPlayPauseToggled()

            controller.commands shouldBe listOf(Command.TogglePlayPause)
        }
    }

    describe("stop") {

        // P-MINI-3, P-MINI-AC3
        it("#172 TC-19: stop calls the controller's stop exactly once") {
            val controller = FakeAudiobookController(playbackState())

            MiniPlayerViewModel(controller).onStop()

            controller.commands shouldBe listOf(Command.Stop)
        }

        // P-MINI-3, P-MINI-AC3
        it("#172 TC-20: stop hides the bar at once, while the controller still reports the book") {
            val controller = FakeAudiobookController(playbackState())
            val vm = MiniPlayerViewModel(controller)

            vm.onStop()

            vm.uiState.value.visible shouldBe false
            controller.state.value.book shouldBe playingBook()
        }

        // P-MINI-3, P-MINI-AC3
        it("#172 TC-21: the bar stays hidden while the controller reports the same book") {
            val controller = FakeAudiobookController(playbackState())
            val vm = MiniPlayerViewModel(controller)
            vm.onStop()

            controller.state.value = playbackState(positionSeconds = 10.0, isPlaying = true)
            vm.uiState.value.visible shouldBe false

            controller.state.value = playbackState(positionSeconds = 11.0, isPlaying = false)
            vm.uiState.value.visible shouldBe false
        }

        // P-MINI-3, P-MINI-AC3
        it("#172 TC-22: after idle, a different book shows the bar again") {
            val controller = FakeAudiobookController(playbackState())
            val vm = MiniPlayerViewModel(controller)
            vm.onStop()

            controller.state.value = PlaybackState()
            controller.state.value = playbackState(book = playingBook("other"))

            vm.uiState.value.visible shouldBe true
            vm.uiState.value.bookUuid shouldBe "other"
        }

        // P-MINI-3, P-MINI-AC3
        it("#172 TC-23: after idle, the same book loaded again shows the bar again") {
            val controller = FakeAudiobookController(playbackState())
            val vm = MiniPlayerViewModel(controller)
            vm.onStop()

            controller.state.value = PlaybackState()
            controller.state.value = playbackState()

            vm.uiState.value.visible shouldBe true
        }

        // P-MINI-3, P-MINI-AC3
        it("#172 TC-24: a different book without an idle state shows the bar") {
            val controller = FakeAudiobookController(playbackState())
            val vm = MiniPlayerViewModel(controller)
            vm.onStop()

            controller.state.value = playbackState(book = playingBook("other"))

            vm.uiState.value.visible shouldBe true
            vm.uiState.value.bookUuid shouldBe "other"
        }

        // P-MINI-3
        it("#172 TC-25: stop with nothing loaded does nothing, and a later load still shows") {
            val controller = FakeAudiobookController()
            val vm = MiniPlayerViewModel(controller)

            vm.onStop()

            controller.commands.shouldBeEmpty()
            vm.uiState.value.visible shouldBe false

            controller.state.value = playbackState()
            vm.uiState.value.visible shouldBe true
        }
    }

    describe("loading and errors") {

        // P-MINI-6
        it("#172 TC-26: a load started with autoplay shows the bar before any player screen opens") {
            val controller = FakeAudiobookController()
            val vm = MiniPlayerViewModel(controller)

            controller.state.value = playbackState(book = playingBook(title = ""), chapters = emptyList(), isLoading = true)

            vm.uiState.value.visible shouldBe true
            vm.uiState.value.title shouldBe ""
            vm.uiState.value.subtitle shouldBe "An Author"
        }

        // P-MINI-4
        it("#172 TC-27: an error keeps the bar, and its play button still sends the toggle") {
            val controller = FakeAudiobookController(playbackState(error = PlaybackError.StoppedAfterRetries))
            val vm = MiniPlayerViewModel(controller)

            vm.uiState.value.visible shouldBe true
            vm.onPlayPauseToggled()

            controller.commands shouldBe listOf(Command.TogglePlayPause)
        }
    }
})
