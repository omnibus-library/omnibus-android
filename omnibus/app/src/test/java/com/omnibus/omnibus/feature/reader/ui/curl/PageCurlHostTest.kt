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
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import io.kotest.property.exhaustive.enum
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** The events that make the snapshots stale. */
private enum class Change { Repaint, Selection, AnnotationTap, Resize, Relocate }

private const val MOVED_CFI = "epubcfi(/6/4)"

/** Long enough for any chain of the host's delays and frames: a capture, a curl and a recapture take under 2 s. */
private const val SETTLE_MS = 10_000L

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

    /**
     * Runs the host's work to rest. The host's coroutines live in [backgroundScope], and `advanceUntilIdle` skips
     * background work, so this advances virtual time instead, past every delay and frame the host schedules.
     */
    fun TestScope.settle() = advanceTimeBy(SETTLE_MS)

    /** Marks the page ready in [host] and waits out the first capture. */
    fun TestScope.readied(host: PageCurlHost) {
        host.onPageReady()
        settle()
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
                    settle()

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
                    settle()

                    log shouldContainInOrder listOf("curl", ReaderScripts.turnInstant(1))
                }
            }

            it("TC-328: lands the turn with turnInstant") {
                runTest {
                    val log = mutableListOf<String>()
                    val host = readyCurl(log)

                    host.tapTurn(1)
                    settle()

                    log shouldContain ReaderScripts.turnInstant(1)
                }
            }

            it("TC-328: never slides the page for the turn") {
                runTest {
                    val log = mutableListOf<String>()
                    val host = readyCurl(log)

                    host.tapTurn(1)
                    settle()

                    log shouldNotContain ReaderScripts.turnSlide(1)
                }
            }

            it("TC-328: recaptures the next page after the turn lands") {
                runTest {
                    val log = mutableListOf<String>()
                    val host = readyCurl(log)

                    host.tapTurn(1)
                    settle()

                    log.dropWhile { it != ReaderScripts.turnInstant(1) } shouldContain ReaderScripts.peek(1)
                }
            }

            it("TC-328: keeps the snapshots of the pages still shown") {
                runTest {
                    val log = mutableListOf<String>()
                    val snapshotter = FakeSnapshotter(log)
                    val host = readyCurl(log, snapshotter = snapshotter)

                    host.tapTurn(1)
                    settle()

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
                    settle()

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
                    settle()

                    gate.complete(JsonPrimitive(true))
                    settle()

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
                        settle()

                        gate.complete(reply)
                        settle()

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
                        settle()

                        gate.complete(reply)
                        settle()

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
                    settle()

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
                    settle()

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
                    settle()

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
                    settle()

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
                    settle()

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
                    settle()

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
                    settle()

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
                    settle()

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
                settle()
                log shouldNotContain ReaderScripts.WHEN_SETTLED

                host.onSelectionCleared()
                settle()

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
                    settle()

                    host.onRelocate(MOVED_CFI)
                    gate.complete(JsonPrimitive(true))
                    settle()

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
                    settle()

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
                    settle()
                    host.release()
                    val atRelease = log.toList()

                    gate.complete(JsonPrimitive(true))
                    host.tapTurn(1)
                    host.onRepaint()
                    host.onRelocate(MOVED_CFI)
                    settle()

                    log shouldBe atRelease
                }
            }
        }
    }

    describe("claiming a swipe toward a neighbour") {
        context("when the mode is Curl") {
            it("TC-338: claims each direction by its neighbour's kind, over every next and previous kind") {
                runTest {
                    val kinds = NeighbourKind.entries.flatMap { next -> NeighbourKind.entries.map { next to it } }
                    checkAll(Exhaustive.collection(kinds)) { (next, prev) ->
                        val log = mutableListOf<String>()
                        val host = readyCurl(log, page = scriptedPage(log, next = next.token, prev = prev.token))

                        host.claimToward(1) shouldBe expectedClaim(ReaderPageTurn.Curl, next, 1)
                        host.claimToward(-1) shouldBe expectedClaim(ReaderPageTurn.Curl, prev, -1)
                    }
                }
            }
        }

        context("when the mode is None") {
            it("TC-338: claims each direction by its neighbour's kind, over every next and previous kind") {
                runTest {
                    val kinds = NeighbourKind.entries.flatMap { next -> NeighbourKind.entries.map { next to it } }
                    checkAll(Exhaustive.collection(kinds)) { (next, prev) ->
                        val log = mutableListOf<String>()
                        val page = scriptedPage(log, next = next.token, prev = prev.token)
                        val host = sut(backgroundScope, log, page = page)
                        host.mode = ReaderPageTurn.None
                        readied(host)

                        host.claimToward(1) shouldBe expectedClaim(ReaderPageTurn.None, next, 1)
                        host.claimToward(-1) shouldBe expectedClaim(ReaderPageTurn.None, prev, -1)
                    }
                }
            }
        }
    }

    describe("a swipe with no motion") {
        context("when the mode is None") {
            it("TC-339: claims a swipe toward a neighbour as a turn, never as a drag") {
                runTest {
                    checkAll(Exhaustive.of(1, -1)) { dir ->
                        val log = mutableListOf<String>()
                        val host = sut(backgroundScope, log, page = scriptedPage(log))
                        host.mode = ReaderPageTurn.None
                        readied(host)

                        host.claimToward(dir) shouldBe CurlClaim.Turn(dir)
                    }
                }
            }

            it("TC-339: lands the swipe with turnInstant alone") {
                runTest {
                    val log = mutableListOf<String>()
                    val host = sut(backgroundScope, log, page = scriptedPage(log))
                    host.mode = ReaderPageTurn.None
                    readied(host)
                    log.clear()

                    val turn = host.claimToward(1).shouldBeInstanceOf<CurlClaim.Turn>()
                    host.tapTurn(turn.dir)
                    advanceUntilIdle()

                    log.first() shouldBe ReaderScripts.turnInstant(1)
                    log shouldNotContain "snapshot"
                    log shouldNotContain "curl"
                    log shouldNotContain ReaderScripts.turnSlide(1)
                }
            }
        }
    }
})

/** The claim a swipe toward a neighbour of [kind] gets: none for `none`, a follow for a page in Curl, else a turn. */
private fun expectedClaim(mode: ReaderPageTurn, kind: NeighbourKind, dir: Int): CurlClaim = when {
    kind == NeighbourKind.None -> CurlClaim.Pass
    mode == ReaderPageTurn.Curl && kind == NeighbourKind.Page -> CurlClaim.Drag(dir)
    else -> CurlClaim.Turn(dir)
}

/** The claim a horizontal swipe toward [dir] gets: well past the slop, with no long press and no live selection. */
private fun PageCurlHost.claimToward(dir: Int): CurlClaim =
    claimFor(dx = -dir * 50f, dy = 0f, slop = 8f, elapsedMs = 0L, pointers = 1)

/** The neighbour kind as the glue reports it in `whenSettled()`. */
private val NeighbourKind.token: String get() = name.lowercase()
