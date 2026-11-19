package com.omnibus.omnibus.feature.reader.ui

import com.omnibus.omnibus.feature.reader.ui.ReaderEventFixtures.ANNOTATION_TAP
import com.omnibus.omnibus.feature.reader.ui.ReaderEventFixtures.ANNOTATION_TAP_HITS
import com.omnibus.omnibus.feature.reader.ui.ReaderEventFixtures.RELOCATE
import com.omnibus.omnibus.feature.reader.ui.ReaderEventFixtures.SELECTION
import com.omnibus.omnibus.feature.reader.ui.ReaderEventFixtures.SHARE_IMAGE
import com.omnibus.omnibus.feature.reader.ui.ReaderEventFixtures.TOC_ENRICHED
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import io.kotest.property.exhaustive.of
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

class ReaderEventTest : DescribeSpec({

    fun decode(type: String, payload: String?) = ReaderEvent.decode(ReaderMessage(type, payload))

    describe("decoding a page message") {
        it("TC-1: reads a captured relocate through the envelope") {
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

        it("TC-3: reads a toc with page and pct") {
            val event = decode("toc", TOC_ENRICHED).shouldBeInstanceOf<ReaderEvent.Toc>()

            event.entries.map { it.page } shouldBe listOf(1, 12, 31)
            event.entries.map { it.pct } shouldBe listOf(0, 4, 10)
        }

        it("TC-8: maps the plain-string and payload-less types") {
            decode("hostReady", null) shouldBe ReaderEvent.HostReady
            decode("status", "ready") shouldBe ReaderEvent.Status("ready")
            decode("selectionCleared", null) shouldBe ReaderEvent.SelectionCleared
            decode("toggleChrome", null) shouldBe ReaderEvent.ToggleChrome
            decode("shareText", "Call me Ishmael.") shouldBe ReaderEvent.ShareText("Call me Ishmael.")
            decode("turnRequest", "1") shouldBe ReaderEvent.TurnRequest(1)
        }

        it("TC-9: covers every type posted by reader.html") {
            // Unit tests run with the module directory as the working directory.
            val readerHtml = File("src/main/assets/reader/reader.html").readText()
            val posted = Regex("""send\("([A-Za-z]+)"""").findAll(readerHtml).map { it.groupValues[1] }.toSet()
            val payloads = mapOf(
                "hostReady" to null,
                "status" to "ready",
                "relocate" to RELOCATE,
                "toc" to TOC_ENRICHED,
                "selection" to SELECTION,
                "selectionCleared" to null,
                "annotationTap" to ANNOTATION_TAP,
                "toggleChrome" to null,
                "shareText" to "Call me Ishmael.",
                "shareImage" to SHARE_IMAGE,
                "turnRequest" to "1",
            )

            posted shouldBe payloads.keys
            checkAll(Exhaustive.collection(payloads.entries.toList())) { (type, payload) ->
                decode(type, payload).shouldNotBeNull()
            }
        }

        it("TC-10: returns null for an unknown type") {
            decode("someNewThing", "{}").shouldBeNull()
        }

        it("TC-11: returns null for a truncated, wrong-shaped, wrong-typed or missing payload") {
            checkAll(
                Exhaustive.of(
                    "relocate" to """{"cfi":"epubcfi(/6/14""",
                    "relocate" to "[1,2]",
                    "toc" to "\"x\"",
                    "relocate" to "{\"page\":\"x\"}",
                    "relocate" to null,
                    "toc" to null,
                    "selection" to null,
                    "shareImage" to null,
                ),
            ) { (type, payload) ->
                decode(type, payload).shouldBeNull()
            }
        }

        it("TC-12: ignores unknown fields") {
            decode("relocate", """{"cfi":"epubcfi(/6/2)","futureField":{"a":1}}""") shouldBe
                ReaderEvent.Relocate(RelocateData(cfi = "epubcfi(/6/2)"))
        }

        it("TC-13: applies defaults for explicit nulls") {
            decode("relocate", """{"page":null,"totalPages":null,"chapterTitle":null}""") shouldBe
                ReaderEvent.Relocate(RelocateData())
        }
    }

    describe("decoding a highlight tap") {
        it("#299 TC-1: decodes every hit in the payload") {
            decode("annotationTap", ANNOTATION_TAP_HITS)
                .shouldBeInstanceOf<ReaderEvent.AnnotationTap>().data.allHits shouldBe listOf(
                AnnotationHit(
                    cfiRange = "epubcfi(/6/14!/4/2/1:0,/4/2/1:24)",
                    rects = listOf(PageRect(x = 24.5, y = 310.0, width = 180.25, height = 22.0)),
                ),
                AnnotationHit(
                    cfiRange = "epubcfi(/6/14!/4/2/1:12,/4/2/1:40)",
                    rects = listOf(PageRect(x = 120.0, y = 310.0, width = 210.5, height = 22.0)),
                ),
                AnnotationHit(
                    cfiRange = "epubcfi(/6/14!/4/2/1:30,/4/2/1:60)",
                    rects = listOf(PageRect(x = 240.0, y = 310.0, width = 96.0, height = 22.0)),
                ),
            )
        }

        it("#299 TC-1: keeps the top-level pair equal to the first hit") {
            val data = decode("annotationTap", ANNOTATION_TAP_HITS)
                .shouldBeInstanceOf<ReaderEvent.AnnotationTap>().data

            data.cfiRange shouldBe data.allHits.first().cfiRange
            data.rects shouldBe data.allHits.first().rects
        }

        it("#299 TC-2: reads a payload without hits as one hit") {
            checkAll(Exhaustive.of(ANNOTATION_TAP, """{"hits":[],""" + ANNOTATION_TAP.removePrefix("{"))) { payload ->
                decode("annotationTap", payload)
                    .shouldBeInstanceOf<ReaderEvent.AnnotationTap>().data.allHits.single() shouldBe AnnotationHit(
                    cfiRange = "epubcfi(/6/14!/4/2/1:0,/4/2/1:24)",
                    rects = listOf(PageRect(x = 24.5, y = 310.0, width = 180.25, height = 22.0)),
                )
            }
        }
    }

    describe("decoding page call replies") {
        it("TC-305: keeps the id and the value of a reply") {
            decode("hostReply", """{"id":3,"value":{"a":1}}""") shouldBe
                ReaderEvent.HostReply(3, Json.parseToJsonElement("""{"a":1}"""))
        }

        it("TC-305: reads an explicit null value as no value") {
            decode("hostReply", """{"id":3,"value":null}""") shouldBe ReaderEvent.HostReply(3, null)
        }

        it("TC-305: returns null for a malformed reply") {
            checkAll(Exhaustive.of("""{"value":1}""", "not json", "{")) { payload ->
                decode("hostReply", payload).shouldBeNull()
            }
        }
    }

    describe("decoding turn requests") {
        it("TC-305: reads 1 and -1 as the direction of the turn") {
            checkAll(Exhaustive.of("1" to 1, "-1" to -1)) { (payload, dir) ->
                decode("turnRequest", payload) shouldBe ReaderEvent.TurnRequest(dir)
            }
        }

        it("TC-305: returns null for any other direction") {
            checkAll(Exhaustive.of("0", "2", "next", "")) { payload ->
                decode("turnRequest", payload).shouldBeNull()
            }
        }
    }
})
