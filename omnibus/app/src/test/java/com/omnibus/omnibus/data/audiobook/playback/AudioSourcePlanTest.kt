package com.omnibus.omnibus.data.audiobook.playback

import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class AudioSourcePlanTest : DescribeSpec({

    describe("planning a direct book") {
        // TC-93
        it("plays the manifest's parts with their lengths in milliseconds (I-LOAD-4)") {
            val bookUuid = randomString()
            val manifest = multiPartManifest()

            planAudioSource(bookUuid, manifest) shouldBe SourcePlan.Ready(
                AudioSource.Parts(
                    mediaId = bookUuid,
                    parts = listOf(
                        SourcePart(manifest.parts[0].url, 1_000_000L, "audio/mpeg"),
                        SourcePart(manifest.parts[1].url, 1_500_000L, "audio/mpeg"),
                        SourcePart(manifest.parts[2].url, 500_000L, "audio/mpeg"),
                    ),
                    headers = manifest.requestHeaders,
                ),
            )
        }

        context("when a part's duration is unusable") {
            // TC-93
            it("fails with TimelineFailed for sub-millisecond, zero, negative, NaN or infinite durations (P-PLAY-10)") {
                checkAll(Exhaustive.of(0.0004, 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) { seconds ->
                    planAudioSource(randomString(), manifestWithFirstPart(seconds)) shouldBe
                        SourcePlan.Failed(PlaybackError.TimelineFailed)
                }
            }
        }

        context("when the manifest has no parts") {
            // TC-93
            it("fails with NoAudioParts (P-PLAY-10)") {
                planAudioSource(randomString(), multiPartManifest().copy(parts = emptyList())) shouldBe
                    SourcePlan.Failed(PlaybackError.NoAudioParts)
            }
        }
    }

    describe("planning an hls book") {
        // TC-93
        it("plays the one playlist (I-LOAD-3)") {
            val bookUuid = randomString()
            val manifest = hlsManifest()

            planAudioSource(bookUuid, manifest) shouldBe SourcePlan.Ready(
                AudioSource.Hls(bookUuid, manifest.playlistUrl, manifest.requestHeaders),
            )
        }
    }
})
