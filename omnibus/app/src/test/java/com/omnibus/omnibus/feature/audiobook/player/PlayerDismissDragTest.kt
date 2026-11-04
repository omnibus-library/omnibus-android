package com.omnibus.omnibus.feature.audiobook.player

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.floats.shouldBeExactly
import io.kotest.matchers.shouldBe

class PlayerDismissDragTest : DescribeSpec({

    describe("offset") {

        // P-SCR-13
        it("#165 TC-43: upward drag reports zero") {
            PlayerDismissDrag.offset(-30f) shouldBeExactly 0f
            PlayerDismissDrag.offset(0f) shouldBeExactly 0f
            PlayerDismissDrag.offset(50f) shouldBeExactly 50f
        }
    }

    describe("shouldDismiss") {

        // P-SCR-13, P-A11Y-6
        it("#165 TC-44: dismisses at 120 dp or on a fast downward flick (P-SCR-13)") {
            PlayerDismissDrag.shouldDismiss(119.9f, 0f) shouldBe false
            PlayerDismissDrag.shouldDismiss(120f, 0f) shouldBe true
            PlayerDismissDrag.shouldDismiss(0f, 5000f) shouldBe false
            PlayerDismissDrag.shouldDismiss(100f, 1200f) shouldBe true
            PlayerDismissDrag.shouldDismiss(50f, 1200f) shouldBe false
            PlayerDismissDrag.shouldDismiss(119f, -1200f) shouldBe false
        }
    }

    describe("progress") {

        // P-SCR-13
        it("#165 TC-45: clamps between 0 and 1") {
            PlayerDismissDrag.progress(0f) shouldBeExactly 0f
            PlayerDismissDrag.progress(60f) shouldBeExactly 0.5f
            PlayerDismissDrag.progress(120f) shouldBeExactly 1f
            PlayerDismissDrag.progress(400f) shouldBeExactly 1f
            PlayerDismissDrag.progress(-20f) shouldBeExactly 0f
        }
    }
})
