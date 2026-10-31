package com.omnibus.omnibus.feature.reader.ui

import com.omnibus.omnibus.feature.reader.ui.ReaderEventFixtures.ANNOTATION_TAP
import com.omnibus.omnibus.feature.reader.ui.ReaderEventFixtures.RELOCATE
import com.omnibus.omnibus.feature.reader.ui.ReaderEventFixtures.SELECTION
import com.omnibus.omnibus.feature.reader.ui.ReaderEventFixtures.SELECTION_EXISTING
import com.omnibus.omnibus.feature.reader.ui.ReaderEventFixtures.SHARE_IMAGE
import com.omnibus.omnibus.feature.reader.ui.ReaderEventFixtures.TOC_BARE
import com.omnibus.omnibus.feature.reader.ui.ReaderEventFixtures.TOC_ENRICHED
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ReaderEventTest : DescribeSpec({

    fun decode(type: String, payload: String?) = ReaderEvent.decode(ReaderMessage(type, payload))

    describe("ReaderEvent.decode") {

        it("TC-1: decodes a captured relocate payload through the envelope") {
            // The glue passes the JSON as a string, so the envelope carries it as a string value.
            val envelope = """{"type":"relocate","payload":${JsonPrimitive(RELOCATE)}}"""

            ReaderEvent.decode(ReaderMessage.parse(envelope)!!) shouldBe ReaderEvent.Relocate(
                RelocateData(
                    cfi = "epubcfi(/6/14!/4/2/1:0)",
                    page = 12,
                    totalPages = 310,
                    pct = 4,
                    atEnd = false,
                    chapter = 2,
                    totalChapters = 24,
                    chapterTitle = "Loomings",
                    chapterPagesLeft = 7,
                    echo = false,
                    settledSeek = null,
                ),
            )
        }

        it("TC-2: decodes a bare toc payload, with page and pct left null") {
            decode("toc", TOC_BARE) shouldBe ReaderEvent.Toc(
                listOf(
                    TocEntry(label = "Cover", href = "cover.xhtml", level = 0),
                    TocEntry(label = "Loomings", href = "chapter1.xhtml", level = 0),
                    TocEntry(label = "The Carpet-Bag", href = "chapter2.xhtml#carpet", level = 1),
                ),
            )
        }

        it("TC-3: decodes a toc payload with page and pct") {
            val event = decode("toc", TOC_ENRICHED).shouldBeInstanceOf<ReaderEvent.Toc>()

            event.entries.map { it.page } shouldBe listOf(1, 12, 31)
            event.entries.map { it.pct } shouldBe listOf(0, 4, 10)
        }

        it("TC-4: decodes a captured selection payload") {
            decode("selection", SELECTION) shouldBe ReaderEvent.Selection(
                SelectionData(
                    cfiRange = "epubcfi(/6/14!/4/2/1:0,/4/2/1:24)",
                    text = "Call me Ishmael.",
                    rects = listOf(PageRect(x = 24.5, y = 310.0, width = 180.25, height = 22.0)),
                    start = SelectionHandle(x = 24.5, y = 310.0, height = 22.0),
                    end = SelectionHandle(x = 204.75, y = 310.0, height = 22.0),
                    existing = null,
                    dragging = false,
                ),
            )
        }

        it("TC-5: keeps an existing annotation as raw json, with start and end null when missing") {
            val data = decode("selection", SELECTION_EXISTING).shouldBeInstanceOf<ReaderEvent.Selection>().data

            data.start.shouldBeNull()
            data.end.shouldBeNull()
            data.existing.shouldNotBeNull().jsonObject["color"]!!.jsonPrimitive.content shouldBe "yellow"
            data.dragging shouldBe true
        }

        it("TC-6: decodes an annotation tap") {
            decode("annotationTap", ANNOTATION_TAP) shouldBe ReaderEvent.AnnotationTap(
                AnnotationTapData(
                    cfiRange = "epubcfi(/6/14!/4/2/1:0,/4/2/1:24)",
                    rects = listOf(PageRect(x = 24.5, y = 310.0, width = 180.25, height = 22.0)),
                ),
            )
        }

        it("TC-7: decodes a share image") {
            decode("shareImage", SHARE_IMAGE) shouldBe ReaderEvent.ShareImage(
                ShareImageData(name = "quote-card.png", dataUrl = "data:image/png;base64,iVBORw0KGgo="),
            )
        }

        it("TC-8: maps the plain-string and payload-less types") {
            decode("hostReady", null) shouldBe ReaderEvent.HostReady
            decode("status", "ready") shouldBe ReaderEvent.Status("ready")
            decode("selectionCleared", null) shouldBe ReaderEvent.SelectionCleared
            decode("toggleChrome", null) shouldBe ReaderEvent.ToggleChrome
            decode("shareText", "Call me Ishmael.") shouldBe ReaderEvent.ShareText("Call me Ishmael.")
            decode("turnRequest", "next") shouldBe ReaderEvent.TurnRequest("next")
        }

        it("TC-9: covers every type posted by reader.html") {
            // Unit tests run with the module directory as the working directory.
            val readerHtml = File("src/main/assets/reader/reader.html").readText()
            val posted = Regex("""send\("([A-Za-z]+)"""").findAll(readerHtml).map { it.groupValues[1] }.toSet()
            val payloads = mapOf(
                "hostReady" to null,
                "status" to "ready",
                "relocate" to RELOCATE,
                "toc" to TOC_BARE,
                "selection" to SELECTION,
                "selectionCleared" to null,
                "annotationTap" to ANNOTATION_TAP,
                "toggleChrome" to null,
                "shareText" to "Call me Ishmael.",
                "shareImage" to SHARE_IMAGE,
                "turnRequest" to "next",
            )

            posted shouldBe payloads.keys
            payloads.forEach { (type, payload) ->
                decode(type, payload).shouldNotBeNull()
            }
        }

        it("TC-10: returns null for an unknown type") {
            decode("someNewThing", "{}").shouldBeNull()
        }

        it("TC-11: never throws on a malformed payload") {
            // Each of these must come back null, and none may throw.
            decode("relocate", """{"cfi":"epubcfi(/6/14""").shouldBeNull()
            decode("relocate", "[1,2]").shouldBeNull()
            decode("toc", "\"x\"").shouldBeNull()
            decode("relocate", "{\"page\":\"x\"}").shouldBeNull()
            decode("relocate", null).shouldBeNull()
            decode("toc", null).shouldBeNull()
            decode("selection", null).shouldBeNull()
            decode("shareImage", null).shouldBeNull()
        }

        it("TC-12: ignores unknown fields, at the top level and nested") {
            decode(
                "relocate",
                """{"cfi":"epubcfi(/6/2)","page":3,"futureField":{"a":1},"echo":true}""",
            ) shouldBe ReaderEvent.Relocate(RelocateData(cfi = "epubcfi(/6/2)", page = 3, echo = true))

            val selection = decode(
                "selection",
                """{"cfiRange":"epubcfi(/6/2)","rects":[{"x":1,"y":2,"width":3,"height":4,"extra":true}],"extra":1}""",
            ).shouldBeInstanceOf<ReaderEvent.Selection>().data
            selection.cfiRange shouldBe "epubcfi(/6/2)"
            selection.rects shouldBe listOf(PageRect(x = 1.0, y = 2.0, width = 3.0, height = 4.0))

            val toc = decode("toc", """[{"label":"A","href":"a.xhtml","level":0,"id":"x","page":1,"pct":2}]""")
            toc shouldBe ReaderEvent.Toc(listOf(TocEntry(label = "A", href = "a.xhtml", level = 0, page = 1, pct = 2)))

            decode("annotationTap", """{"cfiRange":"epubcfi(/6/2)","rects":[],"extra":null}""") shouldBe
                ReaderEvent.AnnotationTap(AnnotationTapData(cfiRange = "epubcfi(/6/2)", rects = emptyList()))

            decode("shareImage", """{"name":"a.png","dataUrl":"data:,","extra":[]}""") shouldBe
                ReaderEvent.ShareImage(ShareImageData(name = "a.png", dataUrl = "data:,"))
        }

        it("TC-13: applies defaults for explicit nulls and missing fields") {
            decode(
                "relocate",
                """{"cfi":"epubcfi(/6/2)","page":null,"totalPages":null,"chapterTitle":null}""",
            ) shouldBe ReaderEvent.Relocate(RelocateData(cfi = "epubcfi(/6/2)", page = 0, totalPages = 0, chapterTitle = ""))

            decode("relocate", "{}") shouldBe ReaderEvent.Relocate(RelocateData())
        }
    }

    describe("RelocateData") {

        it("TC-15: carries the echo flag and derives isMovement from it") {
            val echo = decode("relocate", """{"cfi":"epubcfi(/6/2)","echo":true}""")
                .shouldBeInstanceOf<ReaderEvent.Relocate>().data
            val absent = decode("relocate", """{"cfi":"epubcfi(/6/2)"}""")
                .shouldBeInstanceOf<ReaderEvent.Relocate>().data
            val notEcho = decode("relocate", """{"cfi":"epubcfi(/6/2)","echo":false}""")
                .shouldBeInstanceOf<ReaderEvent.Relocate>().data

            echo.echo shouldBe true
            absent.echo shouldBe false
            notEcho.echo shouldBe false
            echo.isMovement shouldBe false
            absent.isMovement shouldBe true
            notEcho.isMovement shouldBe true
        }
    }
})
