package com.omnibus.omnibus.data.audiobook.playback

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class PlaybackHealthTest : DescribeSpec({

    describe("rebuild budget") {

        // TC-35
        it("waits 1, 2, 3 s then gives up (I-HLTH-2)") {
            val health = PlaybackHealth()

            health.observed(PlaybackSignal.Failed) shouldBe HealthAction.Rebuild(1)
            health.observed(PlaybackSignal.Failed) shouldBe HealthAction.Rebuild(2)
            health.observed(PlaybackSignal.Failed) shouldBe HealthAction.Rebuild(3)
        }

        // TC-35
        it("the fourth failure surrenders (I-HLTH-3)") {
            val health = PlaybackHealth()
            repeat(3) { health.observed(PlaybackSignal.Failed) }

            health.observed(PlaybackSignal.Failed) shouldBe HealthAction.Surrender
        }
    }

    describe("reset") {

        // TC-36
        it("real playback resets the count (I-HLTH-2)") {
            val health = PlaybackHealth()
            health.observed(PlaybackSignal.Failed)
            health.observed(PlaybackSignal.Failed)

            health.observed(PlaybackSignal.Playing) shouldBe HealthAction.Healthy
            health.observed(PlaybackSignal.Failed) shouldBe HealthAction.Rebuild(1)
        }

        // TC-36
        it("buffering never rebuilds (I-HLTH-1)") {
            val health = PlaybackHealth()

            health.observed(PlaybackSignal.Starved) shouldBe HealthAction.Buffering
            health.observed(PlaybackSignal.Starved) shouldBe HealthAction.Buffering
        }

        // TC-36
        it("buffering does not touch the count (I-HLTH-2)") {
            val health = PlaybackHealth()
            health.observed(PlaybackSignal.Failed)
            health.observed(PlaybackSignal.Starved)
            health.observed(PlaybackSignal.Starved)

            health.observed(PlaybackSignal.Failed) shouldBe HealthAction.Rebuild(2)
        }

        // TC-36
        it("reset refills the budget (I-HLTH-3)") {
            val health = PlaybackHealth()
            repeat(4) { health.observed(PlaybackSignal.Failed) }
            health.observed(PlaybackSignal.Failed) shouldBe HealthAction.Surrender

            health.reset()

            health.observed(PlaybackSignal.Failed) shouldBe HealthAction.Rebuild(1)
        }
    }
})
