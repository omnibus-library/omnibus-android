package com.omnibus.omnibus.feature.libraries.manage

import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection

class LibraryNameLimitTest : DescribeSpec({

    describe("capping a library name") {
        context("when the name is 40 characters or fewer") {
            it("TC-1: keeps the name unchanged") {
                checkAll(Exhaustive.collection((0..40).toList())) { length ->
                    val name = randomString(length..length)

                    name.capLibraryName() shouldBe name
                }
            }
        }

        context("when the name is longer than 40 characters") {
            it("TC-1: keeps its first 40 characters") {
                checkAll(Exhaustive.collection((41..80).toList())) { length ->
                    val name = randomString(length..length)

                    name.capLibraryName() shouldBe name.take(40)
                }
            }

            context("when the fortieth code point is an emoji") {
                it("TC-1: keeps the emoji whole and cuts after it") {
                    // U+1F4DA is a surrogate pair: the 40th code point, two chars.
                    val emoji = "📚"

                    ("a".repeat(39) + emoji + "z").capLibraryName() shouldBe "a".repeat(39) + emoji
                }
            }
        }
    }
})
