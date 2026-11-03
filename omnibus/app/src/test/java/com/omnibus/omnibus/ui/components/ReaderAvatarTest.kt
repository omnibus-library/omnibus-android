package com.omnibus.omnibus.ui.components

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ReaderAvatarTest : DescribeSpec({
    describe("avatarInitial") {
        it("TC-18: is the upper-cased first letter of the name") {
            avatarInitial("maren") shouldBe "M"
            avatarInitial("  Theo Quill") shouldBe "T"
        }

        it("TC-19: keeps a whole code point, not half a surrogate pair") {
            avatarInitial("𝔸nna") shouldBe "𝔸"
        }

        it("TC-20: a blank name gives a question mark") {
            avatarInitial("") shouldBe "?"
            avatarInitial("   ") shouldBe "?"
        }
    }
})
