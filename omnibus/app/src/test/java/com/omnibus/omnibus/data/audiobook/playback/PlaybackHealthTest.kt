package com.omnibus.omnibus.data.audiobook.playback

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class PlaybackHealthTest : DescribeSpec({

    describe("counting consecutive failures") {
        context("when the player buffers between failures") {
            // TC-36
            it("carries on from the same count (I-HLTH-2)") {
                val health = PlaybackHealth()
                health.observed(PlaybackSignal.Failed)
                health.observed(PlaybackSignal.Starved)

                health.observed(PlaybackSignal.Failed) shouldBe HealthAction.Rebuild(2)
            }
        }
    }
})
