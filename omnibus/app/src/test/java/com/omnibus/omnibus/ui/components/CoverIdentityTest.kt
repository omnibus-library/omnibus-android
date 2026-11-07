package com.omnibus.omnibus.ui.components

import com.omnibus.omnibus.ui.theme.Oklch
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class CoverIdentityTest : DescribeSpec({

    describe("the hue a title hashes to") {

        // Expected values worked out independently with 64-bit unsigned arithmetic.
        it("TC-114: is djb2 over the UTF-8 bytes, mod 360") {
            CoverIdentity.titleHue("Dune") shouldBe 41.0
        }

        context("when the title has multi-byte characters") {
            it("TC-116: hashes their UTF-8 bytes") {
                CoverIdentity.titleHue("Café ☕") shouldBe 314.0
            }
        }

        context("when the title overflows 64 bits") {
            it("TC-117: wraps") {
                CoverIdentity.titleHue("a".repeat(40)) shouldBe 293.0
            }
        }
    }

    describe("the tone a cover is drawn in") {

        context("when there is no accent") {
            it("TC-118: takes its hue from the title") {
                val identity = randomCoverIdentity().copy(accent = null)

                identity.tone.h shouldBe CoverIdentity.titleHue(identity.title)
            }
        }

        context("when the accent is valid") {
            it("TC-121: uses the accent") {
                randomCoverIdentity().copy(accent = "oklch(0.7 0.12 250)").tone shouldBe Oklch(0.7, 0.12, 250.0)
            }
        }

        context("when the accent can't be parsed") {
            it("TC-123: falls back to the title's tone") {
                val identity = randomCoverIdentity()

                identity.copy(accent = "not a color").tone shouldBe identity.copy(accent = null).tone
            }
        }
    }
})
