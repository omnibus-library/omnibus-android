package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.runtime.saveable.SaverScope
import com.omnibus.omnibus.data.highlights.HighlightColor
import com.omnibus.omnibus.data.reader.BookFileResult
import com.omnibus.omnibus.data.reader.ReaderSettings
import com.omnibus.omnibus.data.reader.randomReaderSettings
import com.omnibus.omnibus.feature.reader.PageHighlight
import com.omnibus.omnibus.feature.reader.randomPageHighlight
import com.omnibus.omnibus.testing.randomDouble
import com.omnibus.omnibus.testing.randomInt
import com.omnibus.omnibus.testing.randomOf
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.enum
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class ReaderControllerTest : DescribeSpec({

    fun sut(
        page: MutableList<String> = mutableListOf(),
        startCfi: String? = randomCfi(),
        bookUuid: String = randomString(),
        settings: ReaderSettings = randomReaderSettings(),
    ) = ReaderController(startCfi).apply {
        configure(bookUuid, settings)
        attach { page += it }
    }

    fun ReaderController.hostReady() = handle(ReaderEvent.HostReady)

    fun ReaderController.pageReady() = handle(ReaderEvent.Status("ready"))

    fun ReaderController.relocate(cfi: String?, echo: Boolean = false, settledSeek: Int? = null) =
        handle(ReaderEvent.Relocate(randomRelocateData().copy(cfi = cfi, echo = echo, settledSeek = settledSeek)))

    /** A copy that differs in font size, whichever size this is. */
    fun ReaderSettings.withOtherFontSize() = withFontSizeStep(if (canIncreaseFontSize) 1 else -1)

    /** The controller a rotation brings back: saved, then restored. */
    fun ReaderController.savedAndRestored(): ReaderController =
        ReaderControllerSaver.restore(with(ReaderControllerSaver) { SaverScope { true }.save(this@savedAndRestored) }!!)!!

    /** The page's mark calls: the clear and each add or remove. */
    fun List<String>.marks(): List<String> = filter {
        it == ReaderScripts.CLEAR_ANNOTATIONS ||
            it.startsWith("OmnibusReader.addAnnotation") ||
            it.startsWith("OmnibusReader.removeAnnotation")
    }

    fun List<PageHighlight>.adds(): List<String> = map { ReaderScripts.addAnnotation(it.cfiRange, it.color, it.hasNote) }

    /** A ready page showing [rows], with the calls that got it there cleared from [page]. */
    fun readyShowing(page: MutableList<String>, rows: List<PageHighlight>): ReaderController = sut(page).apply {
        applyHighlights(rows)
        hostReady()
        pageReady()
        page.clear()
    }

    describe("booting the page") {
        it("TC-248: boots at the start position in the current settings") {
            val page = mutableListOf<String>()
            val uuid = randomString()
            val settings = randomReaderSettings()
            val cfi = randomCfi()
            val reader = sut(page, cfi, uuid, settings)

            reader.hostReady()

            page shouldContainExactly listOf(ReaderScripts.boot(uuid, settings, cfi))
        }

        context("before it knows the book") {
            it("TC-250: does not boot") {
                val page = mutableListOf<String>()

                ReaderController().apply { attach { page += it } }.hostReady()

                page.shouldBeEmpty()
            }
        }
    }

    describe("the position to reboot at") {
        context("with an echo") {
            it("TC-253: stays where the reader was") {
                val reader = sut()
                val moved = randomCfi()
                reader.relocate(moved)

                reader.relocate(randomCfi(), echo = true)

                reader.restoreCfi shouldBe moved
            }
        }

        context("with a relocate that carries no position") {
            it("TC-254: stays where it was, for a null or empty cfi") {
                val start = randomCfi()
                val reader = sut(startCfi = start)

                checkAll(Exhaustive.of(null, "")) { cfi -> reader.relocate(cfi) }

                reader.restoreCfi shouldBe start
            }
        }
    }

    describe("recovering from a lost page") {
        context("when the renderer dies") {
            it("TC-255: reboots a replacement page at the last position in the settings in force then") {
                val uuid = randomString()
                val settings = randomReaderSettings()
                val reader = sut(bookUuid = uuid, settings = settings)
                reader.hostReady()
                reader.pageReady()
                val moved = randomCfi()
                reader.relocate(moved)
                val changed = settings.withOtherFontSize()
                reader.configure(uuid, changed)
                reader.onRendererGone()
                val replacement = mutableListOf<String>()
                reader.attach { replacement += it }

                reader.hostReady()

                replacement shouldContainExactly listOf(ReaderScripts.boot(uuid, changed, moved))
            }
        }

        context("when the renderer keeps dying without painting a page") {
            it("TC-256: gives up") {
                val reader = sut()

                repeat(3) { reader.onRendererGone() shouldBe true }
                reader.onRendererGone() shouldBe false

                reader.status shouldBe ReaderLoadStatus.Failed(ReaderLoadFailure.RendererCrashed)
            }
        }

        context("when a page painted between losses") {
            it("TC-257: starts the count over") {
                val reader = sut()
                repeat(3) { reader.onRendererGone() }

                reader.pageReady()

                repeat(3) { reader.onRendererGone() shouldBe true }
            }
        }

        context("with a second hostReady") {
            it("TC-258: shows loading again") {
                val reader = sut()
                reader.hostReady()
                reader.pageReady()

                reader.hostReady()

                reader.status shouldBe ReaderLoadStatus.Loading
            }
        }

        context("after a failed load") {
            context("when reset for a retry") {
                it("TC-259: is loading") {
                    val reader = sut()
                    reader.onBookFailure(BookFileResult.NotFound)

                    reader.reset()

                    reader.status shouldBe ReaderLoadStatus.Loading
                }
            }
        }
    }

    describe("failing to open the book") {
        it("TC-260: reports each way the book can fail") {
            checkAll(
                Exhaustive.of(
                    BookFileResult.Unauthenticated to ReaderLoadFailure.Unauthenticated,
                    BookFileResult.NotFound to ReaderLoadFailure.NotFound,
                    BookFileResult.NoServer to ReaderLoadFailure.NoServer,
                    BookFileResult.Error to ReaderLoadFailure.Unreachable,
                ),
            ) { (failure, reason) ->
                val reader = sut()

                reader.onBookFailure(failure)

                reader.status shouldBe ReaderLoadStatus.Failed(reason)
            }
        }

        it("TC-261: reports an epub.js error or a failed page load as a render failure") {
            checkAll(
                Exhaustive.of<(ReaderController) -> Unit>(
                    { it.handle(ReaderEvent.Status("error")) },
                    { it.onPageLoadFailed() },
                ),
            ) { fail ->
                val reader = sut()

                fail(reader)

                reader.status shouldBe ReaderLoadStatus.Failed(ReaderLoadFailure.Render)
            }
        }

        it("TC-262: keeps the specific reason over a render failure, in either order") {
            val renderFirst = sut()
            renderFirst.handle(ReaderEvent.Status("error"))
            renderFirst.onBookFailure(BookFileResult.NotFound)
            val specificFirst = sut()
            specificFirst.onBookFailure(BookFileResult.NotFound)
            specificFirst.handle(ReaderEvent.Status("error"))

            renderFirst.status shouldBe ReaderLoadStatus.Failed(ReaderLoadFailure.NotFound)
            specificFirst.status shouldBe ReaderLoadStatus.Failed(ReaderLoadFailure.NotFound)
        }

        context("after the first page painted") {
            it("TC-263: stays ready") {
                val reader = sut()
                reader.pageReady()

                reader.onBookFailure(BookFileResult.Error)
                reader.handle(ReaderEvent.Status("error"))

                reader.status shouldBe ReaderLoadStatus.Ready
            }
        }
    }

    describe("changing the settings") {
        context("on a page that is showing") {
            it("TC-264: applies the change") {
                val page = mutableListOf<String>()
                val uuid = randomString()
                val settings = randomReaderSettings()
                val reader = sut(page, bookUuid = uuid, settings = settings)
                reader.hostReady()
                reader.pageReady()
                page.clear()
                val changed = settings.withOtherFontSize()

                reader.configure(uuid, changed)

                page shouldContainExactly ReaderScripts.settingsChange(settings, changed)
            }
        }

        context("between boot and first paint") {
            context("when the page is ready") {
                it("TC-265: applies the change") {
                    val page = mutableListOf<String>()
                    val uuid = randomString()
                    val settings = randomReaderSettings()
                    val reader = sut(page, bookUuid = uuid, settings = settings)
                    reader.hostReady()
                    page.clear()
                    val changed = settings.withOtherFontSize()
                    reader.configure(uuid, changed)
                    page.shouldBeEmpty()

                    reader.pageReady()

                    page shouldContainExactly ReaderScripts.settingsChange(settings, changed) +
                        ReaderScripts.CLEAR_ANNOTATIONS
                }
            }
        }
    }

    describe("detaching a page") {
        context("when a newer page has taken over") {
            it("TC-268: leaves the newer page attached") {
                val newer = mutableListOf<String>()
                val staleEvaluate: (String) -> Unit = {}
                val reader = ReaderController().apply {
                    attach(staleEvaluate)
                    attach { newer += it }
                }

                reader.detach(staleEvaluate)
                reader.next()

                newer shouldContainExactly listOf(ReaderScripts.NEXT)
            }
        }
    }

    describe("setting the opening position") {
        it("TC-44: takes effect once") {
            val reader = ReaderController()
            val first = randomCfi()
            reader.startAt(first)
            reader.restoreCfi shouldBe first

            reader.startAt(randomCfi())

            reader.restoreCfi shouldBe first
        }

        context("after a rotation restored the controller") {
            it("TC-44: keeps the moved position") {
                val reader = ReaderController().apply { startAt(randomCfi()) }
                val moved = randomCfi()
                reader.relocate(moved)

                val restored = reader.savedAndRestored()
                restored.startAt(randomCfi())

                restored.restoreCfi shouldBe moved
            }
        }
    }

    describe("calls into a page that goes away") {
        context("while a call is pending") {
            it("TC-307: answers the call null at once, whether the page is detached, lost or replaced") {
                checkAll(
                    Exhaustive.of<(ReaderController, (String) -> Unit) -> Unit>(
                        { reader, evaluate -> reader.detach(evaluate) },
                        { reader, _ -> reader.onRendererGone() },
                        { reader, _ -> reader.hostReady() },
                    ),
                ) { goAway ->
                    runTest {
                        val evaluate: (String) -> Unit = {}
                        val reader = ReaderController().apply { attach(evaluate) }
                        val reply = async { reader.call("1+1") }
                        runCurrent()

                        goAway(reader, evaluate)
                        runCurrent()

                        reply.isCompleted shouldBe true
                        reply.await().shouldBeNull()
                    }
                }
            }
        }
    }

    describe("seeking") {
        context("before the page is ready") {
            it("TC-89: parks the seek until it is") {
                val page = mutableListOf<String>()
                val reader = sut(page)

                reader.seek(0.5)
                page.shouldBeEmpty()

                reader.pageReady()
                page shouldContainExactly listOf(ReaderScripts.CLEAR_ANNOTATIONS, ReaderScripts.seek(0.5, 1))
            }
        }

        context("with a fraction out of range") {
            it("TC-89: sends only in-range fractions, for too large, too small and NaN") {
                val page = mutableListOf<String>()
                val reader = sut(page)
                reader.pageReady()

                reader.seek(1.7)
                reader.seek(-1.0)
                reader.seek(Double.NaN)

                page shouldContainExactly listOf(
                    ReaderScripts.CLEAR_ANNOTATIONS,
                    ReaderScripts.seek(1.0, 1),
                    ReaderScripts.seek(0.0, 2),
                )
            }
        }

        context("when the page is replaced") {
            it("TC-90: resends a pending seek until it settles") {
                val page = mutableListOf<String>()
                val reader = sut(page)
                val seek = ReaderScripts.seek(0.5, 1)
                reader.hostReady()
                reader.pageReady()
                reader.seek(0.5)

                reader.hostReady()
                reader.pageReady()
                page.count { it == seek } shouldBe 2

                reader.relocate(randomCfi(), settledSeek = 1)
                reader.hostReady()
                reader.pageReady()
                page.count { it == seek } shouldBe 2
            }
        }

        context("after a rotation restored the controller") {
            it("TC-90: sends a pending seek to the restored controller's page") {
                val restoredPage = mutableListOf<String>()
                val reader = sut()
                reader.seek(0.7)

                val restored = reader.savedAndRestored()
                restored.attach { restoredPage += it }
                restored.pageReady()

                restoredPage shouldContainExactly listOf(ReaderScripts.CLEAR_ANNOTATIONS, ReaderScripts.seek(0.7, 1))
            }
        }

        context("when the reader navigates first") {
            it("TC-91: drops the pending seek, for next, previous or a display") {
                checkAll(
                    Exhaustive.of<(ReaderController) -> Unit>(
                        { it.next() },
                        { it.previous() },
                        { it.display(randomString()) },
                    ),
                ) { navigate ->
                    val page = mutableListOf<String>()
                    val reader = sut(page)
                    reader.seek(0.5)

                    navigate(reader)
                    reader.pageReady()

                    page shouldNotContain ReaderScripts.seek(0.5, 1)
                }
            }
        }
    }

    describe("showing the highlights on the page") {
        context("applied before the page is ready") {
            it("#54 TC-14: sends nothing until the page is ready") {
                val page = mutableListOf<String>()
                val reader = sut(page)

                reader.applyHighlights(List(randomInt(1..4)) { randomPageHighlight() })

                page.shouldBeEmpty()
            }

            it("#54 TC-14: paints each wanted highlight once at ready, after a clear") {
                val page = mutableListOf<String>()
                val reader = sut(page)
                val rows = List(randomInt(1..4)) { randomPageHighlight() }
                reader.applyHighlights(rows)

                reader.hostReady()
                reader.pageReady()

                page.marks() shouldContainExactly listOf(ReaderScripts.CLEAR_ANNOTATIONS) + rows.adds()
            }
        }

        context("when the page loses its highlights") {
            it("#54 TC-14: paints each wanted highlight once at the next ready, after a clear") {
                checkAll(
                    Exhaustive.of<(ReaderController, (String) -> Unit) -> Unit>(
                        { reader, _ -> reader.hostReady() },
                        { reader, _ -> reader.onRendererGone() },
                        { reader, _ -> reader.reset() },
                        { reader, evaluate ->
                            reader.detach(evaluate)
                            reader.attach(evaluate)
                        },
                    ),
                ) { lose ->
                    val page = mutableListOf<String>()
                    val evaluate: (String) -> Unit = { page += it }
                    val reader = ReaderController(randomCfi()).apply {
                        configure(randomString(), randomReaderSettings())
                        attach(evaluate)
                    }
                    val rows = List(randomInt(1..4)) { randomPageHighlight() }
                    reader.applyHighlights(rows)
                    reader.hostReady()
                    reader.pageReady()
                    page.clear()
                    lose(reader, evaluate)

                    reader.pageReady()

                    page.marks() shouldContainExactly listOf(ReaderScripts.CLEAR_ANNOTATIONS) + rows.adds()
                }
            }
        }
    }

    describe("changing the highlights on a ready page") {
        context("when the wanted list is unchanged") {
            it("#54 TC-15: sends no script") {
                val page = mutableListOf<String>()
                val rows = List(randomInt(1..4)) { randomPageHighlight() }
                val reader = readyShowing(page, rows)

                reader.applyHighlights(rows.toList())

                page.shouldBeEmpty()
            }

            it("#54 TC-15: does not call onPageMarked") {
                val page = mutableListOf<String>()
                val rows = List(randomInt(1..4)) { randomPageHighlight() }
                val reader = readyShowing(page, rows)
                var marked = 0
                reader.onPageMarked = { marked++ }

                reader.applyHighlights(rows.toList())

                marked shouldBe 0
            }
        }

        context("when one row is recoloured") {
            it("#54 TC-15: sends only the change for that row") {
                val page = mutableListOf<String>()
                val rows = List(randomInt(1..4)) { randomPageHighlight() }
                val reader = readyShowing(page, rows)
                val recoloured = rows.first().let { row ->
                    row.copy(color = randomOf(HighlightColor.entries.filter { it != row.color }))
                }
                val wanted = listOf(recoloured) + rows.drop(1)

                reader.applyHighlights(wanted)

                page shouldContainExactly ReaderScripts.highlightsChange(rows, wanted)
            }

            it("#54 TC-15: calls onPageMarked once") {
                val page = mutableListOf<String>()
                val rows = List(randomInt(1..4)) { randomPageHighlight() }
                val reader = readyShowing(page, rows)
                var marked = 0
                reader.onPageMarked = { marked++ }
                val recoloured = rows.first().let { row ->
                    row.copy(color = randomOf(HighlightColor.entries.filter { it != row.color }))
                }

                reader.applyHighlights(listOf(recoloured) + rows.drop(1))

                marked shouldBe 1
            }
        }
    }

    describe("driving the selection on the page") {
        context("on a ready page") {
            it("#54 TC-16: begins a drag on either edge") {
                checkAll(Exhaustive.enum<SelectionEdge>()) { edge ->
                    val page = mutableListOf<String>()
                    val reader = sut(page).apply { hostReady(); pageReady() }
                    page.clear()

                    reader.beginEdgeDrag(edge)

                    page shouldContainExactly listOf(ReaderScripts.beginEdgeDrag(edge))
                }
            }

            it("#54 TC-16: moves the dragged handle to the caret and the finger") {
                val page = mutableListOf<String>()
                val reader = sut(page).apply { hostReady(); pageReady() }
                page.clear()
                val caretX = randomDouble(-1000.0, 1000.0).toFloat()
                val caretY = randomDouble(-1000.0, 1000.0).toFloat()
                val fingerX = randomDouble(-1000.0, 1000.0).toFloat()

                reader.dragEdge(caretX, caretY, fingerX)

                page shouldContainExactly listOf(
                    ReaderScripts.extendSelectionTo(caretX.toDouble(), caretY.toDouble(), fingerX.toDouble()),
                )
            }

            it("#54 TC-16: settles the drag") {
                val page = mutableListOf<String>()
                val reader = sut(page).apply { hostReady(); pageReady() }
                page.clear()

                reader.endEdgeDrag()

                page shouldContainExactly listOf(ReaderScripts.END_SELECTION_DRAG)
            }

            it("#54 TC-16: clears the selection") {
                val page = mutableListOf<String>()
                val reader = sut(page).apply { hostReady(); pageReady() }
                page.clear()

                reader.clearSelection()

                page shouldContainExactly listOf(ReaderScripts.CLEAR_SELECTION)
            }

            it("#54 TC-16: runs nothing for a non-finite drag coordinate") {
                checkAll(Exhaustive.of(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) { bad ->
                    val page = mutableListOf<String>()
                    val reader = sut(page).apply { hostReady(); pageReady() }
                    page.clear()

                    reader.dragEdge(bad, 1f, 2f)
                    reader.dragEdge(1f, bad, 2f)
                    reader.dragEdge(1f, 2f, bad)

                    page.shouldBeEmpty()
                }
            }
        }
    }
})
