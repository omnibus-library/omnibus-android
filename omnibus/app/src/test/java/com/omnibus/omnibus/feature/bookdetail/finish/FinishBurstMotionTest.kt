package com.omnibus.omnibus.feature.bookdetail.finish

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe

class FinishBurstMotionTest : DescribeSpec({

    describe("the burst pieces") {

        it("TC-17: sixteen ribbons, squiggles and diamonds in three tones") {
            BURST_PIECES.size shouldBe 16
            BURST_PIECES.first() shouldBe BurstPiece(8f, 6f, BurstKind.Ribbon, BurstTone.Accent, -24f)
            BURST_PIECES.last() shouldBe BurstPiece(70f, 96f, BurstKind.Ribbon, BurstTone.Gold, 14f)

            BURST_PIECES.count { it.kind == BurstKind.Ribbon } shouldBe 6
            BURST_PIECES.count { it.kind == BurstKind.Squiggle } shouldBe 5
            BURST_PIECES.count { it.kind == BurstKind.Diamond } shouldBe 5

            BURST_PIECES.count { it.tone == BurstTone.Accent } shouldBe 5
            BURST_PIECES.count { it.tone == BurstTone.Cream } shouldBe 6
            BURST_PIECES.count { it.tone == BurstTone.Gold } shouldBe 5
        }
    }

    describe("the burst's motion") {

        it("TC-18: each piece flies in from the cover and comes to rest") {
            val start = burstPose(0, 0f)
            start.alpha shouldBe (0f plusOrMinus 0.01f)
            start.dx shouldBe (109.2f plusOrMinus 0.01f)
            start.dy shouldBe (140.8f plusOrMinus 0.01f)
            start.scale shouldBe (0.3f plusOrMinus 0.01f)
            start.rotation shouldBe (0f plusOrMinus 0.01f)

            // Piece 1 is still in its 50 ms delay at 25 ms, so it sits at its start pose.
            val delayed = burstPose(1, 25f)
            delayed.alpha shouldBe (0f plusOrMinus 0.01f)
            delayed.dx shouldBe (-88.4f plusOrMinus 0.01f)
            delayed.dy shouldBe (134.4f plusOrMinus 0.01f)
            delayed.scale shouldBe (0.3f plusOrMinus 0.01f)

            BURST_PIECES.indices.forEach { index ->
                val rest = burstPose(index, 1_600f)
                rest.alpha shouldBe (1f plusOrMinus 0.01f)
                rest.dx shouldBe (0f plusOrMinus 0.01f)
                rest.dy shouldBe (0f plusOrMinus 0.01f)
                rest.scale shouldBe (1f plusOrMinus 0.01f)
                rest.rotation shouldBe (BURST_PIECES[index].rotation plusOrMinus 0.01f)
            }

            for (index in BURST_PIECES.indices) {
                for (elapsed in 0..1_600 step 50) {
                    val pose = burstPose(index, elapsed.toFloat())
                    (pose.alpha in 0f..1f) shouldBe true
                }
            }
        }
    }
})
