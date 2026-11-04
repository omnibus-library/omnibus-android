package com.omnibus.omnibus.data.audiobook.playback

import com.omnibus.omnibus.data.audiobook.AudioPart
import com.omnibus.omnibus.data.audiobook.AudiobookManifest
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

private val headers = mapOf("Authorization" to "Bearer secret", "Accept" to "*/*")

class AudioSourcePlanTest : DescribeSpec({

    describe("placeholderMillis") {

        // TC-93
        it("rounds seconds to millis (I-LOAD-4)") {
            placeholderMillis(100.5) shouldBe 100_500L
        }

        // TC-93
        it("rejects non-positive and non-finite durations (P-PLAY-10)") {
            // 0.0004 s rounds to 0 ms, so only a rounded value above 0 is valid.
            placeholderMillis(0.0004).shouldBeNull()
            placeholderMillis(0.0).shouldBeNull()
            placeholderMillis(-1.0).shouldBeNull()
            placeholderMillis(Double.NaN).shouldBeNull()
            placeholderMillis(Double.POSITIVE_INFINITY).shouldBeNull()
        }
    }

    describe("planAudioSource") {

        // TC-93
        it("joins parts in ordinal order (I-LOAD-4)") {
            val plan = planAudioSource("book-1", multiPartManifest())

            plan shouldBe SourcePlan.Ready(
                AudioSource.Parts(
                    mediaId = "book-1",
                    parts = listOf(
                        SourcePart("https://server.test/api/audiobooks/book-1/parts/1?file_id=11", 1_000_000L, "audio/mpeg"),
                        SourcePart("https://server.test/api/audiobooks/book-1/parts/2?file_id=11", 1_500_000L, "audio/mpeg"),
                        SourcePart("https://server.test/api/audiobooks/book-1/parts/3?file_id=11", 500_000L, "audio/mpeg"),
                    ),
                    headers = headers,
                ),
            )
        }

        // TC-93
        it("a bad duration is TimelineFailed (P-PLAY-10)") {
            val manifest = AudiobookManifest.Direct(
                bookFileId = 7L,
                parts = listOf(AudioPart(1, "https://server.test/part", 0.0, "audio/mpeg")),
                totalDurationSeconds = 0.0,
                chapters = emptyList(),
                requestHeaders = headers,
            )

            planAudioSource("book-1", manifest) shouldBe SourcePlan.Failed(PlaybackError.TimelineFailed)
        }

        // TC-93
        it("no parts is NoAudioParts (P-PLAY-10)") {
            val manifest = AudiobookManifest.Direct(
                bookFileId = 7L,
                parts = emptyList(),
                totalDurationSeconds = 0.0,
                chapters = emptyList(),
                requestHeaders = headers,
            )

            planAudioSource("book-1", manifest) shouldBe SourcePlan.Failed(PlaybackError.NoAudioParts)
        }

        // TC-93
        it("hls is one playlist (I-LOAD-3)") {
            val manifest = AudiobookManifest.Hls(
                bookFileId = 7L,
                playlistUrl = "https://server.test/api/audiobooks/book-1/playlist.m3u8",
                requestHeaders = headers,
            )

            planAudioSource("book-1", manifest) shouldBe SourcePlan.Ready(
                AudioSource.Hls("book-1", "https://server.test/api/audiobooks/book-1/playlist.m3u8", headers),
            )
        }
    }
})
