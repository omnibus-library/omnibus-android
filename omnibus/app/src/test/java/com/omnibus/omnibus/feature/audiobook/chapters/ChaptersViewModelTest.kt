package com.omnibus.omnibus.feature.audiobook.chapters

import com.omnibus.omnibus.data.audiobook.playback.FakeAudiobookController
import com.omnibus.omnibus.data.audiobook.playback.FakeAudiobookController.Command
import com.omnibus.omnibus.data.audiobook.playback.chapters
import com.omnibus.omnibus.data.audiobook.playback.playbackState
import com.omnibus.omnibus.testing.CollectingScope
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

class ChaptersViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    describe("the chapters sheet's view model") {

        // P-CHP-5: a tap seeks to the chapter's start and plays from there
        it("seeks to the chosen chapter's start and plays") {
            val controller = FakeAudiobookController(playbackState(chapters = chapters(600.0, 1200.0, 900.0)))

            ChaptersViewModel(controller).onChapterChosen(2)

            controller.commands shouldBe listOf(Command.SeekTo(1800.0), Command.Play)
        }

        // P-CHP-5: playback that is already running is not restarted
        it("only seeks when the book is already playing") {
            val controller = FakeAudiobookController(
                playbackState(chapters = chapters(600.0, 1200.0), positionSeconds = 700.0, isPlaying = true),
            )

            ChaptersViewModel(controller).onChapterChosen(0)

            controller.commands shouldBe listOf(Command.SeekTo(0.0))
        }

        // P-CHP-5: an index with no chapter (the book was unloaded under the sheet) does nothing
        it("ignores an index that has no chapter") {
            val controller = FakeAudiobookController(playbackState(chapters = chapters(600.0)))
            val vm = ChaptersViewModel(controller)

            vm.onChapterChosen(1)
            vm.onChapterChosen(-1)

            controller.commands shouldBe emptyList()
        }

        // P-CHP-3: the sheet's state follows the player
        it("follows the player's position and chapters") {
            val book = chapters(1000.0, 1000.0)
            val controller = FakeAudiobookController(playbackState(chapters = book, positionSeconds = 100.0))
            val collecting = CollectingScope()
            try {
                val vm = ChaptersViewModel(controller)
                collecting.collect(vm.uiState)

                vm.uiState.value.currentIndex shouldBe 0
                vm.uiState.value.currentProgress shouldBe 0.1f

                controller.state.value = playbackState(chapters = book, positionSeconds = 1500.0)

                vm.uiState.value.currentIndex shouldBe 1
                vm.uiState.value.currentProgress shouldBe 0.5f
            } finally {
                collecting.cancel()
            }
        }
    }
})
