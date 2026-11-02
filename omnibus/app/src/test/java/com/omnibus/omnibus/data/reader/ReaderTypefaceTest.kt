package com.omnibus.omnibus.data.reader

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class ReaderTypefaceTest : DescribeSpec({

    /** Names that exist on Apple platforms and must never appear in a stack. */
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

    /** The families of a stack, quotes and spacing removed, in order. */
    fun families(css: String): List<String> =
        css.split(",").map { it.trim().trim('\'', '"') }

    describe("ReaderTypeface") {

        it("has the six faces with their labels, tokens and stacks") {
            ReaderTypeface.entries.map { it.name } shouldBe
                listOf("Original", "Editorial", "Classic", "Modern", "Sans", "Mono")
            ReaderTypeface.entries.map { it.token } shouldBe
                listOf("original", "editorial", "classic", "modern", "sans", "mono")

            ReaderTypeface.Original.css.shouldBeNull()
            ReaderTypeface.Editorial.css shouldBe "'Instrument Serif','Noto Serif',serif"
            ReaderTypeface.Classic.css shouldBe "'EB Garamond','Noto Serif',serif"
            ReaderTypeface.Modern.css shouldBe "'Noto Serif',serif"
            ReaderTypeface.Sans.css shouldBe "Roboto,'Noto Sans',sans-serif"
            ReaderTypeface.Mono.css shouldBe "'Droid Sans Mono',monospace"
        }

        it("keeps every stack free of Apple and macOS font names") {
            ReaderTypeface.entries.mapNotNull { it.css }.forEach { css ->
                appleNames.forEach { name ->
                    css.contains(name, ignoreCase = true) shouldBe false
                }
            }
        }

        it("ends every non-Original stack in an Android generic family") {
            val generics = setOf("serif", "sans-serif", "monospace")
            val stacks = ReaderTypeface.entries.mapNotNull { it.css }

            stacks.shouldNotBeEmpty()
            stacks.forEach { css ->
                (families(css).last() in generics) shouldBe true
            }
        }

        it("decodes each current token to itself") {
            ReaderTypeface.entries.forEach { face ->
                ReaderTypeface.fromToken(face.token) shouldBe face
            }
        }

        it("decodes the legacy tokens to the faces iOS maps them to") {
            ReaderTypeface.fromToken("serif") shouldBe ReaderTypeface.Original
            ReaderTypeface.fromToken("sans-serif") shouldBe ReaderTypeface.Sans
            ReaderTypeface.fromToken("monospace") shouldBe ReaderTypeface.Mono
        }

        it("returns null for unknown, empty or missing tokens") {
            ReaderTypeface.fromToken("comic").shouldBeNull()
            ReaderTypeface.fromToken("").shouldBeNull()
            ReaderTypeface.fromToken(null).shouldBeNull()
        }

        it("has no stack that is blank in the middle") {
            ReaderTypeface.entries.mapNotNull { it.css }.forEach { css ->
                families(css).filter { it.isBlank() }.shouldBeEmpty()
            }
        }
    }
})
