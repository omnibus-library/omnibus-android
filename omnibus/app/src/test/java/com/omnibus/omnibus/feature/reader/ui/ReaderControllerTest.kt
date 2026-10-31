package com.omnibus.omnibus.feature.reader.ui

import com.omnibus.omnibus.data.reader.BookFileResult
import com.omnibus.omnibus.data.reader.ReaderSettings
import com.omnibus.omnibus.data.reader.ReaderTheme
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

// TC-17 and TC-18 are the existing controller cases, ported to ReaderEvent. Their bodies are unchanged;
// only the send helper above now decodes the envelope first.
class ReaderControllerTest : DescribeSpec({

    val dark = ReaderSettings()
    val light = ReaderSettings(theme = ReaderTheme.Light, fontSize = 24)

    lateinit var page: MutableList<String>
    lateinit var evaluate: (String) -> Unit

    fun controller(startCfi: String? = null): ReaderController =
        ReaderController(startCfi).also {
            it.configure("book-1", dark)
            it.attach(evaluate)
        }

    // Decodes the envelope the way the host does, so each case below drives the controller with the
    // same events the page sends. Events the controller doesn't act on are dropped by the decode.
    fun ReaderController.send(type: String, payload: String? = null) {
        ReaderEvent.decode(ReaderMessage(type, payload))?.let { handle(it) }
    }

    fun ReaderController.relocate(cfi: String, echo: Boolean = false) =
        send("relocate", """{"cfi":"$cfi","echo":$echo}""")

    beforeEach {
        page = mutableListOf()
        evaluate = { page += it }
    }

    describe("boot") {

        // TC-18: a hostReady or status event the controller doesn't act on, and every other event type, leaves it as it was.
        it("TC-18: ignores events it doesn't act on") {
            val reader = controller(startCfi = "start")
            reader.send("status", "something-else")
            reader.send("toc", """[{"label":"A","href":"a.xhtml","level":0}]""")
            reader.send("toggleChrome")
            reader.send("selectionCleared")
            reader.send("shareText", "text")
            reader.send("turnRequest", "next")

            reader.status shouldBe ReaderLoadStatus.Loading
            reader.restoreCfi shouldBe "start"
            page.shouldBeEmpty()
        }

        it("TC-248: boots the page when it reports hostReady, at the start position in the current settings") {
            val reader = controller(startCfi = "epubcfi(/6/2)")

            reader.send("hostReady")

            page.size shouldBe 1
            page[0] shouldContain "OmnibusReader.init('stage', \"https://appassets.androidplatform.net/book/book-1.epub\""
            page[0] shouldContain "\"cfi\":\"epubcfi(/6/2)\""
            page[0] shouldContain "\"theme\":\"dark\""
        }

        it("TC-249: boots without a position when there is none to restore") {
            controller().send("hostReady")

            page[0] shouldNotContain "\"cfi\""
        }

        it("TC-250: does not boot a page it can't address, or before it knows the book") {
            ReaderController().apply { attach(evaluate) }.send("hostReady")
            ReaderController().apply { configure("book-1", dark) }.send("hostReady")

            page.shouldBeEmpty()
        }

        it("TC-251: stays loading until the page reports ready, then is ready") {
            val reader = controller()
            reader.send("hostReady")
            reader.status shouldBe ReaderLoadStatus.Loading

            reader.send("status", "ready")

            reader.status shouldBe ReaderLoadStatus.Ready
        }
    }

    describe("position") {

        it("TC-252: follows real reading movement") {
            val reader = controller(startCfi = "start")

            reader.relocate("epubcfi(/6/8)")

            reader.restoreCfi shouldBe "epubcfi(/6/8)"
        }

        // TC-17: the echo relocate leaves restoreCfi alone, now carried as a decoded RelocateData.
        it("TC-253: ignores an echo, such as a rotation's corrected re-pagination") {
            val reader = controller(startCfi = "start")
            reader.relocate("epubcfi(/6/8)")

            reader.relocate("epubcfi(/6/6)", echo = true)

            reader.restoreCfi shouldBe "epubcfi(/6/8)"
        }

        it("TC-254: ignores a relocate that carries no position or can't be read") {
            val reader = controller(startCfi = "start")

            reader.send("relocate", """{"cfi":"","echo":false}""")
            reader.send("relocate", "garbage")
            reader.send("relocate", null)

            reader.restoreCfi shouldBe "start"
        }
    }

    describe("recovery") {

        it("TC-255: reboots a replacement page at the last position in the settings in force then") {
            val reader = controller(startCfi = "start")
            reader.send("hostReady")
            reader.send("status", "ready")
            reader.relocate("epubcfi(/6/10)")
            reader.configure("book-1", light)
            page.clear()

            reader.onRendererGone() shouldBe true
            reader.status shouldBe ReaderLoadStatus.Loading
            val replacement = mutableListOf<String>()
            reader.attach { replacement += it }
            reader.send("hostReady")

            replacement.size shouldBe 1
            replacement[0] shouldContain "\"cfi\":\"epubcfi(/6/10)\""
            replacement[0] shouldContain "\"theme\":\"light\""
            replacement[0] shouldContain "\"fontSize\":24"
            page.shouldBeEmpty()
        }

        it("TC-256: gives up after the renderer dies repeatedly without ever painting a page") {
            val reader = controller()

            repeat(3) { reader.onRendererGone() shouldBe true }
            reader.onRendererGone() shouldBe false

            reader.status shouldBe ReaderLoadStatus.Failed(ReaderLoadFailure.RendererCrashed)
        }

        it("TC-257: counts only consecutive losses, so a page that painted in between starts the count over") {
            val reader = controller()

            repeat(3) { reader.onRendererGone() }
            reader.send("status", "ready")

            repeat(3) { reader.onRendererGone() shouldBe true }
        }

        it("TC-258: treats a second hostReady as a replaced page and shows loading again") {
            val reader = controller()
            reader.send("hostReady")
            reader.send("status", "ready")

            reader.send("hostReady")

            reader.status shouldBe ReaderLoadStatus.Loading
            page.count { it.startsWith("OmnibusReader.init") } shouldBe 2
        }

        it("TC-259: a failed load is cleared for a retry") {
            val reader = controller()
            reader.onBookFailure(BookFileResult.NotFound)

            reader.reset()

            reader.status shouldBe ReaderLoadStatus.Loading
        }
    }

    describe("failure") {

        it("TC-260: reports each way the book can fail to open") {
            mapOf(
                BookFileResult.Unauthenticated to ReaderLoadFailure.Unauthenticated,
                BookFileResult.NotFound to ReaderLoadFailure.NotFound,
                BookFileResult.NoServer to ReaderLoadFailure.NoServer,
                BookFileResult.Error to ReaderLoadFailure.Unreachable,
            ).forEach { (failure, reason) ->
                val reader = controller()
                reader.onBookFailure(failure)
                reader.status shouldBe ReaderLoadStatus.Failed(reason)
            }
        }

        it("TC-261: reports epub.js and page load failures as a render failure") {
            val fromGlue = controller().apply { send("status", "error") }
            val fromPage = controller().apply { onPageLoadFailed() }

            fromGlue.status shouldBe ReaderLoadStatus.Failed(ReaderLoadFailure.Render)
            fromPage.status shouldBe ReaderLoadStatus.Failed(ReaderLoadFailure.Render)
        }

        it("TC-262: lets the specific reason replace a generic render failure, but not the reverse") {
            val renderFirst = controller()
            renderFirst.send("status", "error")
            renderFirst.onBookFailure(BookFileResult.NotFound)
            renderFirst.status shouldBe ReaderLoadStatus.Failed(ReaderLoadFailure.NotFound)

            val specificFirst = controller()
            specificFirst.onBookFailure(BookFileResult.NotFound)
            specificFirst.send("status", "error")
            specificFirst.status shouldBe ReaderLoadStatus.Failed(ReaderLoadFailure.NotFound)
        }

        it("TC-263: ignores a failure that arrives after the first page painted") {
            val reader = controller()
            reader.send("status", "ready")

            reader.onBookFailure(BookFileResult.Error)
            reader.send("status", "error")

            reader.status shouldBe ReaderLoadStatus.Ready
        }
    }

    describe("settings") {

        it("TC-264: applies a change to a page that is showing") {
            val reader = controller()
            reader.send("hostReady")
            reader.send("status", "ready")
            page.clear()

            reader.configure("book-1", dark.copy(fontSize = 22))

            page.shouldContainExactly("OmnibusReader.setFontSize(22)")
        }

        it("TC-265: applies a change made between boot and first paint once the page is ready") {
            val reader = controller()
            reader.send("hostReady")
            page.clear()

            reader.configure("book-1", dark.copy(fontSize = 22))
            page.shouldBeEmpty()
            reader.send("status", "ready")

            page.shouldContainExactly("OmnibusReader.setFontSize(22)")
        }

        it("TC-266: does not resend what the page already shows") {
            val reader = controller()
            reader.send("hostReady")
            reader.send("status", "ready")
            reader.configure("book-1", dark.copy(fontSize = 22))
            page.clear()

            reader.configure("book-1", dark.copy(fontSize = 22))

            page.shouldBeEmpty()
        }
    }

    describe("status") {

        it("TC-19: status transitions are observable in order") {
            val reader = controller()
            val seen = mutableListOf(reader.status)
            fun step(action: () -> Unit) {
                action()
                seen += reader.status
            }

            step { reader.send("hostReady") }
            step { reader.send("status", "ready") }
            step { reader.onRendererGone() }
            step { reader.send("status", "ready") }
            step { reader.reset() }
            step { reader.onBookFailure(BookFileResult.NotFound) }

            seen shouldBe listOf(
                ReaderLoadStatus.Loading,
                ReaderLoadStatus.Loading,
                ReaderLoadStatus.Ready,
                ReaderLoadStatus.Loading,
                ReaderLoadStatus.Ready,
                ReaderLoadStatus.Loading,
                ReaderLoadStatus.Failed(ReaderLoadFailure.NotFound),
            )
        }
    }

    describe("commands") {

        it("TC-267: sends page turns, navigation and teardown to the page") {
            val reader = controller()

            reader.next()
            reader.previous()
            reader.display("epubcfi(/6/4)")
            reader.teardown()

            page.shouldContainExactly(
                "OmnibusReader.next()",
                "OmnibusReader.prev()",
                "OmnibusReader.display(\"epubcfi(/6/4)\")",
                "OmnibusReader.destroy()",
            )
        }

        it("TC-268: does nothing once the page is detached, and a stale detach leaves a newer page alone") {
            val reader = controller()
            val newer = mutableListOf<String>()
            val newerEvaluate: (String) -> Unit = { newer += it }

            reader.attach(newerEvaluate)
            reader.detach(evaluate)
            reader.next()
            newer.shouldContainExactly("OmnibusReader.next()")

            reader.detach(newerEvaluate)
            reader.next()
            newer.size shouldBe 1
            page.shouldBeEmpty()
        }
    }
})
