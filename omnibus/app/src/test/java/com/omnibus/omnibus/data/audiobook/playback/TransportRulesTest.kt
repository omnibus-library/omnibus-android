package com.omnibus.omnibus.data.audiobook.playback

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class TransportRulesTest : DescribeSpec({

    describe("finding where a skip lands") {
        context("when the skip would pass the start of the book") {
            // TC-28
            it("lands at the start (I-TL-6)") {
                skipTarget(positionSeconds = 5.0, durationSeconds = 3000.0, deltaSeconds = -15.0) shouldBe 0.0
            }
        }

        context("when the skip would pass the end of the book") {
            // TC-28
            it("lands at the end (I-TL-6)") {
                skipTarget(positionSeconds = 2990.0, durationSeconds = 3000.0, deltaSeconds = 30.0) shouldBe 3000.0
            }
        }

        context("when the duration is unknown") {
            // TC-28
            it("has no upper bound (I-TL-6)") {
                skipTarget(positionSeconds = 5.0, durationSeconds = 0.0, deltaSeconds = 30.0) shouldBe 35.0
            }
        }
    }

    describe("finding the previous chapter") {
        context("when more than 3 seconds into a chapter") {
            // TC-29
            it("restarts that chapter (I-TL-7)") {
                previousChapterTarget(chapters(600.0, 1200.0, 900.0), 603.01) shouldBe 600.0
            }
        }

        context("when exactly 3 seconds into a chapter") {
            // TC-29
            it("goes to the chapter before (I-TL-7)") {
                previousChapterTarget(chapters(600.0, 1200.0, 900.0), 603.0) shouldBe 0.0
            }
        }

        context("when in the first chapter") {
            // TC-29
            it("restarts it (P-PLAY-4)") {
                previousChapterTarget(chapters(600.0, 1200.0, 900.0), 1.0) shouldBe 0.0
            }
        }
    }

    describe("finding the next chapter") {
        context("when in the last chapter") {
            // TC-30
            it("finds nothing (P-PLAY-5)") {
                nextChapterTarget(chapters(600.0, 1200.0, 900.0), 2000.0).shouldBeNull()
            }
        }
    }

    describe("limiting the playback rate") {
        // TC-32
        it("keeps any rate from 0.5 to 3.0 (I-SPD-1)") {
            checkAll(Exhaustive.of(0.5, 1.7, 3.0)) { rate ->
                clampRate(rate) shouldBe rate
            }
        }

        context("when the rate is below 0.5") {
            // TC-32
            it("uses 0.5 (I-SPD-1)") {
                clampRate(0.1) shouldBe 0.5
            }
        }

        context("when the rate is above 3.0") {
            // TC-32
            it("uses 3.0 (I-SPD-1)") {
                clampRate(9.0) shouldBe 3.0
            }
        }
    }
})
