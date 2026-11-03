package com.omnibus.omnibus.feature.bookdetail.journal

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class JournalPreviewTest : DescribeSpec({

    describe("the preview's line and block markers") {

        it("TC-18: an empty or blank body has no runs") {
            journalPreview("") shouldBe emptyList()
            journalPreview("   ") shouldBe emptyList()
            journalPreview("\n\n  \n") shouldBe emptyList()
        }

        it("TC-19: only the first non-blank line is kept, and CRLF and bare CR count as line ends") {
            journalPreview("\n\n  First line\nsecond line") shouldBe listOf(PreviewRun.Text("First line"))
            journalPreview("one\r\ntwo") shouldBe listOf(PreviewRun.Text("one"))
            journalPreview("one\rtwo") shouldBe listOf(PreviewRun.Text("one"))
        }

        it("TC-20: one leading block marker is stripped and the text kept") {
            listOf(
                "- item" to "item",
                "* item" to "item",
                "+ item" to "item",
                "3. item" to "item",
                "12) item" to "item",
                "## Title" to "Title",
                "> quote" to "quote",
                ">quote" to "quote",
            ).forEach { (body, text) ->
                journalPreview(body) shouldBe listOf(PreviewRun.Text(text))
            }
        }

        it("TC-21: only one marker is stripped, and text that is not a marker keeps its characters") {
            journalPreview("- - nested") shouldBe listOf(PreviewRun.Text("- nested"))
            journalPreview("#hashtag") shouldBe listOf(PreviewRun.Text("#hashtag"))
            journalPreview("####### seven") shouldBe listOf(PreviewRun.Text("####### seven"))
            journalPreview("2 * 3") shouldBe listOf(PreviewRun.Text("2 * 3"))
            journalPreview("C# parser") shouldBe listOf(PreviewRun.Text("C# parser"))
        }
    }

    describe("the preview's inline Markdown") {

        it("TC-22: bold, italic and strikethrough flatten to their words") {
            journalPreview("**Kvothe** is an *unreliable* narrator") shouldBe
                listOf(PreviewRun.Text("Kvothe is an unreliable narrator"))
            journalPreview("__bold__ and _it_") shouldBe listOf(PreviewRun.Text("bold and it"))
            journalPreview("~~gone~~ stays") shouldBe listOf(PreviewRun.Text("gone stays"))
        }

        it("TC-23: an underscore inside a word and a lone star stay as typed") {
            journalPreview("snake_case_name") shouldBe listOf(PreviewRun.Text("snake_case_name"))
            journalPreview("a * b * c") shouldBe listOf(PreviewRun.Text("a * b * c"))
            journalPreview("5 * 3 = 15") shouldBe listOf(PreviewRun.Text("5 * 3 = 15"))
        }

        it("TC-24: a code span keeps its text verbatim, with no inner Markdown parsed") {
            journalPreview("run `a **b** c` now") shouldBe listOf(PreviewRun.Text("run a **b** c now"))
        }

        it("TC-25: a link keeps its text, an autolink shows its URL, and an image is removed") {
            journalPreview("see [the docs](https://x.io/a) now") shouldBe listOf(PreviewRun.Text("see the docs now"))
            journalPreview("<https://x.io>") shouldBe listOf(PreviewRun.Text("https://x.io"))
            journalPreview("![alt](u.png) after") shouldBe listOf(PreviewRun.Text("after"))
            journalPreview("pic ![alt](u.png) end") shouldBe listOf(PreviewRun.Text("pic end"))
        }

        it("TC-26: a backslash escape gives the literal character, and inline HTML is left as typed") {
            journalPreview("\\*literal\\* star") shouldBe listOf(PreviewRun.Text("*literal* star"))
            journalPreview("<b>x</b>") shouldBe listOf(PreviewRun.Text("<b>x</b>"))
        }
    }

    describe("the spoiler masking") {

        it("TC-27: a spoiler becomes sized filler, and the real text is in no run") {
            val runs = journalPreview("He was ||Ares||.")

            runs shouldBe listOf(
                PreviewRun.Text("He was "),
                PreviewRun.Veiled("lore"),
                PreviewRun.Text("."),
            )
            runs.none { run -> run is PreviewRun.Text && "Ares" in run.text } shouldBe true
        }

        it("TC-28: each filler is sized to its flattened span, and a long span cycles the filler words") {
            JOURNAL_FILLER.length shouldBe 55

            journalPreview("He was ||Ares|| and ||her **uncle**||") shouldBe listOf(
                PreviewRun.Text("He was "),
                PreviewRun.Veiled("lore"),
                PreviewRun.Text(" and "),
                PreviewRun.Veiled("lorem ips"),
            )
            journalPreview("||" + "x".repeat(60) + "||") shouldBe listOf(
                PreviewRun.Veiled("lorem ipsum dolor sit amet consectetur adipiscing elit lorem"),
            )
        }

        it("TC-29: an unterminated || stays literal, and pairs form left to right") {
            journalPreview("a || b") shouldBe listOf(PreviewRun.Text("a || b"))
            journalPreview("||open and ||closed||") shouldBe listOf(
                PreviewRun.Veiled("lorem ips"),
                PreviewRun.Text("closed||"),
            )
            journalPreview("||") shouldBe listOf(PreviewRun.Text("||"))
        }

        it("TC-30: a spoiler at the start of the line does not hide a block marker") {
            journalPreview("||secret|| begins") shouldBe listOf(
                PreviewRun.Veiled("lorem "),
                PreviewRun.Text(" begins"),
            )
            journalPreview("- ||secret||") shouldBe listOf(PreviewRun.Veiled("lorem "))
            journalPreview("## ||secret||") shouldBe listOf(PreviewRun.Veiled("lorem "))
            journalPreview("> ||secret||") shouldBe listOf(PreviewRun.Veiled("lorem "))
        }

        it("TC-31: an empty spoiler gives no run, and adjacent spoilers are two runs") {
            journalPreview("||||") shouldBe emptyList()
            journalPreview("x ||||y") shouldBe listOf(PreviewRun.Text("x y"))
            journalPreview("||a|| ||b||") shouldBe listOf(
                PreviewRun.Veiled("l"),
                PreviewRun.Text(" "),
                PreviewRun.Veiled("l"),
            )
        }

        it("TC-32: markup around a spoiler resolves, and a typed private-use character is dropped, never a veil") {
            journalPreview("**bold ||Ares|| bold**") shouldBe listOf(
                PreviewRun.Text("bold "),
                PreviewRun.Veiled("lore"),
                PreviewRun.Text(" bold"),
            )
            journalPreview("ab ||x||") shouldBe listOf(PreviewRun.Text("ab "), PreviewRun.Veiled("l"))
            journalPreview("ab") shouldBe listOf(PreviewRun.Text("ab"))
            journalPreview(" ||x||") shouldBe listOf(PreviewRun.Veiled("l"))
        }
    }

    describe("the spoken preview") {

        it("TC-33: each veil is spoken as the hidden label, and no filler word is ever read") {
            spokenPreview(
                listOf(PreviewRun.Text("He was "), PreviewRun.Veiled("lore"), PreviewRun.Text(".")),
                "spoiler, hidden",
            ) shouldBe "He was spoiler, hidden."
            spokenPreview(listOf(PreviewRun.Veiled("lorem")), "spoiler, hidden") shouldBe "spoiler, hidden"
            spokenPreview(emptyList(), "spoiler, hidden") shouldBe ""
        }
    }
})
