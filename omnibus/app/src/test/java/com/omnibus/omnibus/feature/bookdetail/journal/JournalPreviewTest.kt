package com.omnibus.omnibus.feature.bookdetail.journal

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class JournalPreviewTest : DescribeSpec({

    describe("the preview's lines") {

        context("when the body is blank") {
            it("TC-18: has no runs") {
                checkAll(Exhaustive.of("", "   ", "\n\n  \n")) { body ->
                    journalPreview(body) shouldBe emptyList()
                }
            }
        }

        context("when the body has several lines") {
            it("TC-19: keeps the first non-blank one, whichever line ending it has") {
                checkAll(Exhaustive.of("\n\n  one\ntwo", "one\r\ntwo", "one\rtwo")) { body ->
                    journalPreview(body) shouldBe listOf(PreviewRun.Text("one"))
                }
            }
        }

        context("when the line starts with a block marker") {
            it("TC-20 / TC-21: strips one, for lists, numbers, headings, quotes or a nested marker") {
                checkAll(
                    Exhaustive.of(
                        "- item" to "item",
                        "* item" to "item",
                        "+ item" to "item",
                        "3. item" to "item",
                        "12) item" to "item",
                        "## item" to "item",
                        ">item" to "item",
                        "- - nested" to "- nested",
                    ),
                ) { (body, text) ->
                    journalPreview(body) shouldBe listOf(PreviewRun.Text(text))
                }
            }
        }
    }

    describe("the preview's inline Markdown") {

        it("TC-22 / TC-24 / TC-25 / TC-26: flattens to what a reader sees, for each kind of markup") {
            checkAll(
                Exhaustive.of(
                    "**word** stays" to "word stays",
                    "__word__ stays" to "word stays",
                    "*word* stays" to "word stays",
                    "_word_ stays" to "word stays",
                    "~~word~~ stays" to "word stays",
                    "run `a **b** c` now" to "run a **b** c now",
                    "see [the docs](https://x.io/a) now" to "see the docs now",
                    "<https://x.io>" to "https://x.io",
                    "pic ![alt](u.png) end" to "pic end",
                    "\\*literal\\* star" to "*literal* star",
                ),
            ) { (body, text) ->
                journalPreview(body) shouldBe listOf(PreviewRun.Text(text))
            }
        }

        context("when the text only looks like markup") {
            it("TC-21 / TC-23 / TC-26: stays as typed") {
                checkAll(
                    Exhaustive.of("#hashtag", "####### seven", "2 * 3", "C# parser", "snake_case_name", "a * b * c", "<b>x</b>"),
                ) { body ->
                    journalPreview(body) shouldBe listOf(PreviewRun.Text(body))
                }
            }
        }
    }

    describe("the spoiler masking") {

        it("TC-27: replaces a spoiler with filler the size of its text") {
            journalPreview("He was ||Ares||.") shouldBe listOf(
                PreviewRun.Text("He was "),
                PreviewRun.Veiled("lore"),
                PreviewRun.Text("."),
            )
        }

        context("when the spoiler outruns the filler") {
            it("TC-28: cycles the filler words") {
                journalPreview("||" + "x".repeat(60) + "||") shouldBe listOf(
                    PreviewRun.Veiled("lorem ipsum dolor sit amet consectetur adipiscing elit lorem"),
                )
            }
        }

        context("when a marker is unterminated") {
            it("TC-29: stays literal") {
                checkAll(Exhaustive.of("a || b", "||")) { body ->
                    journalPreview(body) shouldBe listOf(PreviewRun.Text(body))
                }
            }
        }

        context("when a block marker precedes the spoiler") {
            it("TC-30: strips the marker, for lists, headings or quotes") {
                checkAll(Exhaustive.of("- ||secret||", "## ||secret||", "> ||secret||")) { body ->
                    journalPreview(body) shouldBe listOf(PreviewRun.Veiled("lorem "))
                }
            }
        }

        context("when the spoiler is empty") {
            it("TC-31: gives no run") {
                journalPreview("x ||||y") shouldBe listOf(PreviewRun.Text("x y"))
            }
        }

        context("when the reader typed a private-use character") {
            it("TC-32: drops it, never forming a veil") {
                journalPreview("ab") shouldBe listOf(PreviewRun.Text("ab"))
            }
        }
    }

    describe("the spoken preview") {

        it("TC-33: speaks each veil as the hidden label, never the filler") {
            spokenPreview(
                listOf(PreviewRun.Text("He was "), PreviewRun.Veiled("lore"), PreviewRun.Text(".")),
                "spoiler, hidden",
            ) shouldBe "He was spoiler, hidden."
        }
    }
})
