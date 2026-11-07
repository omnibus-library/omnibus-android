package com.omnibus.omnibus.feature.audiobook.chapters

import app.cash.turbine.test
import com.omnibus.omnibus.data.audiobook.playback.TestAudiobookController
import com.omnibus.omnibus.data.audiobook.playback.TestAudiobookController.Command
import com.omnibus.omnibus.data.audiobook.playback.chapters
import com.omnibus.omnibus.data.audiobook.playback.playbackState
import com.omnibus.omnibus.data.audiobook.playback.randomPlaybackState
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

class ChaptersViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun sut(controller: TestAudiobookController = TestAudiobookController()) = ChaptersViewModel(controller)

    describe("choosing a chapter") {
        context("when the book is paused") {
            // P-CHP-5: a tap seeks to the chapter's start and plays from there
            it("seeks to the chapter's start, then plays") {
                val state = randomPlaybackState().copy(chapters = chapters(600.0, 1200.0, 900.0), isPlaying = false)
                val controller = TestAudiobookController(state)

                sut(controller).onChapterChosen(2)

                controller.commands shouldBe listOf(Command.SeekTo(1800.0), Command.Play)
            }
        }

        context("when the book is playing") {
            // P-CHP-5: playback that is already running is not restarted
            it("only seeks") {
                val state = randomPlaybackState().copy(chapters = chapters(600.0, 1200.0), isPlaying = true)
                val controller = TestAudiobookController(state)

                sut(controller).onChapterChosen(1)

                controller.commands shouldBe listOf(Command.SeekTo(600.0))
            }
        }

        context("when the index has no chapter") {
            // P-CHP-5: an index with no chapter (the book was unloaded under the sheet) does nothing
            it("sends nothing") {
                val controller = TestAudiobookController(randomPlaybackState().copy(chapters = chapters(600.0)))
                val vm = sut(controller)

                vm.onChapterChosen(1)
                vm.onChapterChosen(-1)

                controller.commands.shouldBeEmpty()
            }
        }
    }

    describe("the sheet's state") {
        // P-CHP-3: the sheet's state follows the player
        it("follows the player's position") {
            val book = chapters(1000.0, 1000.0)
            val controller = TestAudiobookController(playbackState(chapters = book, positionSeconds = 100.0))

            sut(controller).uiState.test {
                awaitItem().currentIndex shouldBe 0

                controller.state.value = playbackState(chapters = book, positionSeconds = 1500.0)

                awaitItem().currentIndex shouldBe 1
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
})
