package com.omnibus.omnibus.feature.audiobook.carmode

import com.omnibus.omnibus.data.audiobook.playback.TestAudiobookController
import com.omnibus.omnibus.data.audiobook.playback.TestAudiobookController.Command
import com.omnibus.omnibus.data.audiobook.playback.randomPlaybackState
import com.omnibus.omnibus.data.audiobook.playback.randomPlayingBook
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class CarModeViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun sut(controller: TestAudiobookController = TestAudiobookController()) = CarModeViewModel(controller)

    describe("the state") {
        // P-CAR-3
        it("#170 TC-12: follows the controller") {
            val controller = TestAudiobookController()
            val vm = sut(controller)
            val state = randomPlaybackState().copy(book = randomPlayingBook())

            controller.state.value = state

            vm.uiState.value shouldBe carModeUIState(state)
        }
    }

    describe("the tiles") {
        // P-CAR-AC2, P-PLAY-3, P-PLAY-4, P-PLAY-5, P-SCR-10
        it("#170 TC-13 to TC-17: each tile sends its command") {
            checkAll(
                Exhaustive.of(
                    CarModeViewModel::onSkipBack to Command.SkipBack,
                    CarModeViewModel::onSkipForward to Command.SkipForward,
                    CarModeViewModel::onPlayPauseToggled to Command.TogglePlayPause,
                    CarModeViewModel::onPreviousChapter to Command.PreviousChapter,
                    CarModeViewModel::onNextChapter to Command.NextChapter,
                ),
            ) { (tile, command) ->
                val controller = TestAudiobookController(randomPlaybackState())

                tile(sut(controller))

                controller.commands shouldBe listOf(command)
            }
        }
    }
})
