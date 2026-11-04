package com.omnibus.omnibus.data.audiobook.playback

import com.omnibus.omnibus.data.audiobook.playback.FakeAudiobookController.Command
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class FakeAudiobookControllerTest : DescribeSpec({

    describe("FakeAudiobookController") {

        // TC-84
        it("records commands in order and never mutates state") {
            val fake = FakeAudiobookController()
            val before = playbackState(positionSeconds = 700.0)
            fake.state.value = before

            fake.load("book-1", null, 120.0, autoplay = true)
            fake.play()
            fake.pause()
            fake.togglePlayPause()
            fake.skipBack()
            fake.skipForward()
            fake.previousChapter()
            fake.nextChapter()
            fake.seekTo(50.0)
            fake.setRate(2.0, persist = true)
            fake.setSleepTimer(SleepTimerOption.After(5))
            fake.setSleepTimer(null)
            fake.stop()

            fake.commands shouldBe listOf(
                Command.Load("book-1", null, 120.0, autoplay = true),
                Command.Play,
                Command.Pause,
                Command.TogglePlayPause,
                Command.SkipBack,
                Command.SkipForward,
                Command.PreviousChapter,
                Command.NextChapter,
                Command.SeekTo(50.0),
                Command.SetRate(2.0, persist = true),
                Command.SetSleepTimer(SleepTimerOption.After(5)),
                Command.SetSleepTimer(null),
                Command.Stop,
            )
            fake.state.value shouldBe before
        }
    }
})
