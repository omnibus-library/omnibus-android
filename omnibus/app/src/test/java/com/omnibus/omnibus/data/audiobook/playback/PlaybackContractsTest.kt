package com.omnibus.omnibus.data.audiobook.playback

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

class PlaybackContractsTest : DescribeSpec({

    describe("contracts") {

        // TC-81
        it("C4 and C7 declarations match the epic plan") {
            UnloadReason.entries.toList() shouldBe listOf(UnloadReason.Stop, UnloadReason.SwitchBook)

            SleepTimerOption.After(5) shouldBe SleepTimerOption.After(5)
            SleepTimerOption.After(5) shouldNotBe SleepTimerOption.After(6)
            SleepTimerOption.EndOfChapter shouldBeSameInstanceAs SleepTimerOption.EndOfChapter
            SleepTimerState(SleepTimerOption.After(5), 300.0) shouldBe SleepTimerState(SleepTimerOption.After(5), 300.0)

            // Every PlaybackListener method has a default, so an empty implementation compiles and does nothing.
            val listener = object : PlaybackListener {}
            listener.onUnload(PlaybackState(), UnloadReason.Stop)
            listener.onSettled(PlaybackState(), 0.0)
            listener.onEnded(PlaybackState())
        }
    }
})
