package com.omnibus.omnibus.feature.audiobook.carmode

import com.omnibus.omnibus.data.audiobook.playback.TestAudiobookController
import com.omnibus.omnibus.data.audiobook.playback.TestAudiobookController.Command
import com.omnibus.omnibus.data.audiobook.playback.playbackState
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class CarModeViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    describe("state") {

        // P-CAR-1
        it("#170 TC-11: shows a book already loaded on the first read, and sends no command") {
            val controller = TestAudiobookController(playbackState(positionSeconds = 700.0))

            val vm = CarModeViewModel(controller)

            vm.uiState.value.title shouldBe "Chapter 2"
            controller.commands.shouldBeEmpty()
        }

        // P-CAR-3
        it("#170 TC-12: follows the controller") {
            val controller = TestAudiobookController(playbackState(isPlaying = false))
            val vm = CarModeViewModel(controller)

            controller.state.value = playbackState(isPlaying = true, positionSeconds = 700.0)

            vm.uiState.value.isPlaying shouldBe true
            vm.uiState.value.title shouldBe "Chapter 2"
        }
    }

    describe("commands") {

        // P-CAR-AC2, P-PLAY-3
        it("#170 TC-13: back 15 skips back") {
            val controller = TestAudiobookController(playbackState())

            CarModeViewModel(controller).onSkipBack()

            controller.commands shouldBe listOf(Command.SkipBack)
        }

        // P-CAR-AC2, P-PLAY-3
        it("#170 TC-14: forward 30 skips forward") {
            val controller = TestAudiobookController(playbackState())

            CarModeViewModel(controller).onSkipForward()

            controller.commands shouldBe listOf(Command.SkipForward)
        }

        // P-CAR-AC2, P-SCR-10
        it("#170 TC-15: play/pause toggles") {
            val controller = TestAudiobookController(playbackState())

            CarModeViewModel(controller).onPlayPauseToggled()

            controller.commands shouldBe listOf(Command.TogglePlayPause)
        }

        // P-CAR-AC2, P-PLAY-4
        it("#170 TC-16: previous chapter goes to the previous chapter") {
            val controller = TestAudiobookController(playbackState())

            CarModeViewModel(controller).onPreviousChapter()

            controller.commands shouldBe listOf(Command.PreviousChapter)
        }

        // P-CAR-AC2, P-PLAY-5
        it("#170 TC-17: next chapter goes to the next chapter") {
            val controller = TestAudiobookController(playbackState())

            CarModeViewModel(controller).onNextChapter()

            controller.commands shouldBe listOf(Command.NextChapter)
        }
    }
})
