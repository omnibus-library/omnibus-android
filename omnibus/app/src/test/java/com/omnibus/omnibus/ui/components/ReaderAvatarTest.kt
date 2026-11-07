package com.omnibus.omnibus.ui.components

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class ReaderAvatarTest : DescribeSpec({

    describe("the initial on a reader's avatar") {

        it("TC-18: is the upper-cased first letter, past any leading space") {
            avatarInitial("  theo Quill") shouldBe "T"
        }

        context("when the first character is a surrogate pair") {
            it("TC-19: keeps the whole code point") {
                avatarInitial("𝔸nna") shouldBe "𝔸"
            }
        }

        context("when the name is blank") {
            it("TC-20: is a question mark") {
                checkAll(Exhaustive.of("", "   ")) { name ->
                    avatarInitial(name) shouldBe "?"
                }
            }
        }
    }
})
