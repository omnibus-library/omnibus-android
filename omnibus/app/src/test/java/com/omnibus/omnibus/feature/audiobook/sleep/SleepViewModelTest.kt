package com.omnibus.omnibus.feature.audiobook.sleep

import com.omnibus.omnibus.data.audiobook.playback.FakeAudiobookController
import com.omnibus.omnibus.data.audiobook.playback.FakeAudiobookController.Command
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerOption
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerState
import com.omnibus.omnibus.data.audiobook.playback.noChapterState
import com.omnibus.omnibus.data.audiobook.playback.playbackState
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

    describe("the sheet's state") {
        // R10, R11, R12: the choices, the selected cell and the countdown follow the controller's state
        it("#169 TC-32: choices, selection and countdown follow the controller, with the countdown only while it runs (R10-R12)") {
            val fake = FakeAudiobookController(playbackState(positionSeconds = 100.0))
            val vm = SleepViewModel(fake)
            vm.uiState.value.choices.size shouldBe 9
            vm.uiState.value.selected shouldBe SleepChoice.Off
            vm.uiState.value.remainingSeconds.shouldBeNull()

            fake.state.value = playbackState(positionSeconds = 100.0, sleepTimer = SleepTimerState(SleepTimerOption.After(15), 899.0))
            vm.uiState.value.remainingSeconds shouldBe 899.0
            vm.uiState.value.selected shouldBe SleepChoice.Timed(15)

            fake.state.value = playbackState(positionSeconds = 100.0, sleepTimer = SleepTimerState(SleepTimerOption.After(15), 0.0))
            vm.uiState.value.remainingSeconds.shouldBeNull()

            fake.state.value = playbackState(positionSeconds = 100.0, sleepTimer = null)
            vm.uiState.value.selected shouldBe SleepChoice.Off

            fake.state.value = noChapterState()
            vm.uiState.value.choices.size shouldBe 8
        }
    }

    describe("a pick") {
        // R11: each pick sends its option to the controller, and Off sends null
        it("#169 TC-33: each pick sends its option to the controller (P-SLP-5)") {
            val fake = FakeAudiobookController(playbackState(positionSeconds = 100.0))
            val vm = SleepViewModel(fake)
            vm.onSelect(SleepChoice.Off)
            vm.onSelect(SleepChoice.Timed(30))
            vm.onSelect(SleepChoice.EndOfChapter(1))
            fake.commands shouldBe listOf(
                Command.SetSleepTimer(null),
                Command.SetSleepTimer(SleepTimerOption.After(30)),
                Command.SetSleepTimer(SleepTimerOption.EndOfChapter),
            )
        }
    }
})
