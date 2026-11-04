package com.omnibus.omnibus.data.audiobook.playback

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class PlaybackStateFixturesTest : DescribeSpec({

    describe("fixtures") {

        it("chapters are contiguous") {
            val chapters = chapters(600.0, 1200.0, 900.0)

            chapters.map { it.startSeconds } shouldBe listOf(0.0, 600.0, 1800.0)
            chapters.map { it.durationSeconds } shouldBe listOf(600.0, 1200.0, 900.0)
            chapters.map { it.ordinal } shouldBe listOf(1L, 2L, 3L)
            chapters.map { it.title } shouldBe listOf("Chapter 1", "Chapter 2", "Chapter 3")
        }

        it("playbackState derives the chapter index") {
            val state = playbackState(positionSeconds = 700.0)

            state.currentChapterIndex shouldBe 1
            state.durationSeconds shouldBe 2700.0
        }

        it("no-chapter state") {
            val state = noChapterState()

            state.chapters shouldBe emptyList()
            state.currentChapterIndex.shouldBeNull()
            state.durationSeconds shouldBe 3600.0
        }

        it("multi-part manifest") {
            val manifest = multiPartManifest()

            manifest.bookFileId shouldBe 11L
            manifest.parts.map { it.ordinal } shouldBe listOf(1L, 2L, 3L)
            manifest.parts.map { it.durationSeconds } shouldBe listOf(1000.0, 1500.0, 500.0)
            manifest.parts[1].url shouldBe "https://server.test/api/audiobooks/book-1/parts/2?file_id=11"
            manifest.totalDurationSeconds shouldBe 3000.0

            // The middle chapter starts in part 1 and ends in part 2, and the last one crosses into part 3.
            val middle = manifest.chapters[1]
            (middle.startSeconds < 1000.0 && 1000.0 < middle.startSeconds + middle.durationSeconds) shouldBe true
            val last = manifest.chapters[2]
            (last.startSeconds < 2500.0 && 2500.0 < last.startSeconds + last.durationSeconds) shouldBe true
        }
    }
})
