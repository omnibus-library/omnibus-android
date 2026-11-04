package com.omnibus.omnibus.data.audiobook.playback

import com.omnibus.omnibus.testing.FakeTimeSource
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class RemotePressFilterTest : DescribeSpec({

    describe("RemotePressFilter") {

        // TC-34
        it("coalesces different same-direction commands within 1 s (I-SYS-3)") {
            val clock = FakeTimeSource(0L)
            val filter = RemotePressFilter(clock)

            filter.shouldMove(RemoteTransport.NextTrack) shouldBe true
            clock.advanceBy(300L)
            filter.shouldMove(RemoteTransport.SeekForward) shouldBe false
        }

        // TC-34
        it("a repeat of the same command moves again (I-SYS-3)") {
            val clock = FakeTimeSource(0L)
            val filter = RemotePressFilter(clock)

            filter.shouldMove(RemoteTransport.NextTrack) shouldBe true
            clock.advanceBy(300L)
            filter.shouldMove(RemoteTransport.NextTrack) shouldBe true
            clock.advanceBy(300L)
            filter.shouldMove(RemoteTransport.NextTrack) shouldBe true
        }

        // TC-34
        it("opposite directions both move (I-SYS-3)") {
            val clock = FakeTimeSource(0L)
            val filter = RemotePressFilter(clock)

            filter.shouldMove(RemoteTransport.SkipForward) shouldBe true
            clock.advanceBy(200L)
            filter.shouldMove(RemoteTransport.SkipBackward) shouldBe true
        }

        // TC-34
        it("a back-direction pair coalesces (I-SYS-3)") {
            val clock = FakeTimeSource(0L)
            val filter = RemotePressFilter(clock)

            filter.shouldMove(RemoteTransport.PreviousTrack) shouldBe true
            clock.advanceBy(200L)
            filter.shouldMove(RemoteTransport.SkipBackward) shouldBe false
        }

        // TC-34
        it("moves again after 1 s (I-SYS-3)") {
            val clock = FakeTimeSource(0L)
            val filter = RemotePressFilter(clock)

            filter.shouldMove(RemoteTransport.NextTrack) shouldBe true
            clock.advanceBy(1_000L)
            filter.shouldMove(RemoteTransport.SkipForward) shouldBe true
        }

        // TC-34
        it("999 ms still coalesces (I-SYS-3)") {
            val clock = FakeTimeSource(0L)
            val filter = RemotePressFilter(clock)

            filter.shouldMove(RemoteTransport.NextTrack) shouldBe true
            clock.advanceBy(999L)
            filter.shouldMove(RemoteTransport.SkipForward) shouldBe false
        }

        // TC-34
        it("a third command after a coalesced pair moves (I-SYS-3)") {
            val clock = FakeTimeSource(0L)
            val filter = RemotePressFilter(clock)

            filter.shouldMove(RemoteTransport.NextTrack) shouldBe true
            clock.advanceBy(300L)
            filter.shouldMove(RemoteTransport.SeekForward) shouldBe false
            clock.advanceBy(100L)
            filter.shouldMove(RemoteTransport.SkipForward) shouldBe true
        }

        // TC-34
        it("a negative elapsed time counts as the same press (I-SYS-3)") {
            val clock = FakeTimeSource(1_000L)
            val filter = RemotePressFilter(clock)

            filter.shouldMove(RemoteTransport.NextTrack) shouldBe true
            clock.set(500L)
            filter.shouldMove(RemoteTransport.SkipForward) shouldBe false
        }

        // TC-34
        it("maps each command to a direction (P-PLAY-6)") {
            RemoteTransport.SkipForward.seekDeltaSeconds shouldBe 30.0
            RemoteTransport.NextTrack.seekDeltaSeconds shouldBe 30.0
            RemoteTransport.SeekForward.seekDeltaSeconds shouldBe 30.0
            RemoteTransport.SkipBackward.seekDeltaSeconds shouldBe -15.0
            RemoteTransport.PreviousTrack.seekDeltaSeconds shouldBe -15.0
            RemoteTransport.SeekBackward.seekDeltaSeconds shouldBe -15.0
        }
    }

    describe("key mapping") {

        // TC-88
        it("maps the six media keys (P-PLAY-6)") {
            RemoteTransport.forMediaKey(87) shouldBe RemoteTransport.NextTrack
            RemoteTransport.forMediaKey(88) shouldBe RemoteTransport.PreviousTrack
            RemoteTransport.forMediaKey(89) shouldBe RemoteTransport.SeekBackward
            RemoteTransport.forMediaKey(90) shouldBe RemoteTransport.SeekForward
            RemoteTransport.forMediaKey(272) shouldBe RemoteTransport.SkipForward
            RemoteTransport.forMediaKey(273) shouldBe RemoteTransport.SkipBackward
            RemoteTransport.forMediaKey(85) shouldBe null
            RemoteTransport.forMediaKey(126) shouldBe null
        }

        // TC-88
        it("acts only on the first key down (P-PLAY-6)") {
            RemoteTransport.actsOn(RemoteTransport.ACTION_DOWN, 0) shouldBe true
            RemoteTransport.actsOn(1, 0) shouldBe false
            RemoteTransport.actsOn(RemoteTransport.ACTION_DOWN, 1) shouldBe false
        }
    }
})
