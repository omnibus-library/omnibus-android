package com.omnibus.omnibus.data.reader

import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContainIgnoringCase
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import io.kotest.property.exhaustive.enum
import io.kotest.property.exhaustive.of

private val stacks = ReaderTypeface.entries.mapNotNull { it.css }

class ReaderTypefaceTest : DescribeSpec({

    describe("the CSS font stacks") {

        it("names no Apple font regardless of the stack") {
            val appleNames = listOf(
                "Georgia",
                "system-ui",
                "-apple-system",
                "Helvetica Neue",
                "Arial",
                "ui-monospace",
                "SF Mono",
                "Menlo",
                "Consolas",
            )

            checkAll(Exhaustive.collection(stacks), Exhaustive.collection(appleNames)) { css, name ->
                css shouldNotContainIgnoringCase name
            }
        }

        it("ends in an Android generic family regardless of the stack") {
            checkAll(Exhaustive.collection(stacks)) { css ->
                css.substringAfterLast(",") shouldBeIn listOf("serif", "sans-serif", "monospace")
            }
        }

        context("when the face is Original") {

            it("has no stack, which keeps the book's own face") {
                ReaderTypeface.Original.css.shouldBeNull()
            }
        }
    }

    describe("decoding a typeface token") {

        it("reads back its own token regardless of the face") {
            checkAll(Exhaustive.enum<ReaderTypeface>()) { face ->
                ReaderTypeface.fromToken(face.token) shouldBe face
            }
        }

        it("maps each legacy token to the face iOS maps it to") {
            checkAll(
                Exhaustive.of(
                    "serif" to ReaderTypeface.Original,
                    "sans-serif" to ReaderTypeface.Sans,
                    "monospace" to ReaderTypeface.Mono,
                ),
            ) { (token, face) ->
                ReaderTypeface.fromToken(token) shouldBe face
            }
        }

        it("gives nothing regardless of whether the token is unknown, empty or missing") {
            checkAll(Exhaustive.of(null, "", "unknown-${randomString()}")) { token ->
                ReaderTypeface.fromToken(token).shouldBeNull()
            }
        }
    }
})
