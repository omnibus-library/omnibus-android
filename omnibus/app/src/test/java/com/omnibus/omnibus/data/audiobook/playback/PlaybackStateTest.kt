package com.omnibus.omnibus.data.audiobook.playback

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

class PlaybackStateTest : DescribeSpec({

    describe("PlaybackState()") {

        // TC-21
        it("is the idle state (C1)") {
            val state = PlaybackState()

            state.book.shouldBeNull()
            state.chapters shouldBe emptyList()
            state.positionSeconds shouldBe 0.0
            state.durationSeconds shouldBe 0.0
            state.currentChapterIndex.shouldBeNull()
            state.isPlaying shouldBe false
            state.isLoading shouldBe false
            state.isBuffering shouldBe false
            state.rate shouldBe 1.0
            state.error.shouldBeNull()
            state.sleepTimer.shouldBeNull()
        }
    }

    describe("PlaybackError") {

        // TC-21
        it("variants (C1)") {
            PlaybackError.NoAudioParts shouldBeSameInstanceAs PlaybackError.NoAudioParts
            PlaybackError.StoppedAfterRetries shouldBeSameInstanceAs PlaybackError.StoppedAfterRetries

            PlaybackError.Server("a") shouldBe PlaybackError.Server("a")
            PlaybackError.Server("a") shouldNotBe PlaybackError.Server("b")
            PlaybackError.Server(null) shouldNotBe PlaybackError.TimelineFailed

            val all: List<PlaybackError> = listOf(
                PlaybackError.NoAudioParts,
                PlaybackError.TimelineFailed,
                PlaybackError.PartUnreadable,
                PlaybackError.Server("a"),
                PlaybackError.StoppedAfterRetries,
            )
            all.distinct().size shouldBe 5
        }
    }
})
