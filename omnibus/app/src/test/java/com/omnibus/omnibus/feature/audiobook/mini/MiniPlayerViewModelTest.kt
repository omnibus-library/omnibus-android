package com.omnibus.omnibus.feature.audiobook.mini

import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.TestAudiobookController
import com.omnibus.omnibus.data.audiobook.playback.TestAudiobookController.Command
import com.omnibus.omnibus.data.audiobook.playback.randomPlaybackState
import com.omnibus.omnibus.data.audiobook.playback.randomPlayingBook
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

private fun loadedState(): PlaybackState = randomPlaybackState().copy(book = randomPlayingBook())

@OptIn(ExperimentalCoroutinesApi::class)
class MiniPlayerViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun sut(controller: TestAudiobookController = TestAudiobookController()) = MiniPlayerViewModel(controller)

    describe("following the controller's book") {
        context("when the book is unloaded") {
            // P-MINI-1, P-MINI-2
            it("#172 TC-16: hides the bar") {
                val controller = TestAudiobookController(loadedState())
                val vm = sut(controller)

                controller.state.value = PlaybackState()

                vm.uiState.value.visible shouldBe false
            }
        }
    }

    describe("the controls") {
        // P-MINI-AC2, P-MINI-3, P-MINI-AC3
        it("#172 TC-17 TC-18 TC-19: each control sends its command") {
            checkAll(
                Exhaustive.of(
                    MiniPlayerViewModel::onSkipBack to Command.SkipBack,
                    MiniPlayerViewModel::onPlayPauseToggled to Command.TogglePlayPause,
                    MiniPlayerViewModel::onStop to Command.Stop,
                ),
            ) { (control, command) ->
                val controller = TestAudiobookController(loadedState())

                control(sut(controller))

                controller.commands shouldBe listOf(command)
            }
        }
    }

    describe("stopping") {
        context("when the controller still reports the stopped book") {
            // P-MINI-3, P-MINI-AC3
            it("#172 TC-20: hides the bar at once") {
                val vm = sut(TestAudiobookController(loadedState()))

                vm.onStop()

                vm.uiState.value.visible shouldBe false
            }

            // P-MINI-3, P-MINI-AC3
            it("#172 TC-21: keeps the bar hidden through later updates") {
                val controller = TestAudiobookController(loadedState())
                val vm = sut(controller)
                vm.onStop()

                controller.state.value = controller.state.value.copy(positionSeconds = 10.0, isPlaying = true)

                vm.uiState.value.visible shouldBe false
            }
        }

        context("when the controller has gone idle") {
            // P-MINI-3, P-MINI-AC3
            it("#172 TC-23: shows the bar for the same book loaded again") {
                val loaded = loadedState()
                val controller = TestAudiobookController(loaded)
                val vm = sut(controller)
                vm.onStop()

                controller.state.value = PlaybackState()
                controller.state.value = loaded

                vm.uiState.value.visible shouldBe true
            }
        }

        context("when nothing is loaded") {
            // P-MINI-3
            it("#172 TC-25: sends nothing") {
                val controller = TestAudiobookController()

                sut(controller).onStop()

                controller.commands.shouldBeEmpty()
            }
        }
    }
})
