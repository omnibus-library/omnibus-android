package com.omnibus.omnibus.feature.audiobook.player

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.floats.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class PlayerDismissDragTest : DescribeSpec({

    describe("the surface's offset") {
        // P-SCR-13
        it("#165 TC-43: follows a downward drag, staying at zero for an upward one") {
            PlayerDismissDrag.offset(-30f) shouldBeExactly 0f
            PlayerDismissDrag.offset(50f) shouldBeExactly 50f
        }
    }

    describe("dismissing on release") {
        // P-SCR-13, P-A11Y-6
        it("#165 TC-44: needs 120 dp, or a shorter drag with a fast downward flick") {
            checkAll(
                Exhaustive.of(
                    Triple(119.9f, 0f, false),
                    Triple(120f, 0f, true),
                    Triple(0f, 5000f, false),
                    Triple(100f, 1200f, true),
                    Triple(50f, 1200f, false),
                    Triple(119f, -1200f, false),
                ),
            ) { (offset, velocity, expected) ->
                PlayerDismissDrag.shouldDismiss(offset, velocity) shouldBe expected
            }
        }
    }
})
