package com.omnibus.omnibus.ui.components

import com.omnibus.omnibus.ui.theme.Oklch
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

private fun identity(title: String, accent: String? = null) =
    CoverIdentity(uuid = "u", title = title, accent = accent, hasCover = false)

class CoverIdentityTest : DescribeSpec({

    describe("titleHue") {

        it("TC-114: hashes with djb2 over the UTF-8 bytes, mod 360") {
            // Expected values worked out independently with 64-bit unsigned arithmetic.
            CoverIdentity.titleHue("Dune") shouldBe 41.0
            CoverIdentity.titleHue("The Left Hand of Darkness") shouldBe 284.0
            CoverIdentity.titleHue("a") shouldBe 190.0
        }

        it("TC-116: hashes multi-byte characters by their UTF-8 bytes") {
            CoverIdentity.titleHue("Café ☕") shouldBe 314.0
        }

        it("TC-117: wraps at 64 bits") {
            CoverIdentity.titleHue("a".repeat(40)) shouldBe 293.0
        }
    }

    describe("tone") {

        it("TC-118: derives lightness, chroma and hue from the title when there is no accent") {
            identity("Dune").tone shouldBe Oklch(0.55, 0.09, 41.0)
        }

        it("TC-121: uses a valid accent instead of the title hash") {
            identity("Dune", accent = "oklch(0.7 0.12 250)").tone shouldBe Oklch(0.7, 0.12, 250.0)
        }

        it("TC-122: accepts a hex accent") {
            identity("Dune", accent = "#112233").tone shouldBe Oklch.parse("#112233")
        }

        it("TC-123: falls back to the title hash when the accent can't be parsed") {
            identity("Dune", accent = "not a color").tone shouldBe Oklch(0.55, 0.09, 41.0)
        }
    }
})
