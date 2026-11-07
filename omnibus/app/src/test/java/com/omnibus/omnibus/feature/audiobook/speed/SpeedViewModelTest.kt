package com.omnibus.omnibus.feature.audiobook.speed

import com.omnibus.omnibus.data.audiobook.playback.TestAudiobookController
import com.omnibus.omnibus.data.audiobook.playback.TestAudiobookController.Command
import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class SpeedViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    /** A controller at [rate], then a view model over it, so the sheet starts at that rate. */
    fun viewModelAt(rate: Double): Pair<TestAudiobookController, SpeedViewModel> {
        val fake = TestAudiobookController(PlaybackState(rate = rate))
        return fake to SpeedViewModel(fake)
    }

    describe("the sheet's state") {

        // R12: the sheet starts at the player's rate, with the preset and bounds that rate implies
        it("#168 TC-62: the initial state mirrors the controller's rate at each bound and preset (R12)") {
            val (_, atOne) = viewModelAt(1.0)
            atOne.uiState.value shouldBe SpeedUIState(rate = 1.0, selectedPreset = 1.0, canSlower = true, canFaster = true)

            val (_, at125) = viewModelAt(1.25)
            at125.uiState.value shouldBe SpeedUIState(rate = 1.25, selectedPreset = null, canSlower = true, canFaster = true)

            val (_, atMin) = viewModelAt(0.5)
            atMin.uiState.value shouldBe SpeedUIState(rate = 0.5, selectedPreset = 0.5, canSlower = false, canFaster = true)

            val (_, atMax) = viewModelAt(3.0)
            atMax.uiState.value shouldBe SpeedUIState(rate = 3.0, selectedPreset = null, canSlower = true, canFaster = false)
        }

        // R12: an external rate change (the load-time rate() answer) updates the sheet without an intent
        it("#168 TC-63: an external rate change updates the sheet with no intent call (R12)") {
            val (fake, vm) = viewModelAt(1.0)

            fake.state.value = fake.state.value.copy(rate = 1.75)

            vm.uiState.value.rate shouldBe 1.75
            vm.uiState.value.selectedPreset shouldBe null
            fake.commands shouldBe emptyList()
        }

        // R12: the intents go through setRate, and the state is republished synchronously
        it("#168 TC-74: slider values are snapped to the grid and bounded before setRate (R12, R9)") {
            val (fake, vm) = viewModelAt(1.0)

            vm.onSliderMoved(5f)
            vm.onSliderReleased()
            fake.commands.last() shouldBe Command.SetRate(3.0, true)
            fake.state.value = fake.state.value.copy(rate = 3.0)

            vm.onSliderMoved(-1f)
            fake.commands.last() shouldBe Command.SetRate(0.5, false)
            vm.onSliderReleased()

            vm.onSliderMoved(Float.NaN)
            fake.commands.last() shouldBe Command.SetRate(1.0, false)
        }
    }

    describe("presets") {

        // R4: a preset tap persists at once, including the preset already selected (A4)
        it("#168 TC-64: a preset tap persists at once, including the selected preset (R4, R12)") {
            val (fake, vm) = viewModelAt(1.0)

            vm.onPresetChosen(1.5)
            fake.state.value = fake.state.value.copy(rate = 1.5)
            vm.onPresetChosen(1.5)

            fake.commands shouldBe listOf(Command.SetRate(1.5, true), Command.SetRate(1.5, true))
        }
    }

    describe("the slider drag") {

        // R6: live values are snapped and sent without persisting; a duplicate snap sends nothing
        it("#168 TC-65: a drag sends snapped live values, not persisted, and skips repeats (R6, R12)") {
            val (fake, vm) = viewModelAt(1.0)

            vm.onSliderMoved(1.1f)
            vm.onSliderMoved(1.1f)
            vm.onSliderMoved(1.12f)
            vm.onSliderMoved(1.15f)

            fake.commands shouldBe listOf(Command.SetRate(1.1, false), Command.SetRate(1.15, false))
            vm.uiState.value.rate shouldBe 1.15
        }

        // R6: the release persists the last snapped value exactly once
        it("#168 TC-66: the release persists the last snapped value once (R6, R12)") {
            val (fake, vm) = viewModelAt(1.0)

            vm.onSliderMoved(1.5f)
            vm.onSliderReleased()
            vm.onSliderReleased()
            fake.state.value = fake.state.value.copy(rate = 1.5)
            vm.onSliderReleased()

            fake.commands shouldBe listOf(Command.SetRate(1.5, false), Command.SetRate(1.5, true))
            vm.uiState.value.rate shouldBe 1.5
        }

        // R6, I-SPD-2: dismissing mid-drag persists the live value once, and later closes do nothing
        it("#168 TC-68: a dismiss mid-drag persists once and later closes do nothing (R6, I-SPD-2)") {
            val (fake, vm) = viewModelAt(1.0)

            vm.onSliderMoved(1.8f)
            vm.onSheetClosed()
            vm.onSheetClosed()

            fake.commands shouldBe listOf(Command.SetRate(1.8, false), Command.SetRate(1.8, true))

            fake.commands.clear()
            vm.onSliderMoved(1.2f)
            vm.onSliderReleased()
            vm.onSheetClosed()
            fake.commands shouldBe listOf(Command.SetRate(1.2, false), Command.SetRate(1.2, true))
        }

        // R6: a drag that is still open keeps the live value even when the player's rate changes under it
        it("#168 TC-73: a live drag wins over an external rate until it is released (R12)") {
            val (fake, vm) = viewModelAt(1.0)

            vm.onSliderMoved(1.2f)
            fake.state.value = fake.state.value.copy(rate = 2.5)
            vm.uiState.value.rate shouldBe 1.2

            vm.onSliderReleased()
            vm.uiState.value.rate shouldBe 2.5
        }
    }

    describe("the step buttons") {

        // R7, P-SPD-4: each step persists from the current rate, and off-grid rates snap on the step
        it("#168 TC-69: slower and faster step from the current rate and persist (R7, R12)") {
            val (fake, vm) = viewModelAt(1.0)
            vm.onSlower()
            vm.onFaster()
            fake.commands shouldBe listOf(Command.SetRate(0.95, true), Command.SetRate(1.05, true))

            // Off-grid 1.33 snaps on the step: slower lands on 1.30, faster on 1.40 (fresh view model per row)
            val (slowFake, slowVm) = viewModelAt(1.33)
            slowVm.onSlower()
            slowFake.commands shouldBe listOf(Command.SetRate(1.3, true))

            val (fastFake, fastVm) = viewModelAt(1.33)
            fastVm.onFaster()
            fastFake.commands shouldBe listOf(Command.SetRate(1.4, true))

            // Mid-drag, a step starts from the live value and clears the drag, so closing adds nothing
            val (dragFake, dragVm) = viewModelAt(1.0)
            dragVm.onSliderMoved(1.5f)
            dragVm.onFaster()
            dragVm.onSheetClosed()
            dragFake.commands shouldBe listOf(Command.SetRate(1.5, false), Command.SetRate(1.55, true))
        }

        // R7, P-SPD-4: at a bound the step makes no call, and the flags report the bound
        it("#168 TC-70: a step at a bound sends nothing and the bound flags report it (R7, R12)") {
            val (fake, vm) = viewModelAt(0.5)
            vm.onSlower()
            fake.commands shouldBe emptyList()
            vm.uiState.value.canSlower shouldBe false
            vm.uiState.value.canFaster shouldBe true

            val (topFake, topVm) = viewModelAt(3.0)
            topVm.onFaster()
            topFake.commands shouldBe emptyList()
            topVm.uiState.value.canFaster shouldBe false
            topVm.uiState.value.canSlower shouldBe true
        }

        // R7: twenty faster taps land exactly on 2.0, with the fake applying each change
        it("#168 TC-71: twenty faster taps from 1.0 land exactly on 2.0 (R7, I-SPD-1)") {
            val (fake, vm) = viewModelAt(1.0)

            repeat(20) {
                vm.onFaster()
                fake.state.value = fake.state.value.copy(rate = (fake.commands.last() as Command.SetRate).rate)
            }

            fake.state.value.rate shouldBe 2.0
            vm.uiState.value.rate shouldBe 2.0
            fake.commands.map { (it as Command.SetRate).rate } shouldBe (21..40).map { it / 20.0 }
        }
    }
})
