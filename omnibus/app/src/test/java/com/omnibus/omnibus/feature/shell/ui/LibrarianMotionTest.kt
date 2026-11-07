package com.omnibus.omnibus.feature.shell.ui

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.floats.shouldBeBetween
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.numericFloat
import io.kotest.property.checkAll

class LibrarianMotionTest : DescribeSpec({

    describe("how far the book has been shelved") {
        it("ends the loop where it began, so the loop has no jump") {
            shelvedAmount(1f) shouldBe shelvedAmount(0f)
        }

        it("has the book in place at the resting phase") {
            shelvedAmount(SCENE_REST_PHASE) shouldBe 1f
        }

        it("stays between in hand and in place at every phase") {
            checkAll(Arb.numericFloat(0f, 1f)) { phase ->
                shelvedAmount(phase).shouldBeBetween(0f, 1f, 0.0001f)
            }
        }
    }
})
