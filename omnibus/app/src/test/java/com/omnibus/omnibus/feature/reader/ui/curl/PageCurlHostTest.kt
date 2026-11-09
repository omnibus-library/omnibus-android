package com.omnibus.omnibus.feature.reader.ui.curl

import com.omnibus.omnibus.data.reader.ReaderPageTurn
import com.omnibus.omnibus.feature.reader.ui.ReaderScripts
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.enum
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** The events that make the snapshots stale. */
private enum class Change { Repaint, Selection, AnnotationTap, Resize, Relocate }

private const val MOVED_CFI = "epubcfi(/6/4)"

private fun PageCurlHost.change(event: Change): Unit = when (event) {
    Change.Repaint -> onRepaint()
    Change.Selection -> onSelection()
    Change.AnnotationTap -> onAnnotationTap()
    Change.Resize -> onSizeChanged()
    Change.Relocate -> onRelocate(MOVED_CFI)
}

class PageCurlHostTest : DescribeSpec({

    /** A page that settles with [next] and [prev] neighbour kinds, and answers every peek and turn with success. */
    fun scriptedPage(log: MutableList<String>, next: String = "page", prev: String = "section"): FakePage =
        FakePage(log).apply {
            replies[ReaderScripts.WHEN_SETTLED] = { settledJson(next = next, prev = prev) }
            listOf(1, -1, 0).forEach { replies[ReaderScripts.peek(it)] = { JsonPrimitive(true) } }
            listOf(1, -1).forEach { replies[ReaderScripts.turnInstant(it)] = { JsonPrimitive(true) } }
        }

    /** A host over the fakes, logging every page call, script and stage change to [log]. */
    fun sut(
        scope: CoroutineScope,
        log: MutableList<String>,
        page: FakePage = FakePage(log),
        snapshotter: FakeSnapshotter = FakeSnapshotter(log),
        stage: FakeStage = FakeStage(log),
        reducedMotion: () -> Boolean = { false },
        lowRam: Boolean = false,
    ): PageCurlHost = PageCurlHost(
        call = page::call,
        evaluate = { log += it },
        snapshotter = snapshotter,
        stage = stage,
        clock = TestFrameClock(),
        scope = scope,
        environment = CurlEnvironment(reducedMotion = reducedMotion, isLowRam = { lowRam }),
    )

    /** Marks the page ready in [host] and waits out the first capture. */
    fun TestScope.readied(host: PageCurlHost) {
        host.onPageReady()
        advanceUntilIdle()
    }

    /** A host in Curl mode, with its page ready and snapshotted, and the log cleared after the capture. */
    fun TestScope.readyCurl(
        log: MutableList<String>,
        page: FakePage = scriptedPage(log),
        snapshotter: FakeSnapshotter = FakeSnapshotter(log),
        stage: FakeStage = FakeStage(log),
        reducedMotion: () -> Boolean = { false },
        lowRam: Boolean = false,
    ): PageCurlHost {
        val host = sut(backgroundScope, log, page, snapshotter, stage, reducedMotion, lowRam)
        host.mode = ReaderPageTurn.Curl
        readied(host)
        log.clear()
        return host
    }

    describe("switching the page-turn mode") {
        context("when the mode is Slide") {
            it("TC-327: makes no page call and no script") {
                runTest {
                    val log = mutableListOf<String>()
                    val host = sut(backgroundScope, log, page = scriptedPage(log))

                    readied(host)

                    log.shouldBeEmpty()
                }
            }
        }

        context("when the mode is Curl") {
            it("TC-327: announces that the curl is on") {
                runTest {
                    val log = mutableListOf<String>()
                    val host = sut(backgroundScope, log, page = scriptedPage(log))
                    host.mode = ReaderPageTurn.Curl

                    readied(host)

                    log shouldContain ReaderScripts.setCurlReady(on = true, next = false, prev = false)
                }
            }
        }

        context("when the mode is None") {
            it("TC-327: announces that the curl is on") {
                runTest {
                    val log = mutableListOf<String>()
                    val host = sut(backgroundScope, log, page = scriptedPage(log))
                    host.mode = ReaderPageTurn.None

                    readied(host)

                    log shouldContain ReaderScripts.setCurlReady(on = true, next = false, prev = false)
                }
            }
        }

        context("when the mode changes from Curl to None") {
            it("TC-327: does not repeat the announcement") {
                runTest {
                    val log = mutableListOf<String>()
                    val host = readyCurl(log)

                    host.mode = ReaderPageTurn.None
                    advanceUntilIdle()

                    log shouldNotContain ReaderScripts.setCurlReady(on = true, next = false, prev = false)
                }
            }
        }

        context("when the mode changes from Curl to Slide") {
            it("TC-327: announces that the curl is off") {
                runTest {
                    val log = mutableListOf<String>()
                    val host = readyCurl(log)

                    host.mode = ReaderPageTurn.Slide

                    log shouldContain ReaderScripts.setCurlReady(on = false, next = false, prev = false)
                }
            }

            it("TC-327: releases the snapshots") {
                runTest {
                    val log = mutableListOf<String>()
                    val snapshotter = FakeSnapshotter(log)
                    val host = readyCurl(log, snapshotter = snapshotter)

                    host.mode = ReaderPageTurn.Slide

                    snapshotter.images.map { it.recycles } shouldBe listOf(1, 1)
                }
            }

            it("TC-327: slides the next tap") {
                runTest {
                    val log = mutableListOf<String>()
                    val host = readyCurl(log)
                    host.mode = ReaderPageTurn.Slide
                    log.clear()

                    host.tapTurn(1)

                    log shouldBe listOf(ReaderScripts.turnSlide(1))
                }
            }
        }
    }

    describe("a gutter tap") {
        context("when the mode is Curl and the next page is snapshotted") {
            it("TC-328: plays the curl before the turn lands") {
                runTest {
                    val log = mutableListOf<String>()
                    val host = readyCurl(log)

                    host.tapTurn(1)
                    advanceUntilIdle()

                    log shouldContainInOrder listOf("curl", ReaderScripts.turnInstant(1))
                }
            }

            it("TC-328: lands the turn with turnInstant") {
                runTest {
                    val log = mutableListOf<String>()
                    val host = readyCurl(log)

                    host.tapTurn(1)
                    advanceUntilIdle()

                    log shouldContain ReaderScripts.turnInstant(1)
                }
            }

            it("TC-328: never slides the page for the turn") {
                runTest {
                    val log = mutableListOf<String>()
                    val host = readyCurl(log)

                    host.tapTurn(1)
                    advanceUntilIdle()

                    log shouldNotContain ReaderScripts.turnSlide(1)
                }
            }

            it("TC-328: recaptures the next page after the turn lands") {
                runTest {
                    val log = mutableListOf<String>()
                    val host = readyCurl(log)

                    host.tapTurn(1)
                    advanceUntilIdle()

                    log.dropWhile { it != ReaderScripts.turnInstant(1) } shouldContain ReaderScripts.peek(1)
                }
            }

            it("TC-328: keeps the snapshots of the pages still shown") {
                runTest {
                    val log = mutableListOf<String>()
                    val snapshotter = FakeSnapshotter(log)
                    val host = readyCurl(log, snapshotter = snapshotter)

                    host.tapTurn(1)
                    advanceUntilIdle()

                    snapshotter.images.take(2).map { it.recycles } shouldBe listOf(0, 0)
                }
            }
        }
    }

    describe("the cover while a turn lands") {
        context("when the page confirms the turn") {
            it("TC-329: keeps the cover up while the turn is pending") {
                runTest {
                    val log = mutableListOf<String>()
                    val gate = CompletableDeferred<JsonElement?>()
                    val stage = FakeStage(log)
                    val host = readyCurl(
                        log,
                        page = scriptedPage(log).apply { replies[ReaderScripts.turnInstant(1)] = { gate.await() } },
                        stage = stage,
                    )

                    host.tapTurn(1)
                    advanceUntilIdle()

                    stage.cover.shouldNotBeNull()
                }
            }

            it("TC-329: drops the cover only after the page has drawn the turn") {
                runTest {
                    val log = mutableListOf<String>()
                    val gate = CompletableDeferred<JsonElement?>()
                    val stage = FakeStage(log)
                    val host = readyCurl(
                        log,
                        page = scriptedPage(log).apply { replies[ReaderScripts.turnInstant(1)] = { gate.await() } },
                        stage = stage,
                    )
                    host.tapTurn(1)
                    advanceUntilIdle()

                    gate.complete(JsonPrimitive(true))
                    advanceUntilIdle()

                    log shouldContainInOrder listOf(ReaderScripts.turnInstant(1), "drawn", "cover:null")
                }
            }
        }

        context("when the turn is not confirmed") {
            it("TC-329: drops the cover") {
                runTest {
                    checkAll(Exhaustive.of<JsonElement?>(JsonPrimitive(false), null)) { reply ->
                        val log = mutableListOf<String>()
                        val gate = CompletableDeferred<JsonElement?>()
                        val stage = FakeStage(log)
                        val host = readyCurl(
                            log,
                            page = scriptedPage(log).apply { replies[ReaderScripts.turnInstant(1)] = { gate.await() } },
                            stage = stage,
                        )
                        host.tapTurn(1)
                        advanceUntilIdle()

                        gate.complete(reply)
                        advanceUntilIdle()

                        stage.cover shouldBe null
                    }
                }
            }

            it("TC-329: recaptures the page") {
                runTest {
                    checkAll(Exhaustive.of<JsonElement?>(JsonPrimitive(false), null)) { reply ->
                        val log = mutableListOf<String>()
                        val gate = CompletableDeferred<JsonElement?>()
                        val host = readyCurl(
                            log,
                            page = scriptedPage(log).apply { replies[ReaderScripts.turnInstant(1)] = { gate.await() } },
                        )
                        host.tapTurn(1)
                        advanceUntilIdle()

                        gate.complete(reply)
                        advanceUntilIdle()

                        log.dropWhile { it != ReaderScripts.turnInstant(1) } shouldContain ReaderScripts.WHEN_SETTLED
                    }
                }
            }
        }

        context("when the page confirms the turn but does not draw it in time") {
            it("TC-329: drops the cover") {
                runTest {
                    val log = mutableListOf<String>()
                    val snapshotter = FakeSnapshotter(log)
                    val stage = FakeStage(log)
                    val host = readyCurl(log, snapshotter = snapshotter, stage = stage)
                    snapshotter.drawn = false

                    host.tapTurn(1)
                    advanceUntilIdle()

                    stage.cover shouldBe null
                }
            }

            it("TC-329: recaptures the page") {
                runTest {
                    val log = mutableListOf<String>()
                    val snapshotter = FakeSnapshotter(log)
                    val host = readyCurl(log, snapshotter = snapshotter)
                    snapshotter.drawn = false

                    host.tapTurn(1)
                    advanceUntilIdle()

                    log.dropWhile { it != ReaderScripts.turnInstant(1) } shouldContain ReaderScripts.WHEN_SETTLED
                }
            }
        }
    }

    describe("a gutter tap that cannot curl") {
        context("when the snapshots are stale") {
            it("TC-330: slides the turn with no curl or cover") {
                runTest {
                    val log = mutableListOf<String>()
                    val host = readyCurl(log)
                    host.onRepaint()
                    log.clear()

                    host.tapTurn(1)

                    log shouldBe listOf(ReaderScripts.turnSlide(1))
                }
            }
        }

        context("when a selection is live") {
            it("TC-330: slides the turn with no curl or cover") {
                runTest {
                    val log = mutableListOf<String>()
                    val host = readyCurl(log)
                    host.onSelection()
                    log.clear()

                    host.tapTurn(1)

                    log shouldBe listOf(ReaderScripts.turnSlide(1))
                }
            }
        }

        context("when the page is not ready") {
            it("TC-330: slides the turn with no curl or cover") {
                runTest {
                    val log = mutableListOf<String>()
                    val host = sut(backgroundScope, log, page = scriptedPage(log))
                    host.mode = ReaderPageTurn.Curl
                    log.clear()

                    host.tapTurn(1)

                    log shouldBe listOf(ReaderScripts.turnSlide(1))
                }
            }
        }

        context("when the neighbour in that direction has no snapshot") {
            it("TC-330: slides the turn with no curl or cover") {
                runTest {
                    val log = mutableListOf<String>()
                    val host = readyCurl(log, page = scriptedPage(log, next = "none"))

                    host.tapTurn(1)

                    log shouldBe listOf(ReaderScripts.turnSlide(1))
                }
            }
        }

        context("when the device is low on RAM") {
            it("TC-330: slides the turn with no curl or cover") {
                runTest {
                    val log = mutableListOf<String>()
                    val host = readyCurl(log, lowRam = true)

                    host.tapTurn(1)

                    log shouldBe listOf(ReaderScripts.turnSlide(1))
                }
            }
        }
    }

    describe("a turn across a chapter boundary") {
        context("when the next section is not laid out") {
            it("TC-331: shows the cover, then the curl, then the landed cover, in that order") {
                runTest {
                    val log = mutableListOf<String>()
                    val host = readyCurl(log, page = scriptedPage(log, next = "section"))

                    host.tapTurn(1)
                    advanceUntilIdle()

                    log shouldContainInOrder listOf(
                        "cover:snap-1",
                        ReaderScripts.turnInstant(1),
                        "drawn",
                        "snapshot",
                        "curl",
                        "cover:snap-2",
                        "curl:null",
                        "cover:null",
                    )
                }
            }
        }
    }

    describe("a turn with no motion") {
        context("when the mode is None") {
            it("TC-332: lands the tap with turnInstant") {
                runTest {
                    val log = mutableListOf<String>()
                    val host = sut(backgroundScope, log, page = scriptedPage(log))
                    host.mode = ReaderPageTurn.None
                    readied(host)
                    log.clear()

                    host.tapTurn(1)
                    advanceUntilIdle()

                    log shouldContain ReaderScripts.turnInstant(1)
                }
            }

            it("TC-332: takes no snapshot and draws no curl") {
                runTest {
                    val log = mutableListOf<String>()
                    val host = sut(backgroundScope, log, page = scriptedPage(log))
                    host.mode = ReaderPageTurn.None
                    readied(host)
                    log.clear()

                    host.tapTurn(1)
                    advanceUntilIdle()

                    log shouldNotContain "snapshot"
                    log shouldNotContain "curl"
                }
            }
        }

        context("when reduced motion turns on after the snapshots are taken") {
            it("TC-332: lands the tap with turnInstant") {
                runTest {
                    val log = mutableListOf<String>()
                    var reduced = false
                    val host = readyCurl(log, reducedMotion = { reduced })
                    reduced = true

                    host.tapTurn(1)
                    advanceUntilIdle()

                    log shouldContain ReaderScripts.turnInstant(1)
                }
            }

            it("TC-332: takes no snapshot and draws no curl") {
                runTest {
                    val log = mutableListOf<String>()
                    var reduced = false
                    val host = readyCurl(log, reducedMotion = { reduced })
                    reduced = true

                    host.tapTurn(1)
                    advanceUntilIdle()

                    log shouldNotContain "snapshot"
                    log shouldNotContain "curl"
                }
            }
        }
    }

    describe("taps that arrive during a turn") {
        context("when three forward taps arrive during a curl") {
            it("TC-333: runs the first and the two queued taps, and drops a fourth") {
                runTest {
                    val log = mutableListOf<String>()
                    val host = readyCurl(log)

                    host.tapTurn(1)
                    host.tapTurn(1)
                    host.tapTurn(1)
                    host.tapTurn(1)
                    advanceUntilIdle()

                    log.count { it == ReaderScripts.turnInstant(1) } shouldBe 3
                }
            }
        }
    }

    describe("snapshots going stale") {
        it("TC-334: makes the snapshots stale and releases them at once, for every change") {
            runTest {
                checkAll(Exhaustive.enum<Change>()) { change ->
                    val log = mutableListOf<String>()
                    val snapshotter = FakeSnapshotter(log)
                    val host = readyCurl(log, snapshotter = snapshotter)

                    host.change(change)

                    host.isReadyToCurl shouldBe false
                    snapshotter.images.map { it.recycles } shouldBe listOf(1, 1)
                }
            }
        }

        it("TC-334: recaptures 120 ms after a repaint, a tap or a relocate, and not before") {
            runTest {
                checkAll(Exhaustive.of(Change.Repaint, Change.AnnotationTap, Change.Resize, Change.Relocate)) { change ->
                    val log = mutableListOf<String>()
                    val host = readyCurl(log)

                    host.change(change)
                    log.clear()
                    advanceTimeBy(119)
                    log shouldNotContain ReaderScripts.WHEN_SETTLED
                    advanceTimeBy(1)
                    runCurrent()

                    log shouldContain ReaderScripts.WHEN_SETTLED
                }
            }
        }

        it("TC-334: waits for a selection to clear before recapturing") {
            runTest {
                val log = mutableListOf<String>()
                val host = readyCurl(log)
                host.onSelection()
                advanceUntilIdle()
                log shouldNotContain ReaderScripts.WHEN_SETTLED

                host.onSelectionCleared()
                advanceUntilIdle()

                log shouldContain ReaderScripts.WHEN_SETTLED
            }
        }

        context("when a relocate lands during the host's own turn") {
            it("TC-334: leaves the snapshots fresh") {
                runTest {
                    val log = mutableListOf<String>()
                    val gate = CompletableDeferred<JsonElement?>()
                    val host = readyCurl(
                        log,
                        page = scriptedPage(log).apply { replies[ReaderScripts.turnInstant(1)] = { gate.await() } },
                    )
                    host.tapTurn(1)
                    advanceUntilIdle()

                    host.onRelocate(MOVED_CFI)
                    gate.complete(JsonPrimitive(true))
                    advanceUntilIdle()

                    host.isReadyToCurl shouldBe true
                }
            }
        }
    }

    describe("releasing the host") {
        context("when a turn is pending on the page") {
            it("TC-335: frees every snapshot") {
                runTest {
                    val log = mutableListOf<String>()
                    val gate = CompletableDeferred<JsonElement?>()
                    val snapshotter = FakeSnapshotter(log)
                    val host = readyCurl(
                        log,
                        page = scriptedPage(log).apply { replies[ReaderScripts.turnInstant(1)] = { gate.await() } },
                        snapshotter = snapshotter,
                    )
                    host.tapTurn(1)
                    advanceUntilIdle()

                    host.release()

                    snapshotter.images.map { it.recycles } shouldBe listOf(1, 1)
                }
            }

            it("TC-335: evaluates nothing after the release") {
                runTest {
                    val log = mutableListOf<String>()
                    val gate = CompletableDeferred<JsonElement?>()
                    val host = readyCurl(
                        log,
                        page = scriptedPage(log).apply { replies[ReaderScripts.turnInstant(1)] = { gate.await() } },
                    )
                    host.tapTurn(1)
                    advanceUntilIdle()
                    host.release()
                    val atRelease = log.toList()

                    gate.complete(JsonPrimitive(true))
                    host.tapTurn(1)
                    host.onRepaint()
                    host.onRelocate(MOVED_CFI)
                    advanceUntilIdle()

                    log shouldBe atRelease
                }
            }
        }
    }
})
