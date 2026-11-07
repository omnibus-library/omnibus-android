package com.omnibus.omnibus.feature.audiobook.sleep

import com.omnibus.omnibus.data.audiobook.playback.SleepTimerOption
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerState
import com.omnibus.omnibus.data.audiobook.playback.TestAudiobookController
import com.omnibus.omnibus.data.audiobook.playback.TestAudiobookController.Command
import com.omnibus.omnibus.data.audiobook.playback.randomPlaybackState
import com.omnibus.omnibus.testing.randomDouble
import com.omnibus.omnibus.testing.randomOf
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** The sleep sheet's view model: what it maps from the controller, and what a pick sends (R10-R12). */
@OptIn(ExperimentalCoroutinesApi::class)
class SleepViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun sut(controller: TestAudiobookController = TestAudiobookController()) = SleepViewModel(controller)

    describe("the sheet's state") {
        // R10, R11, R12: the selected cell and the countdown follow the controller's state
        it("#169 TC-32: follows the controller's timer (R10-R12)") {
            val controller = TestAudiobookController()
            val vm = sut(controller)
            val minutes = randomOf(SleepTimedMinutes)
            val remaining = randomDouble(1.0, 7_200.0)

            controller.state.value = randomPlaybackState().copy(sleepTimer = SleepTimerState(SleepTimerOption.After(minutes), remaining))

            vm.uiState.value.selected shouldBe SleepChoice.Timed(minutes)
            vm.uiState.value.remainingSeconds shouldBe remaining
        }

        context("when the timer has no time left") {
            // R12: the countdown shows only while it runs
            it("#169 TC-32: hides the countdown (R12)") {
                val timer = SleepTimerState(SleepTimerOption.After(randomOf(SleepTimedMinutes)), 0.0)
                val vm = sut(TestAudiobookController(randomPlaybackState().copy(sleepTimer = timer)))

                vm.uiState.value.remainingSeconds.shouldBeNull()
            }
        }
    }

    describe("a pick") {
        // R11: each pick sends its option to the controller, and Off sends null
        it("#169 TC-33: sends its option to the controller (P-SLP-5)") {
            val controller = TestAudiobookController()
            val vm = sut(controller)

            vm.onSelect(SleepChoice.Off)
            vm.onSelect(SleepChoice.Timed(30))
            vm.onSelect(SleepChoice.EndOfChapter(1))

            controller.commands shouldBe listOf(
                Command.SetSleepTimer(null),
                Command.SetSleepTimer(SleepTimerOption.After(30)),
                Command.SetSleepTimer(SleepTimerOption.EndOfChapter),
            )
        }
    }
})
