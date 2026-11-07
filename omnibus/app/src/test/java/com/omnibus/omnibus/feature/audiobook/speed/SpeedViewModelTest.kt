package com.omnibus.omnibus.feature.audiobook.speed

import com.omnibus.omnibus.data.audiobook.playback.TestAudiobookController
import com.omnibus.omnibus.data.audiobook.playback.TestAudiobookController.Command
import com.omnibus.omnibus.data.audiobook.playback.randomPlaybackState
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

private fun controllerAt(rate: Double) = TestAudiobookController(randomPlaybackState().copy(rate = rate))

@OptIn(ExperimentalCoroutinesApi::class)
class SpeedViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun sut(controller: TestAudiobookController = TestAudiobookController()) = SpeedViewModel(controller)

    describe("the sheet's state") {
        // R12: the sheet starts at the player's rate, with the preset and bounds that rate implies
        it("#168 TC-62: mirrors the controller's rate at a preset, between presets, at each bound (R12)") {
            checkAll(
                Exhaustive.of(
                    SpeedUIState(rate = 1.0, selectedPreset = 1.0, canSlower = true, canFaster = true),
                    SpeedUIState(rate = 1.25, selectedPreset = null, canSlower = true, canFaster = true),
                    SpeedUIState(rate = 0.5, selectedPreset = 0.5, canSlower = false, canFaster = true),
                    SpeedUIState(rate = 3.0, selectedPreset = null, canSlower = true, canFaster = false),
                ),
            ) { expected ->
                sut(controllerAt(expected.rate)).uiState.value shouldBe expected
            }
        }

        // R12: an external rate change (the load-time rate() answer) updates the sheet without an intent
        it("#168 TC-63: follows an external rate change (R12)") {
            val controller = controllerAt(1.0)
            val vm = sut(controller)

            controller.state.value = controller.state.value.copy(rate = 1.75)

            vm.uiState.value.rate shouldBe 1.75
        }
    }

    describe("choosing a preset") {
        // R4: a preset tap persists at once, including the preset already selected (A4)
        it("#168 TC-64: persists at once, including the preset already selected (R4, R12)") {
            val controller = controllerAt(1.5)

            sut(controller).onPresetChosen(1.5)

            controller.commands shouldBe listOf(Command.SetRate(1.5, true))
        }
    }

    describe("dragging the slider") {
        // R6: live values are snapped and sent without persisting; a duplicate snap sends nothing
        it("#168 TC-65: sends snapped live values unpersisted, skipping repeats (R6, R12)") {
            val controller = controllerAt(1.0)
            val vm = sut(controller)

            vm.onSliderMoved(1.1f)
            vm.onSliderMoved(1.12f)
            vm.onSliderMoved(1.15f)

            controller.commands shouldBe listOf(Command.SetRate(1.1, false), Command.SetRate(1.15, false))
        }

        // R6, I-SPD-2: the release, or dismissing mid-drag, persists the last snapped value exactly once
        it("#168 TC-66 TC-68: persists the last snapped value once, on release or on the sheet closing (R6, I-SPD-2)") {
            checkAll(Exhaustive.of(SpeedViewModel::onSliderReleased, SpeedViewModel::onSheetClosed)) { end ->
                val controller = controllerAt(1.0)
                val vm = sut(controller)
                vm.onSliderMoved(1.5f)
                controller.commands.clear()

                end(vm)
                end(vm)

                controller.commands shouldBe listOf(Command.SetRate(1.5, true))
            }
        }

        context("when the player's rate changes under the drag") {
            // R6: a drag that is still open keeps the live value even when the player's rate changes under it
            it("#168 TC-73: shows the live value until the release (R12)") {
                val controller = controllerAt(1.0)
                val vm = sut(controller)
                vm.onSliderMoved(1.2f)

                controller.state.value = controller.state.value.copy(rate = 2.5)
                vm.uiState.value.rate shouldBe 1.2

                vm.onSliderReleased()
                vm.uiState.value.rate shouldBe 2.5
            }
        }
    }

    describe("the step buttons") {
        // R7, P-SPD-4: each step persists from the current rate
        it("#168 TC-69: step from the current rate, persisting (R7, R12)") {
            val controller = controllerAt(1.0)
            val vm = sut(controller)

            vm.onSlower()
            vm.onFaster()

            controller.commands shouldBe listOf(Command.SetRate(0.95, true), Command.SetRate(1.05, true))
        }

        context("when a drag is open") {
            // R7: a step starts from the live value and clears the drag, so closing adds nothing
            it("#168 TC-69: step from the live value, ending the drag (R7, R12)") {
                val controller = controllerAt(1.0)
                val vm = sut(controller)
                vm.onSliderMoved(1.5f)
                controller.commands.clear()

                vm.onFaster()
                vm.onSheetClosed()

                controller.commands shouldBe listOf(Command.SetRate(1.55, true))
            }
        }

        context("when the rate is at a bound") {
            // R7, P-SPD-4: at a bound the step makes no call
            it("#168 TC-70: send nothing past the lower or upper bound (R7, R12)") {
                checkAll(Exhaustive.of(0.5 to SpeedViewModel::onSlower, 3.0 to SpeedViewModel::onFaster)) { (rate, step) ->
                    val controller = controllerAt(rate)

                    step(sut(controller))

                    controller.commands.shouldBeEmpty()
                }
            }
        }
    }
})
